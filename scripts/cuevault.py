#!/usr/bin/env python3
# SPDX-License-Identifier: CC0-1.0
"""Reference implementation of the Cue: vault container format, version 1.

The normative specification is docs/vault-format.md in the repository of Cue: React & Act; this
script follows it section by section. It needs Python 3.9 or newer and the package `cryptography`:

    python3 -m venv venv
    venv/bin/pip install --require-hashes -r cuevault-requirements.txt

Commands:

    cuevault.py extract vault.cuevault [-o vault.tar]     decrypt and verify, write the tar archive
    cuevault.py create DIRECTORY vault.cuevault            build a vault from a directory
    cuevault.py vectors [--check] fixtures/vault           generate or check the test vectors

The password is asked for on the terminal. For scripted use it can come from a file
(`--password-file`, its last line break ignored) or from the environment variable
CUEVAULT_PASSWORD.

`extract` reads only a regular file: save a vault that comes through a pipe to a file first.
With `-o -` the tar goes to standard output as it verifies, and a failure cannot take back what
was already written: trust that output only when the exit code is 0.

Exit codes: 0 success, 1 any other failure (input or output, an input that is not a regular file,
a refused `create`), 2 wrong usage, 3 not_a_vault, 4 unsupported_version, 5 bad_header,
6 wrong_password, 7 stale_key, 8 corrupt, 9 bad_archive, 10 a test vector did not check out;
SIGTERM or SIGHUP end `extract` and `create` with 128 plus the signal's number, the partial output
removed.
"""

from __future__ import annotations

import argparse
import contextlib
import getpass
import hashlib
import io
import json
import math
import os
import secrets
import signal
import stat
import struct
import sys
import tempfile
import time
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO, Callable, Dict, Iterable, Iterator, List, Optional, Tuple

try:
    from cryptography.exceptions import InvalidTag
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    sys.stderr.write(
        "cuevault.py needs the Python package 'cryptography':\n"
        "  pip install --require-hashes -r cuevault-requirements.txt\n"
    )
    sys.exit(1)


# =================================================================================================
# Format constants (specification: "Header", "Payload", "Limits")
# =================================================================================================

MAGIC = b"CUEVAULT"
VERSION = 1
HEADER_SIZE = 136
WRAP_AAD_SIZE = 61  # header bytes [0, 61): magic, version, iterations, salt, vault_id, key_id

KEY_SIZE = 32
TAG_SIZE = 16
SEGMENT_SIZE = 65536
SEALED_SEGMENT_SIZE = SEGMENT_SIZE + TAG_SIZE  # 65552

MAX_SEGMENTS = 2 ** 32  # the segment index is a u32 in the nonce
MAX_ARCHIVE_SIZE = MAX_SEGMENTS * SEGMENT_SIZE  # 2^48 bytes of plaintext

MIN_ITERATIONS = 100_000
MAX_ITERATIONS = 10_000_000
WRITE_ITERATIONS = 600_000

#                 magic version iterations salt vault_id key_id written_at wrap_nonce wrapped tag prefix
HEADER_STRUCT = struct.Struct("<8s B I 16s 16s 16s Q 12s 32s 16s 7s")
assert HEADER_STRUCT.size == HEADER_SIZE


# =================================================================================================
# Error categories (specification: "Opening a vault")
# =================================================================================================

NOT_A_VAULT = "not_a_vault"
UNSUPPORTED_VERSION = "unsupported_version"
BAD_HEADER = "bad_header"
WRONG_PASSWORD = "wrong_password"
STALE_KEY = "stale_key"
CORRUPT = "corrupt"
BAD_ARCHIVE = "bad_archive"

CATEGORIES = (NOT_A_VAULT, UNSUPPORTED_VERSION, BAD_HEADER, WRONG_PASSWORD, STALE_KEY, CORRUPT,
              BAD_ARCHIVE)


class VaultError(Exception):
    """A vault that cannot be opened, with the category every implementation reports."""

    def __init__(self, category: str, message: str):
        assert category in CATEGORIES
        super().__init__(message)
        self.category = category
        self.message = message


class InputError(Exception):
    """A directory, file or password the commands refuse; exit code 1."""


# =================================================================================================
# Passwords (specification: "Passwords")
# =================================================================================================

MIN_PASSWORD_LENGTH = 8


def normalize_password(password: str) -> bytes:
    """The bytes PBKDF2 takes: the password normalized to NFC, in UTF-8."""
    try:
        return unicodedata.normalize("NFC", password).encode("utf-8")
    except UnicodeEncodeError:
        # A lone surrogate, which is also what os.environ makes of bytes that are not UTF-8.
        raise InputError("the password is not valid UTF-8") from None


def password_problem(password: str) -> Optional[str]:
    """Why a writer must refuse this password as a new one, or None when it is acceptable."""
    if len(unicodedata.normalize("NFC", password)) < MIN_PASSWORD_LENGTH:
        return f"a password needs at least {MIN_PASSWORD_LENGTH} characters"
    if any(unicodedata.category(c) == "Cn" for c in password):
        return (f"the password holds a character that Unicode {unicodedata.unidata_version}, the "
                "version this Python knows, leaves unassigned; another app may normalize it "
                "differently")
    return None


# =================================================================================================
# Header (specification: "Header")
# =================================================================================================

@dataclass(frozen=True)
class Header:
    iterations: int
    salt: bytes
    vault_id: bytes
    key_id: bytes
    written_at: int
    wrap_nonce: bytes
    wrapped_cek: bytes
    wrap_tag: bytes
    nonce_prefix: bytes
    version: int = VERSION

    def pack(self) -> bytes:
        return HEADER_STRUCT.pack(
            MAGIC, self.version, self.iterations, self.salt, self.vault_id, self.key_id,
            self.written_at, self.wrap_nonce, self.wrapped_cek, self.wrap_tag, self.nonce_prefix,
        )

    @staticmethod
    def unpack(data: bytes) -> "Header":
        (_magic, version, iterations, salt, vault_id, key_id, written_at, wrap_nonce, wrapped_cek,
         wrap_tag, nonce_prefix) = HEADER_STRUCT.unpack(data[:HEADER_SIZE])
        return Header(iterations, salt, vault_id, key_id, written_at, wrap_nonce, wrapped_cek,
                      wrap_tag, nonce_prefix, version)


def _segment_count(payload_size: int) -> int:
    """n for a payload of L bytes, or a VaultError when L cannot be cut into sealed segments."""
    n = -(-payload_size // SEALED_SEGMENT_SIZE)  # ceil(L / 65552)
    last = payload_size - (n - 1) * SEALED_SEGMENT_SIZE
    if n == 0 or last < TAG_SIZE + 1:
        raise VaultError(BAD_HEADER, f"a payload of {payload_size} bytes is no sequence of "
                                     "segments (the file is cut short)")
    if n > MAX_SEGMENTS:
        raise VaultError(BAD_HEADER, f"a payload of {payload_size} bytes needs {n} segments, "
                                     f"more than the {MAX_SEGMENTS} a vault may have")
    return n


def parse_header(head: bytes, file_size: int) -> Header:
    """Every check that needs no key, in the order the specification fixes. `head` is the file's
    first bytes, as many of the 136 as it has."""
    if len(head) < len(MAGIC) or head[:len(MAGIC)] != MAGIC:
        raise VaultError(NOT_A_VAULT, "this is not a Cue: vault (no CUEVAULT signature)")
    if len(head) > len(MAGIC) and head[len(MAGIC)] != VERSION:
        raise VaultError(UNSUPPORTED_VERSION, f"vault format version {head[len(MAGIC)]} is not "
                                              f"supported (this reader knows {VERSION})")
    if file_size < HEADER_SIZE:
        raise VaultError(BAD_HEADER, f"the file has {file_size} bytes, shorter than the "
                                     f"{HEADER_SIZE}-byte header")
    header = Header.unpack(head)
    if not MIN_ITERATIONS <= header.iterations <= MAX_ITERATIONS:
        raise VaultError(BAD_HEADER, f"{header.iterations} key derivation iterations are outside "
                                     f"{MIN_ITERATIONS}-{MAX_ITERATIONS}")
    _segment_count(file_size - HEADER_SIZE)
    return header


# =================================================================================================
# Keys and payload (specification: "Keys", "Payload")
# =================================================================================================

@dataclass(frozen=True)
class StoredKey:
    """A content key a device kept from an earlier unlock, with the key_id it belongs to."""
    key_id: bytes
    cek: bytes


def derive_kek(password: bytes, salt: bytes, iterations: int) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", password, salt, iterations, KEY_SIZE)


def segment_nonce(nonce_prefix: bytes, index: int, last: bool) -> bytes:
    return nonce_prefix + struct.pack("<I", index) + (b"\x01" if last else b"\x00")


def sealed_header(*, kek: bytes, cek: bytes, iterations: int, salt: bytes, vault_id: bytes,
                  key_id: bytes, written_at: int, wrap_nonce: bytes, nonce_prefix: bytes) -> bytes:
    """The 136 header bytes with the CEK wrapped under the KEK; every random input is a parameter."""
    if not MIN_ITERATIONS <= iterations <= MAX_ITERATIONS:
        raise ValueError(f"{iterations} iterations are outside {MIN_ITERATIONS}-{MAX_ITERATIONS}")
    unwrapped = Header(iterations, salt, vault_id, key_id, written_at, wrap_nonce,
                       bytes(32), bytes(16), nonce_prefix)
    sealed_cek = AESGCM(kek).encrypt(wrap_nonce, cek, unwrapped.pack()[:WRAP_AAD_SIZE])
    return Header(iterations, salt, vault_id, key_id, written_at, wrap_nonce,
                  sealed_cek[:KEY_SIZE], sealed_cek[KEY_SIZE:], nonce_prefix).pack()


class Sealer:
    """Writes the header, then seals an archive written into it in pieces of any size. Only the end
    of the archive tells which segment is the last, so one segment of plaintext is held back."""

    def __init__(self, out: BinaryIO, header: bytes, cek: bytes):
        self._out = out
        self._header = header
        self._prefix = Header.unpack(header).nonce_prefix
        self._aead = AESGCM(cek)
        self._pending = bytearray()
        self._index = 0
        out.write(header)

    def write(self, data: bytes) -> None:
        self._pending += data
        while len(self._pending) > SEGMENT_SIZE:
            self._seal(bytes(self._pending[:SEGMENT_SIZE]), last=False)
            del self._pending[:SEGMENT_SIZE]

    def close(self) -> None:
        if not self._pending:
            raise ValueError("an archive is never empty")
        self._seal(bytes(self._pending), last=True)
        self._pending.clear()

    def _seal(self, chunk: bytes, last: bool) -> None:
        if self._index >= MAX_SEGMENTS:
            raise ValueError(f"the archive needs more than {MAX_SEGMENTS} segments")
        nonce = segment_nonce(self._prefix, self._index, last)
        self._out.write(self._aead.encrypt(nonce, chunk, self._header))
        self._index += 1


def seal(archive: bytes, *, kek: bytes, cek: bytes, iterations: int, salt: bytes,
         vault_id: bytes, key_id: bytes, written_at: int, wrap_nonce: bytes,
         nonce_prefix: bytes) -> bytes:
    """The whole container for an archive; every random input is a parameter."""
    header = sealed_header(kek=kek, cek=cek, iterations=iterations, salt=salt, vault_id=vault_id,
                           key_id=key_id, written_at=written_at, wrap_nonce=wrap_nonce,
                           nonce_prefix=nonce_prefix)
    out = io.BytesIO()
    sealer = Sealer(out, header, cek)
    sealer.write(archive)
    sealer.close()
    return out.getvalue()


def unwrap_cek(header: Header, header_bytes: bytes, password: bytes) -> bytes:
    kek = derive_kek(password, header.salt, header.iterations)
    try:
        return AESGCM(kek).decrypt(header.wrap_nonce, header.wrapped_cek + header.wrap_tag,
                                   header_bytes[:WRAP_AAD_SIZE])
    except InvalidTag:
        raise VaultError(WRONG_PASSWORD, "wrong password, or the header is damaged") from None


def content_key(header: Header, header_bytes: bytes, *, password: Optional[bytes] = None,
                stored_key: Optional[StoredKey] = None) -> bytes:
    """Step 6 of "Opening a vault": the CEK from a password or a stored key."""
    if stored_key is not None:
        if stored_key.key_id != header.key_id:
            raise VaultError(STALE_KEY, "the stored key belongs to an older password; "
                                        "the vault needs its password")
        return stored_key.cek
    if password is not None:
        return unwrap_cek(header, header_bytes, password)
    raise ValueError("a password or a stored key is needed")


def archive_size_of(payload_size: int) -> int:
    """P for a payload whose layout parse_header accepted."""
    return payload_size - TAG_SIZE * _segment_count(payload_size)


def open_segments(read: Callable[[int], bytes], header: Header, header_bytes: bytes, cek: bytes,
                  payload_size: int) -> Iterator[bytes]:
    """The plaintext of each segment in order, each only after its tag verified. `read` returns the
    next bytes of the payload; the archive is verified once the last one has been yielded."""
    n = _segment_count(payload_size)
    aead = AESGCM(cek)
    for i in range(n):
        size = SEALED_SEGMENT_SIZE if i < n - 1 else payload_size - (n - 1) * SEALED_SEGMENT_SIZE
        sealed = read(size)
        if len(sealed) != size:
            raise InputError("the file ended before its stated size; did it change while it was "
                             "read?")
        try:
            yield aead.decrypt(segment_nonce(header.nonce_prefix, i, i == n - 1), sealed,
                               header_bytes)
        except InvalidTag:
            raise VaultError(CORRUPT, f"segment {i} of {n} does not authenticate: the file is "
                                      "damaged, cut short or reordered") from None
    if read(1):
        raise InputError("the file is longer than its stated size; did it change while it was "
                         "read?")


def read_head(vault: BinaryIO, file_size: int) -> Tuple[Header, bytes]:
    """The checked header of a vault of `file_size` bytes, read from its start."""
    head = vault.read(HEADER_SIZE)
    if len(head) != min(file_size, HEADER_SIZE):
        raise InputError("the file ended before its stated size; did it change while it was read?")
    return parse_header(head, file_size), head


def open_container(data: bytes, *, password: Optional[bytes] = None,
                   stored_key: Optional[StoredKey] = None) -> bytes:
    """The verified plaintext archive of a container held in memory."""
    header_bytes = data[:HEADER_SIZE]
    header = parse_header(header_bytes, len(data))
    cek = content_key(header, header_bytes, password=password, stored_key=stored_key)
    payload = io.BytesIO(data)
    payload.seek(HEADER_SIZE)
    return b"".join(open_segments(payload.read, header, header_bytes, cek,
                                  len(data) - HEADER_SIZE))


# =================================================================================================
# Plaintext archive: the ustar profile (specification: "Archive")
# =================================================================================================

BLOCK_SIZE = 512
ZERO_BLOCK = bytes(BLOCK_SIZE)
NAME_SIZE = 100
PREFIX_SIZE = 155
MAX_OCTAL_11 = 8 ** 11 - 1
MAX_CONTENT_SIZE = MAX_OCTAL_11  # the size field's 11 octal digits: 8 GiB less one byte

TYPE_FILE = b"0"
TYPE_DIR = b"5"
MODE_FILE = b"0000644\0"
MODE_DIR = b"0000755\0"
ZERO_OCTAL_7 = b"0000000\0"
USTAR_MAGIC = b"ustar\0"
USTAR_VERSION = b"00"


@dataclass(frozen=True)
class Entry:
    path: str  # relative, a directory's ending in "/"; NFC when this script wrote it
    is_dir: bool
    mtime: int
    content: bytes = b""


def _padded(size: int) -> int:
    return -(-size // BLOCK_SIZE) * BLOCK_SIZE


def path_problem(path: str, is_dir: bool) -> Optional[str]:
    """Why a path breaks the profile, or None. Normalization is the writer's business."""
    if path.endswith("/") != is_dir:
        return "a directory path must end in '/' and a file path must not"
    if path.startswith("/"):
        return "the path is absolute"
    if "\0" in path:
        return "the path contains NUL"
    for segment in (path[:-1] if is_dir else path).split("/"):
        if segment in ("", ".", ".."):
            return "the path has an empty, '.' or '..' segment"
    return None


def split_path(path: bytes) -> Tuple[bytes, bytes]:
    """(prefix, name): the longest prefix of at most 155 bytes that leaves 1-100 bytes in name."""
    if len(path) <= NAME_SIZE:
        return b"", path
    for cut in range(min(PREFIX_SIZE, len(path) - 2), 0, -1):
        if len(path) - cut - 1 > NAME_SIZE:
            break
        if path[cut:cut + 1] == b"/":
            return path[:cut], path[cut + 1:]
    raise ValueError(f"the path {path.decode('utf-8', 'replace')!r} does not fit into ustar's "
                     "name and prefix fields")


def _octal(value: int, digits: int) -> bytes:
    return b"%0*o" % (digits, value)


def ustar_header(name: bytes, prefix: bytes, typeflag: bytes, mode: bytes, size: int,
                 mtime: int, linkname: bytes = b"") -> bytes:
    block = bytearray(BLOCK_SIZE)

    def put(offset: int, length: int, value: bytes) -> None:
        assert len(value) <= length
        block[offset:offset + len(value)] = value

    put(0, 100, name)
    put(100, 8, mode)
    put(108, 8, ZERO_OCTAL_7)                 # uid
    put(116, 8, ZERO_OCTAL_7)                 # gid
    put(124, 12, _octal(size, 11) + b"\0")
    put(136, 12, _octal(mtime, 11) + b"\0")
    put(148, 8, b" " * 8)                     # chksum counts as spaces while it is summed
    put(156, 1, typeflag)
    put(157, 100, linkname)
    put(257, 6, USTAR_MAGIC)
    put(263, 2, USTAR_VERSION)
    # uname (265, 32) and gname (297, 32) stay NUL
    put(329, 8, ZERO_OCTAL_7)                 # devmajor
    put(337, 8, ZERO_OCTAL_7)                 # devminor
    put(345, 155, prefix)
    put(148, 8, _octal(sum(block), 6) + b"\0 ")
    return bytes(block)


@dataclass(frozen=True)
class Member:
    """An entry to write, whose `size` bytes of content `chunks` hands out in pieces."""
    path: str
    is_dir: bool
    mtime: int
    size: int
    chunks: Callable[[], Iterable[bytes]]


def member_of(entry: Entry) -> Member:
    return Member(entry.path, entry.is_dir, entry.mtime, len(entry.content),
                  lambda: (entry.content,))


def archive_size(sizes: Iterable[int]) -> int:
    """The size write_archive will produce for entries of these content sizes (0 for directories)."""
    return sum(BLOCK_SIZE + _padded(size) for size in sizes) + 2 * BLOCK_SIZE


def plan_archive(members: Iterable[Member]) -> List[Member]:
    """The members as the archive holds them: NFC paths, clamped times, in the profile's order,
    after every check a writer makes. Every ancestor directory must be among them."""
    normalized: Dict[bytes, Member] = {}
    for member in members:
        path = unicodedata.normalize("NFC", member.path)
        problem = path_problem(path, member.is_dir)
        if problem:
            raise ValueError(f"{member.path!r}: {problem}")
        if member.is_dir and member.size:
            raise ValueError(f"{member.path!r}: a directory has no content")
        if member.size > MAX_CONTENT_SIZE:
            raise ValueError(f"{member.path!r}: {member.size} bytes are more than the "
                             f"{MAX_CONTENT_SIZE} the archive holds in one file")
        split_path(path.encode("utf-8"))
        key = path.rstrip("/").encode("utf-8")
        if key in normalized:
            raise ValueError(f"{member.path!r}: the path occurs twice")
        mtime = min(max(member.mtime, 0), MAX_OCTAL_11)
        normalized[key] = Member(path, member.is_dir, mtime, member.size, member.chunks)

    for key, member in normalized.items():
        parts = key.split(b"/")
        for depth in range(1, len(parts)):
            parent = normalized.get(b"/".join(parts[:depth]))
            if parent is None or not parent.is_dir:
                raise ValueError(f"{member.path!r}: its parent directory is not an entry")

    size = archive_size(m.size for m in normalized.values())
    if size > MAX_ARCHIVE_SIZE:
        raise ValueError(f"the archive would have {size} bytes, more than {MAX_ARCHIVE_SIZE}")
    return sorted(normalized.values(), key=lambda m: m.path.encode("utf-8"))


def archive_stream(planned: List[Member]) -> Iterator[bytes]:
    """The byte-exact archive of planned members, in pieces of at most one content chunk."""
    for member in planned:
        prefix, name = split_path(member.path.encode("utf-8"))
        typeflag, mode = (TYPE_DIR, MODE_DIR) if member.is_dir else (TYPE_FILE, MODE_FILE)
        yield ustar_header(name, prefix, typeflag, mode, member.size, member.mtime)
        written = 0
        for chunk in member.chunks():
            written += len(chunk)
            if written > member.size:
                break
            yield chunk
        if written != member.size:
            raise ValueError(f"{member.path!r}: the content is not the {member.size} bytes it "
                             "had when it was listed; did it change while it was read?")
        yield bytes(_padded(member.size) - member.size)
    yield ZERO_BLOCK * 2


def write_archive(entries: Iterable[Entry]) -> bytes:
    """The profile's byte-exact archive. Every ancestor directory must be among the entries."""
    return b"".join(archive_stream(plan_archive(member_of(e) for e in entries)))


def _bad(offset: int, message: str) -> VaultError:
    return VaultError(BAD_ARCHIVE, f"archive header at byte {offset}: {message}")


def _parse_octal(field: bytes, digits: int, terminator: bytes, offset: int, what: str) -> int:
    if (len(field) != digits + len(terminator) or field[digits:] != terminator
            or any(c not in b"01234567" for c in field[:digits])):
        raise _bad(offset, f"{what} is not {digits} octal digits")
    return int(field[:digits], 8)


def _field_text(field: bytes) -> bytes:
    end = field.find(b"\0")
    return field if end < 0 else field[:end]


class ArchiveChecker:
    """The profile's checks on a decrypted archive of a known size, fed in pieces of any length.
    Only paths are kept, and the entries with their content when `keep_content` asks for them."""

    _HEADER, _CONTENT, _END, _DONE = range(4)

    def __init__(self, size: int, keep_content: bool = False):
        if size % BLOCK_SIZE or size < 2 * BLOCK_SIZE:
            raise VaultError(BAD_ARCHIVE, f"an archive of {size} bytes is no whole number of "
                                          "blocks with its end marker")
        self.size = size
        self.files = 0
        self.directories = 0
        self.entries: List[Entry] = []
        self._keep = keep_content
        self._state = self._HEADER
        self._pos = 0  # where the next block starts
        self._block = bytearray()
        self._left = 0  # content and padding still to come
        self._entry: Optional[Tuple[str, bool, int, int]] = None  # path, is_dir, mtime, size
        self._content = bytearray()
        self._kinds: Dict[bytes, bool] = {}  # path without its trailing "/" -> is_dir
        self._ancestors: set = set()

    def feed(self, data: bytes) -> None:
        view = memoryview(data)
        at = 0
        while at < len(view):
            if self._state == self._CONTENT:
                take = min(self._left, len(view) - at)
                if self._keep and self._entry is not None:
                    want = self._entry[3] - len(self._content)
                    self._content += view[at:at + min(take, max(want, 0))]
                self._left -= take
                self._pos += take
                at += take
                if self._left == 0:
                    self._end_entry()
                continue
            take = min(BLOCK_SIZE - len(self._block), len(view) - at)
            self._block += view[at:at + take]
            at += take
            if len(self._block) == BLOCK_SIZE:
                block = bytes(self._block)
                self._block.clear()
                self._take_block(block)
                self._pos += BLOCK_SIZE

    def finish(self) -> None:
        if self._state != self._DONE or self._block:
            raise VaultError(BAD_ARCHIVE, "the archive ends without its two zero blocks")

    def _take_block(self, block: bytes) -> None:
        pos = self._pos
        if self._state == self._DONE:
            raise VaultError(BAD_ARCHIVE, "the archive must end in exactly two zero blocks")
        if self._state == self._END:
            if block != ZERO_BLOCK:
                raise VaultError(BAD_ARCHIVE, "the archive must end in exactly two zero blocks")
            self._state = self._DONE
            return
        if block == ZERO_BLOCK:
            if pos + 2 * BLOCK_SIZE != self.size:
                raise VaultError(BAD_ARCHIVE, "the archive must end in exactly two zero blocks")
            self._state = self._END
            return

        if block[257:263] != USTAR_MAGIC or block[263:265] != USTAR_VERSION:
            raise _bad(pos, "not a ustar header")
        checksum = _parse_octal(block[148:156], 6, b"\0 ", pos, "chksum")
        if checksum != sum(block[:148]) + 8 * 0x20 + sum(block[156:]):
            raise _bad(pos, "the checksum does not match")
        typeflag = block[156:157]
        if typeflag not in (TYPE_FILE, TYPE_DIR):
            raise _bad(pos, f"entry type {typeflag!r} is not a file or a directory")
        is_dir = typeflag == TYPE_DIR
        size = _parse_octal(block[124:136], 11, b"\0", pos, "size")
        mtime = _parse_octal(block[136:148], 11, b"\0", pos, "mtime")
        if is_dir and size:
            raise _bad(pos, "a directory has a size")

        name, prefix = _field_text(block[0:100]), _field_text(block[345:500])
        if not name:
            raise _bad(pos, "the name is empty")
        raw_path = prefix + b"/" + name if prefix else name
        try:
            path = raw_path.decode("utf-8")
        except UnicodeDecodeError:
            raise _bad(pos, "the path is not UTF-8") from None
        problem = path_problem(path, is_dir)
        if problem:
            raise _bad(pos, f"{path!r}: {problem}")
        key = raw_path.rstrip(b"/")
        if key in self._kinds:
            raise _bad(pos, f"{path!r} occurs twice")
        parts = key.split(b"/")
        parents = [b"/".join(parts[:depth]) for depth in range(1, len(parts))]
        if any(self._kinds.get(parent) is False for parent in parents) or (
                not is_dir and key in self._ancestors):
            raise _bad(pos, f"{path!r}: a path lies inside a file")
        self._kinds[key] = is_dir
        self._ancestors.update(parents)

        if pos + BLOCK_SIZE + _padded(size) > self.size:
            raise _bad(pos, f"{path!r}: the content runs past the end of the archive")
        if is_dir:
            self.directories += 1
        else:
            self.files += 1
        self._entry = (path, is_dir, mtime, size)
        self._content.clear()
        self._left = _padded(size)
        if self._left:
            self._state = self._CONTENT
        else:
            self._end_entry()

    def _end_entry(self) -> None:
        if self._keep and self._entry is not None:
            path, is_dir, mtime, _ = self._entry
            self.entries.append(Entry(path, is_dir, mtime, bytes(self._content)))
        self._entry = None
        self._state = self._HEADER


def read_archive(data: bytes) -> List[Entry]:
    """Every entry of a decrypted archive held in memory, after checking it against the profile."""
    checker = ArchiveChecker(len(data), keep_content=True)
    checker.feed(data)
    checker.finish()
    return checker.entries


def open_vault(data: bytes, *, password: Optional[str] = None,
               stored_key: Optional[StoredKey] = None) -> Tuple[bytes, List[Entry]]:
    """The verified archive and its entries. A damaged segment wins over a broken archive."""
    archive = open_container(
        data, password=None if password is None else normalize_password(password),
        stored_key=stored_key,
    )
    return archive, read_archive(archive)


# =================================================================================================
# Directories (specification: "Archive", the names a writer leaves out)
# =================================================================================================

JUNK_ANYWHERE = {".DS_Store"}
JUNK_AT_ROOT = {".fseventsd", ".Spotlight-V100", ".Trashes", ".TemporaryItems",
                ".DocumentRevisions-V100", ".metadata_never_index"}


def _is_junk(name: str, at_root: bool) -> bool:
    return name in JUNK_ANYWHERE or name.startswith("._") or (at_root and name in JUNK_AT_ROOT)


def _mtime(st: os.stat_result) -> int:
    return min(max(math.floor(st.st_mtime), 0), MAX_OCTAL_11)


def _file_chunks(source: Path) -> Iterator[bytes]:
    with open(source, "rb") as file:
        while True:
            chunk = file.read(SEGMENT_SIZE)
            if not chunk:
                return
            yield chunk


def collect_entries(root: Path) -> List[Member]:
    """Everything under root, junk left out; a file's content is read only when it is written."""
    found: List[Member] = []
    stack = [(root, "")]
    while stack:
        directory, relative = stack.pop()
        for item in sorted(os.scandir(directory), key=lambda e: e.name):
            if _is_junk(item.name, relative == ""):
                continue
            path = relative + item.name
            st = item.stat(follow_symlinks=False)
            if stat.S_ISDIR(st.st_mode):
                found.append(Member(path + "/", True, _mtime(st), 0, lambda: ()))
                stack.append((Path(item.path), path + "/"))
            elif stat.S_ISREG(st.st_mode):
                source = Path(item.path)
                found.append(Member(path, False, _mtime(st), st.st_size,
                                    lambda source=source: _file_chunks(source)))
            else:
                raise InputError(f"{item.path}: only regular files and directories can go into "
                                 "a vault (this is a symbolic link or a special file)")
    return found


# =================================================================================================
# Test vectors (fixtures/vault/vectors.json; its README describes the schema)
# =================================================================================================

class VectorFailure(Exception):
    pass


def _hex(value: str) -> bytes:
    return bytes.fromhex(value)


def content_bytes(spec: Optional[dict]) -> bytes:
    if spec is None:
        return b""
    if len(spec) != 1:
        raise VectorFailure(f"content needs exactly one form: {spec}")
    form, value = next(iter(spec.items()))
    if form == "utf8":
        return value.encode("utf-8")
    if form == "hex":
        return _hex(value)
    if form == "repeat":
        return _hex(value["byte"]) * value["count"]
    if form == "sequence":
        return bytes(i % 256 for i in range(value["count"]))
    raise VectorFailure(f"unknown content form {form!r}")


def vector_entries(specs: List[dict]) -> List[Entry]:
    return [Entry(s["path"], s["type"] == "dir", s["mtime"], content_bytes(s.get("content")))
            for s in specs]


def crafted_archive(specs: List[dict]) -> bytes:
    """An archive in the given order with no profile checks: symbolic links and any path allowed."""
    out = bytearray()
    for spec in specs:
        typeflag, mode = {"file": (TYPE_FILE, MODE_FILE), "dir": (TYPE_DIR, MODE_DIR),
                          "symlink": (b"2", b"0000777\0")}[spec["type"]]
        content = content_bytes(spec.get("content"))
        prefix, name = split_path(spec["path"].encode("utf-8"))
        out += ustar_header(name, prefix, typeflag, mode, len(content), spec["mtime"],
                            spec.get("linkname", "").encode("utf-8"))
        out += content + bytes(_padded(len(content)) - len(content))
    return bytes(out + ZERO_BLOCK * 2)


def build_positive(case: dict) -> Tuple[bytes, dict]:
    inputs = case["inputs"]
    kek = derive_kek(normalize_password(inputs["password"]), _hex(inputs["salt"]),
                     inputs["iterations"])
    archive = write_archive(vector_entries(inputs["entries"]))
    return _seal_inputs(inputs, kek, archive), {"kek": kek.hex()}


def _seal_inputs(inputs: dict, kek: bytes, archive: bytes) -> bytes:
    return seal(archive, kek=kek, cek=_hex(inputs["cek"]), iterations=inputs["iterations"],
                salt=_hex(inputs["salt"]), vault_id=_hex(inputs["vault_id"]),
                key_id=_hex(inputs["key_id"]), written_at=inputs["written_at"],
                wrap_nonce=_hex(inputs["wrap_nonce"]), nonce_prefix=_hex(inputs["nonce_prefix"]))


def positive_expected(case: dict, data: bytes, kek_hex: str) -> dict:
    archive = write_archive(vector_entries(case["inputs"]["entries"]))
    return {
        "file": case["name"] + ".cuevault",
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "kek": kek_hex,
        "header": data[:HEADER_SIZE].hex(),
        "segments": -(-len(archive) // SEGMENT_SIZE),
        "archive_size": len(archive),
        "archive_sha256": hashlib.sha256(archive).hexdigest(),
    }


def apply_mutation(base_case: dict, base: bytes, mutation: Optional[dict]) -> bytes:
    if mutation is None:
        return base
    kind = mutation["kind"]
    data = bytearray(base)
    if kind == "xor":
        offset, mask = mutation["offset"], _hex(mutation["mask"])
        for i, m in enumerate(mask):
            data[offset + i] ^= m
    elif kind == "set_bytes":
        offset, value = mutation["offset"], _hex(mutation["hex"])
        data[offset:offset + len(value)] = value
    elif kind == "truncate":
        del data[mutation["length"]:]
    elif kind == "swap_segments":
        a, b = (slice(HEADER_SIZE + i * SEALED_SEGMENT_SIZE,
                      HEADER_SIZE + (i + 1) * SEALED_SEGMENT_SIZE)
                for i in (mutation["first"], mutation["second"]))
        if len(data[a]) != SEALED_SEGMENT_SIZE or len(data[b]) != SEALED_SEGMENT_SIZE:
            raise VectorFailure("swap_segments takes two full segments")
        data[a], data[b] = data[b], data[a]
    elif kind == "replace_entries":
        inputs = base_case["inputs"]
        kek = derive_kek(normalize_password(inputs["password"]), _hex(inputs["salt"]),
                         inputs["iterations"])
        return _seal_inputs(inputs, kek, crafted_archive(mutation["entries"]))
    else:
        raise VectorFailure(f"unknown mutation {kind!r}")
    return bytes(data)


def credential_of(spec: dict) -> dict:
    if "password" in spec:
        return {"password": spec["password"]}
    key = spec["stored_key"]
    return {"stored_key": StoredKey(_hex(key["key_id"]), _hex(key["cek"]))}


def build_all(document: dict) -> Tuple[Dict[str, bytes], dict]:
    """Every vector file by name, and the document with its expected values recomputed."""
    files: Dict[str, bytes] = {}
    built: Dict[str, Tuple[dict, bytes]] = {}
    out = json.loads(json.dumps(document))

    for case in out["passwords"]:
        case["expected"] = normalize_password(case["input"]).hex()

    for case in out["positive"]:
        data, extra = build_positive(case)
        case["expected"] = positive_expected(case, data, extra["kek"])
        files[case["expected"]["file"]] = data
        built[case["name"]] = (case, data)

    for group in ("negative", "accepted"):
        for case in out[group]:
            base_case, base = built[case["base"]]
            data = apply_mutation(base_case, base, case["mutation"])
            name = (base_case["expected"]["file"] if case["mutation"] is None
                    else case["name"] + ".cuevault")
            files[name] = data
            expected = {"file": name, "sha256": hashlib.sha256(data).hexdigest()}
            for kept in ("error", "error_streaming"):
                if kept in case["expected"]:
                    expected[kept] = case["expected"][kept]
            case["expected"] = expected
    return files, out


def _dump(document: dict) -> str:
    # Combining marks are escaped so that the NFD inputs stay NFD when an editor normalizes text.
    text = json.dumps(document, indent=2, ensure_ascii=False) + "\n"
    return "".join(f"\\u{ord(c):04x}" if unicodedata.category(c) == "Mn" else c for c in text)


def generate_vectors(directory: Path) -> None:
    document = json.loads((directory / "vectors.json").read_text("utf-8"))
    files, updated = build_all(document)
    for stale in directory.glob("*.cuevault"):
        if stale.name not in files:
            stale.unlink()
    for name, data in files.items():
        (directory / name).write_bytes(data)
    (directory / "vectors.json").write_text(_dump(updated), "utf-8")
    print(f"wrote {len(files)} vault files and vectors.json to {directory}")


def _raw(credential: dict) -> dict:
    password = credential.get("password")
    return {"password": None if password is None else normalize_password(password),
            "stored_key": credential.get("stored_key")}


def _streamed(data: bytes, credential: dict, out: Optional[io.BytesIO] = None,
              keep_invalid: bool = False) -> bytes:
    """The archive as extract streams it, a segment at a time, from a file of len(data) bytes."""
    vault = io.BytesIO(data)
    header, head = read_head(vault, len(data))
    cek = content_key(header, head, **_raw(credential))
    target = io.BytesIO() if out is None else out
    stream_archive(vault, header, head, cek, len(data), target, keep_invalid)
    return target.getvalue()


def _check_streamed(label: str, data: bytes, credential: dict, archive: bytes) -> List[str]:
    try:
        streamed = _streamed(data, credential)
    except (VaultError, InputError) as error:
        return [f"{label} (streamed): {error}"]
    if streamed != archive:
        return [f"{label} (streamed): the archive differs from the one read in memory"]
    return []


def _check_read_errors(data: bytes, credential: dict) -> List[str]:
    """A file that does not hold the bytes its size stated is an input error, not a category."""
    failures: List[str] = []
    for label, content in (("a header cut short", data[:HEADER_SIZE - 1]),
                           ("a segment cut short", data[:HEADER_SIZE + SEALED_SEGMENT_SIZE + 1]),
                           ("a last segment cut short", data[:-1]),
                           ("a byte past the size", data + b"\0")):
        vault = io.BytesIO(content)
        try:
            header, head = read_head(vault, len(data))
            cek = content_key(header, head, **_raw(credential))
            stream_archive(vault, header, head, cek, len(data), io.BytesIO(), False)
            failures.append(f"streaming {label}: read without an error")
        except InputError:
            pass
        except VaultError as error:
            failures.append(f"streaming {label}: {error.category}, expected an input error")
    for content in (b"abcd", b"abcdef"):
        member = Member("a", False, 0, 5, lambda content=content: (content,))
        try:
            b"".join(archive_stream([member]))
            failures.append(f"a file of 5 bytes that yields {len(content)}: written")
        except ValueError:
            pass
    return failures


def check_vectors(directory: Path) -> List[str]:
    failures: List[str] = []
    text = (directory / "vectors.json").read_text("utf-8")
    document = json.loads(text)
    files, rebuilt = build_all(document)
    if _dump(rebuilt) != text:
        failures.append("vectors.json: an expected value differs from what the inputs produce")

    on_disk = {p.name for p in directory.glob("*.cuevault")}
    for extra in sorted(on_disk - set(files)):
        failures.append(f"{extra}: no case refers to it")
    for name, data in sorted(files.items()):
        path = directory / name
        if not path.is_file():
            failures.append(f"{name}: missing")
        elif path.read_bytes() != data:
            failures.append(f"{name}: differs from the bytes its case produces")

    for case in document["passwords"]:
        if normalize_password(case["input"]).hex() != case["expected"]:
            failures.append(f"password {case['input']!r}: normalized differently")

    for case in document["positive"]:
        data = (directory / case["expected"]["file"]).read_bytes()
        if hashlib.sha256(data).hexdigest() != case["expected"]["sha256"]:
            failures.append(f"{case['name']}: SHA-256 differs")
        inputs = case["inputs"]
        want = sorted(
            (Entry(unicodedata.normalize("NFC", e.path), e.is_dir, e.mtime, e.content)
             for e in vector_entries(inputs["entries"])),
            key=lambda e: e.path.encode("utf-8"),
        )
        archive = b""
        for label, credential in (
            ("password", {"password": inputs["password"]}),
            ("stored key", {"stored_key": StoredKey(_hex(inputs["key_id"]), _hex(inputs["cek"]))}),
        ):
            try:
                archive, entries = open_vault(data, **credential)
            except VaultError as error:
                failures.append(f"{case['name']} ({label}): {error.category}: {error.message}")
                continue
            if entries != want:
                failures.append(f"{case['name']} ({label}): the entries differ from the case")
            failures += _check_streamed(f"{case['name']} ({label})", data, credential, archive)
        for piece in (1, 777) if archive else ():
            out = io.BytesIO()
            sealer = Sealer(out, data[:HEADER_SIZE], _hex(inputs["cek"]))
            for at in range(0, len(archive), piece):
                sealer.write(archive[at:at + piece])
            sealer.close()
            if out.getvalue() != data:
                failures.append(f"{case['name']}: sealed in writes of {piece} bytes, it differs")

    for case in document["negative"]:
        data = (directory / case["expected"]["file"]).read_bytes()
        if hashlib.sha256(data).hexdigest() != case["expected"]["sha256"]:
            failures.append(f"{case['name']}: SHA-256 differs")
        expected = case["expected"]["error"]
        credential = credential_of(case["credential"])
        try:
            open_vault(data, **credential)
            failures.append(f"{case['name']}: opened, expected {expected}")
        except VaultError as error:
            if error.category != expected:
                failures.append(f"{case['name']}: {error.category} ({error.message}), "
                                f"expected {expected}")
        allowed = {expected, case["expected"].get("error_streaming", expected)}
        try:
            _streamed(data, credential)
            failures.append(f"{case['name']} (streamed): opened, expected {expected}")
        except VaultError as error:
            if error.category not in allowed:
                failures.append(f"{case['name']} (streamed): {error.category} ({error.message}), "
                                f"expected {expected}")
        except InputError as error:
            failures.append(f"{case['name']} (streamed): {error}")
        if expected == BAD_ARCHIVE:
            try:
                kept = _streamed(data, credential, keep_invalid=True)
                if kept != open_container(data, **_raw(credential)):
                    failures.append(f"{case['name']}: the kept invalid archive differs")
            except (VaultError, InputError) as error:
                failures.append(f"{case['name']} (kept invalid): {error}")

    for case in document["accepted"]:
        data = (directory / case["expected"]["file"]).read_bytes()
        if hashlib.sha256(data).hexdigest() != case["expected"]["sha256"]:
            failures.append(f"{case['name']}: SHA-256 differs")
        credential = credential_of(case["credential"])
        try:
            archive, entries = open_vault(data, **credential)
        except VaultError as error:
            failures.append(f"{case['name']}: {error.category}: {error.message}")
            continue
        if entries != vector_entries(case["mutation"]["entries"]):
            failures.append(f"{case['name']}: the entries differ from the mutation's")
        failures += _check_streamed(case["name"], data, credential, archive)

    multi = next(c for c in document["positive"] if c["expected"]["segments"] > 1)
    failures += _check_read_errors((directory / multi["expected"]["file"]).read_bytes(),
                                   {"password": multi["inputs"]["password"]})
    if not failures:
        print(f"ok: {len(document['passwords'])} passwords, {len(document['positive'])} positive, "
              f"{len(document['negative'])} negative and {len(document['accepted'])} accepted "
              f"cases, {len(files)} files, in memory and streamed")
    return failures


# =================================================================================================
# Command line
# =================================================================================================

EXIT_FAILURE = 1
EXIT_CODES = {NOT_A_VAULT: 3, UNSUPPORTED_VERSION: 4, BAD_HEADER: 5, WRONG_PASSWORD: 6,
              STALE_KEY: 7, CORRUPT: 8, BAD_ARCHIVE: 9}
EXIT_VECTORS = 10
PASSWORD_ENV = "CUEVAULT_PASSWORD"


def read_password(password_file: Optional[str], prompt: str) -> str:
    if password_file:
        try:
            text = Path(password_file).read_text("utf-8")
        except UnicodeDecodeError:
            raise InputError(f"{password_file} is not valid UTF-8") from None
        cut = 2 if text.endswith("\r\n") else 1 if text.endswith("\n") else 0
        password = text[:len(text) - cut]
    elif PASSWORD_ENV in os.environ:
        password = os.environ[PASSWORD_ENV]
    else:
        password = ask_password(prompt)
    normalize_password(password)
    return password


def ask_password(prompt: str) -> str:
    try:
        return getpass.getpass(prompt)
    except UnicodeDecodeError:
        raise InputError("the password is not valid UTF-8") from None


@contextlib.contextmanager
def private_output(target: Path, force: bool) -> Iterator[BinaryIO]:
    """A file only its owner can read (on POSIX; Windows ignores the mode) that takes the target's
    name once the block succeeds; on any failure it is removed, so nothing partial is left behind.
    Without `force` it never replaces a file that appeared meanwhile: the link fails instead."""
    fd, temp = tempfile.mkstemp(dir=target.parent, prefix=".cuevault-")
    try:
        with os.fdopen(fd, "wb") as out:
            yield out
        if force:
            os.replace(temp, target)
        else:
            _move_new(temp, target)
    except BaseException:
        with contextlib.suppress(FileNotFoundError):
            os.unlink(temp)
        raise


def _move_new(temp: str, target: Path) -> None:
    exists = InputError(f"{target} exists; choose another name or pass --force")
    try:
        os.link(temp, target)
    except FileExistsError:
        raise exists from None
    except OSError:
        pass  # a file system without hard links (FAT, exFAT, some network shares)
    else:
        os.unlink(temp)
        return
    try:
        os.close(os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600))
    except FileExistsError:
        raise exists from None
    os.replace(temp, target)


def extract_into(out: BinaryIO, segments: Iterable[bytes], size: int,
                 keep_invalid: bool) -> Tuple[Optional[ArchiveChecker], Optional[VaultError]]:
    """Checks the archive while it decrypts and writes each segment only after the checks of what
    it holds. A broken archive is reported once every segment authenticated, so corrupt wins."""
    checker: Optional[ArchiveChecker] = None
    problem: Optional[VaultError] = None
    try:
        checker = ArchiveChecker(size)
    except VaultError as error:
        problem = error
    for plaintext in segments:
        if problem is None and checker is not None:
            try:
                checker.feed(plaintext)
            except VaultError as error:
                problem = error
        if problem is None or keep_invalid:
            out.write(plaintext)
    if problem is None and checker is not None:
        try:
            checker.finish()
        except VaultError as error:
            problem = error
    if problem is not None and not keep_invalid:
        raise problem
    return checker, problem


def stream_archive(vault: BinaryIO, header: Header, head: bytes, cek: bytes, file_size: int,
                   out: BinaryIO, keep_invalid: bool
                   ) -> Tuple[int, Optional[ArchiveChecker], Optional[VaultError]]:
    """The payload after read_head, decrypted and checked into `out` a segment at a time."""
    payload_size = file_size - HEADER_SIZE
    size = archive_size_of(payload_size)
    segments = open_segments(vault.read, header, head, cek, payload_size)
    return (size, *extract_into(out, segments, size, keep_invalid))


def _regular(st: os.stat_result, source: Path) -> None:
    if not stat.S_ISREG(st.st_mode):
        raise InputError(f"{source} is not a regular file (a pipe, for example): extract needs "
                         "to know the vault's size, so save it to a file first and extract that")


def command_extract(args: argparse.Namespace) -> int:
    source = Path(args.file)
    output = args.output or source.with_suffix(".tar").name
    _regular(os.stat(source), source)
    with open(source, "rb") as vault:
        st = os.fstat(vault.fileno())
        _regular(st, source)
        header, head = read_head(vault, st.st_size)
        if output != "-" and os.path.lexists(output) and not args.force:
            raise InputError(f"{output} exists; choose another name or pass --force")
        password = read_password(args.password_file, "Vault password: ")
        cek = content_key(header, head, password=normalize_password(password))
        if output == "-":
            size, checker, problem = stream_archive(vault, header, head, cek, st.st_size,
                                                    sys.stdout.buffer, args.keep_invalid_archive)
            sys.stdout.buffer.flush()
        else:
            with private_output(Path(output), args.force) as out:
                size, checker, problem = stream_archive(vault, header, head, cek, st.st_size,
                                                        out, args.keep_invalid_archive)

    if problem is not None:
        sys.stderr.write(
            f"{problem.category}: {problem.message}\n"
            f"Every segment authenticated, so the archive was written as it is "
            f"({size} bytes) because of --keep-invalid-archive. It breaks the profile and "
            "may hold links or paths outside the current directory: list it with 'tar tvf' and "
            "unpack it into an empty directory.\n"
        )
        return EXIT_CODES[problem.category]
    assert checker is not None
    sys.stderr.write(f"verified: {checker.files} files and {checker.directories} directories, "
                     f"{size} bytes of archive\n")
    if output != "-":
        private = " (readable by you only)" if os.name == "posix" else ""
        sys.stderr.write(f"written to {output}{private}; unpack it with:\n"
                         f"  tar xf {output}\n")
    return 0


def command_create(args: argparse.Namespace) -> int:
    root = Path(args.directory)
    if not root.is_dir():
        raise InputError(f"{root} is not a directory")
    target = Path(args.file)
    if target.exists() and not args.force:
        raise InputError(f"{target} exists; choose another name or pass --force")
    try:
        planned = plan_archive(collect_entries(root))
    except ValueError as error:
        raise InputError(str(error)) from None

    password = read_password(args.password_file, "New vault password: ")
    if not args.password_file and PASSWORD_ENV not in os.environ:
        if ask_password("Repeat it: ") != password:
            raise InputError("the two passwords differ")
    problem = password_problem(password)
    if problem:
        raise InputError(problem)

    salt, cek = secrets.token_bytes(16), secrets.token_bytes(KEY_SIZE)
    header = sealed_header(kek=derive_kek(normalize_password(password), salt, WRITE_ITERATIONS),
                           cek=cek, iterations=WRITE_ITERATIONS, salt=salt,
                           vault_id=secrets.token_bytes(16), key_id=secrets.token_bytes(16),
                           written_at=int(time.time()), wrap_nonce=secrets.token_bytes(12),
                           nonce_prefix=secrets.token_bytes(7))
    with private_output(target, args.force) as out:
        sealer = Sealer(out, header, cek)
        try:
            for piece in archive_stream(planned):
                sealer.write(piece)
        except ValueError as error:
            raise InputError(str(error)) from None
        sealer.close()

    size = archive_size(member.size for member in planned)
    sys.stderr.write(f"wrote {target}: {len(planned)} entries, {size} bytes of archive\n")
    return 0


def command_vectors(args: argparse.Namespace) -> int:
    directory = Path(args.directory)
    if not args.check:
        generate_vectors(directory)
        return 0
    failures = check_vectors(directory)
    for failure in failures:
        sys.stderr.write(f"FAIL {failure}\n")
    return EXIT_VECTORS if failures else 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(
        prog="cuevault.py", description="Open, create and test Cue: vault containers (format 1).",
        epilog="Exit codes: 0 success, 1 failure, 2 usage, 3 not_a_vault, 4 unsupported_version, "
               "5 bad_header, 6 wrong_password, 7 stale_key, 8 corrupt, 9 bad_archive, "
               f"10 vectors failed. The password can come from ${PASSWORD_ENV}.",
    )
    commands = root.add_subparsers(dest="command", required=True)

    extract = commands.add_parser("extract", help="verify and decrypt a vault into a tar archive")
    extract.add_argument("file", help="the .cuevault file")
    extract.add_argument("-o", "--output", help="the tar file to write, '-' for standard output, "
                                                "which is written as the vault verifies: trust it "
                                                "only when the exit code is 0 (default: the "
                                                "vault's name with .tar)")
    extract.add_argument("--password-file", help="read the password from this file")
    extract.add_argument("--force", action="store_true", help="overwrite an existing output")
    extract.add_argument("--keep-invalid-archive", action="store_true",
                         help="write the archive even if it breaks the archive profile, once every "
                              "segment authenticated (recovery; exits with 9 all the same)")
    extract.set_defaults(run=command_extract)

    create = commands.add_parser("create", help="build a vault from a directory")
    create.add_argument("directory")
    create.add_argument("file", help="the .cuevault file to write")
    create.add_argument("--password-file", help="read the password from this file")
    create.add_argument("--force", action="store_true", help="overwrite an existing file")
    create.set_defaults(run=command_create)

    vectors = commands.add_parser("vectors", help="generate or check the test vectors")
    vectors.add_argument("--check", action="store_true",
                         help="verify instead of writing; fails on any difference")
    vectors.add_argument("directory", help="the vector directory, holding vectors.json")
    vectors.set_defaults(run=command_vectors)
    return root


@contextlib.contextmanager
def signals_as_exit() -> Iterator[None]:
    """SIGTERM and SIGHUP raise SystemExit, so that private_output removes its partial file."""
    def leave(signum: int, _frame: object) -> None:
        raise SystemExit(128 + signum)

    previous = {}
    for name in ("SIGTERM", "SIGHUP"):
        number = getattr(signal, name, None)  # Windows has no SIGHUP
        if number is not None:
            previous[number] = signal.signal(number, leave)
    try:
        yield
    finally:
        for number, handler in previous.items():
            signal.signal(number, handler)


def main(argv: Optional[List[str]] = None) -> int:
    args = parser().parse_args(argv)
    try:
        if args.command in ("extract", "create"):
            with signals_as_exit():
                return args.run(args)
        return args.run(args)
    except VaultError as error:
        sys.stderr.write(f"{error.category}: {error.message}\n")
        return EXIT_CODES[error.category]
    except (InputError, OSError) as error:
        sys.stderr.write(f"error: {error}\n")
        return EXIT_FAILURE
    except KeyboardInterrupt:
        return EXIT_FAILURE


if __name__ == "__main__":
    sys.exit(main())
