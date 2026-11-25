# Cue: vault container format

`vaultFormatVersion: 1`

This document, the reference implementation `scripts/cuevault.py` with its
`scripts/cuevault-requirements.txt`, and the test vectors in `fixtures/vault/` are dedicated to the
public domain under CC0 1.0 Universal (`SPDX-License-Identifier: CC0-1.0`, full text in
`LICENSES/CC0-1.0.txt`), apart from the GPL-3.0-or-later that covers the rest of the repository:
anyone may read and write vaults, on any platform and under any license. The dedication waives no
trademark rights; the names and the icon of Cue: stay as `NOTICE` reserves them.

This is the canonical, normative specification of the `.cuevault` file: one encrypted container that
holds a tar archive of files and directories. Cue: Vault & View (for Google Drive), Cue Vault for
short, on macOS reads and writes it, Cue: React & Act on Android reads it, and `scripts/cuevault.py`
reads and writes it without either app. The three are tested against the same vectors in
`fixtures/vault/`. The design aims at one property above all: whoever has the file and its password
can always get the data back, with nothing but this document, or with the script and the system's
`tar`.

The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are to be read as described in RFC 2119 and
RFC 8174 when, and only when, they appear in all capitals.

## Contents

- [Conventions](#conventions)
- [Overview](#overview)
- [Header](#header)
- [Keys](#keys)
- [Payload](#payload)
- [Archive](#archive)
- [Limits](#limits)
- [Opening a vault](#opening-a-vault)
- [Writing a vault](#writing-a-vault)
- [Passwords](#passwords)
- [Test vectors](#test-vectors)
- [Recovery](#recovery)
- [Change policy](#change-policy)

## Conventions

- Multi-byte integers are **little-endian**. `u8`, `u32le` and `u64le` are unsigned integers of one,
  four and eight bytes; `‖` is byte concatenation; `[a, b)` is the bytes from offset a up to, but
  not including, offset b; sizes are in bytes.
- Hex is lowercase with no separators. `ceil(x / y)` rounds up.
- AES-256-GCM is the AEAD of NIST SP 800-38D with a 32-byte key, a 12-byte nonce and a 16-byte tag;
  its output is the ciphertext followed by the tag. PBKDF2 is RFC 8018 §5.2 with HMAC-SHA256
  (RFC 2104, FIPS 180-4). UTF-8 is RFC 3629, NFC is Unicode Normalization Form C (UAX #15).
- The archive is a tar archive in the ustar interchange format of POSIX.1 (IEEE Std 1003.1), cut
  down to the profile under [Archive](#archive).
- Random values come from a cryptographically secure random number generator.
- The file name extension is `.cuevault`; macOS knows the type as the uniform type identifier
  `app.cue.vault.container`, which Cue Vault exports.

## Overview

```
password ──PBKDF2──▶ KEK ──AES-GCM unwrap──▶ CEK ──AES-GCM, per segment──▶ tar archive

+--------------------+-------------+-------------+-----+---------------+
| header (136 bytes) | segment 0   | segment 1   | ... | segment n − 1 |
+--------------------+-------------+-------------+-----+---------------+
                     |<──────────────── payload, L bytes ─────────────▶|
```

- The **content encryption key** (CEK) is 32 random bytes. It encrypts the archive, cut into
  segments of 65 536 bytes, each sealed on its own.
- The **key encryption key** (KEK) is derived from the password. It seals the CEK into the header.
- A device MAY keep the CEK (for example behind biometrics) and open the vault with it later
  without the password. The header's `key_id` tells such a device whether its CEK is still the
  current one.

## Header

The header is the first 136 bytes of the file.

| Offset | Size | Field | Contents |
|---|---|---|---|
| 0 | 8 | `magic` | `CUEVAULT` in ASCII: `43 55 45 56 41 55 4c 54` |
| 8 | 1 | `version` | `u8`, `1` |
| 9 | 4 | `kdf_iterations` | `u32le`, the PBKDF2 iteration count; written as 600 000, read within 100 000–10 000 000 |
| 13 | 16 | `kdf_salt` | random, new with every new CEK |
| 29 | 16 | `vault_id` | random when the vault is created, never changed afterwards |
| 45 | 16 | `key_id` | random, new with every new CEK |
| 61 | 8 | `written_at` | `u64le`, Unix time in seconds of the write that produced this file |
| 69 | 12 | `wrap_nonce` | random, new every time the CEK is wrapped |
| 81 | 32 | `wrapped_cek` | the CEK encrypted under the KEK |
| 113 | 16 | `wrap_tag` | the GCM tag of that encryption |
| 129 | 7 | `nonce_prefix` | random, new with every write of the file |
| 136 | L | payload | the sealed segments, see [Payload](#payload) |

`vault_id` names the vault across rewrites and password changes, so that a device can tell its own
vault from another one. `written_at` lets a reader show how old the copy in front of it is, for
example a stale copy a sync service has not refreshed yet. Neither is secret.

## Keys

```
KEK = PBKDF2-HMAC-SHA256(password bytes, kdf_salt, kdf_iterations, 32 bytes)

wrapped_cek ‖ wrap_tag = AES-256-GCM-Encrypt(key = KEK, nonce = wrap_nonce,
                                             plaintext = CEK, aad = header[0, 61))
```

- The password bytes are the password after the normalization under [Passwords](#passwords).
- The wrap's additional authenticated data is the header's first 61 bytes: `magic`, `version`,
  `kdf_iterations`, `kdf_salt`, `vault_id` and `key_id`. A changed key derivation parameter, vault
  or key id makes the unwrap fail as surely as a wrong password does.
- `written_at`, `wrap_nonce`, the wrap itself and `nonce_prefix` are outside that range, so a writer
  that holds only the CEK can rewrite the file without the password; every segment authenticates
  the whole header, which covers them too.
- A **new CEK** comes with a new `key_id`, a new `kdf_salt` and a new `wrap_nonce`, and the whole
  payload is encrypted again under it. A writer generates a new CEK when it creates a vault and when
  the password changes; the old CEK and the old password then open nothing written afterwards. The
  CEK is never derived from the password, so an old password and a new one share nothing.

## Payload

The payload is the archive of P bytes, `P ≥ 1`, encrypted in **segments**; [Limits](#limits) says
how large P can be.

- The writer cuts the archive into `n = ceil(P / 65 536)` segments of plaintext. Segments 0 to
  n − 2 hold 65 536 bytes each; the last one holds the rest, 1 to 65 536 bytes. Since an archive is
  never shorter than 1024 bytes, there is never an empty segment.
- Segment i is sealed on its own:

  ```
  sealed_i = AES-256-GCM-Encrypt(key = CEK, nonce = nonce_i, plaintext = segment_i,
                                 aad = header[0, 136))
  ```

  It is 16 bytes longer than its plaintext: 65 552 bytes for a full segment. The payload is
  `sealed_0 ‖ sealed_1 ‖ … ‖ sealed_(n−1)`, with nothing between or after them.
- The nonce of segment i is 12 bytes:

  | Offset | Size | Contents |
  |---|---|---|
  | 0 | 7 | `nonce_prefix` from the header |
  | 7 | 4 | i as `u32le`, starting at 0 |
  | 11 | 1 | `01` for the last segment (i = n − 1), `00` for every other one |

  The index makes a reordered segment fail its tag, and the last flag makes a file that lost its
  final segments fail: the segment that is now last was sealed without the flag. The additional
  authenticated data of every segment is the **whole** header, so a changed byte anywhere in it
  fails every segment.

A reader derives n and P from the payload's length L alone (the file's size minus 136):

```
n    = ceil(L / 65 552)
last = L − (n − 1) · 65 552          the length of the last sealed segment
P    = L − 16 · n
```

The layout is valid only if `1 ≤ n ≤ 2^32` and `17 ≤ last ≤ 65 552` — every sealed segment carries
at least one byte of plaintext and its tag, and every index fits the nonce's `u32le`. Because every
segment before the last is exactly 65 552 bytes long and the last one is 17 to 65 552 bytes long, n
is the only count that fits: for example an archive of P = 143 360 bytes gives n = 3 and
L = 143 360 + 48 = 143 408, and the reader computes ceil(143 408 / 65 552) = 3
(2 · 65 552 = 131 104 < 143 408 ≤ 196 656), a last sealed segment of 12 304 bytes and
P = 143 408 − 48 = 143 360. An archive of exactly 65 536 bytes gives L = 65 552, n = 1 and a full
last segment. An L with no valid layout — 0, one whose last segment would hold 1 to 16 bytes, or one
that needs more than 2^32 segments — is rejected as `bad_header`.

A reader hands out no plaintext of a segment before that segment's tag verified, and it need not
hold the archive in memory. A reader that knows the file's length MAY verify and decrypt the
segments in any order, for random access: segment i starts at byte 136 + i · 65 552 of the file, and
the last one, i = n − 1 with the flag, is known from the length. The archive as a whole is verified
only once every segment has verified, the last one included: a reader that streams, or that hands
out segments before it has verified them all, MUST treat the archive as unverified until then.

The nonce prefix is 56 random bits, new with every write, and the same CEK serves every write until
the password changes. A vault is written thousands of times in its life, not billions, so two
writes picking the same prefix is negligibly likely; within one write the index keeps the nonces
apart.

## Archive

The plaintext is a tar archive in a strict ustar profile. Two independent writers given the same
entries MUST produce the same bytes, which the vectors check byte for byte, and the system's
`tar xf` MUST be able to unpack it after decryption, which is the recovery path.

### Entries and paths

- An entry is a regular file (typeflag `0`) or a directory (typeflag `5`). Symbolic links, hard
  links, devices, FIFOs, pax headers (`x`, `g`), GNU extensions (`L`, `K` and the rest) and the old
  typeflag NUL do not occur.
- A path is relative and in UTF-8. It does not start with `/`, contains no NUL, and none of its
  `/`-separated segments is empty, `.` or `..`. A directory's path ends in `/`, a file's does not.
  The root itself has no entry.
- A writer normalizes every path it takes to **NFC** (macOS file systems may hand out NFD), with
  the Unicode version it has. A reader does not check the normalization: NFC changes with the
  Unicode version, the readers' platforms know different ones, and a path one writer normalized need
  not count as NFC for every reader.
- Paths compare as their exact UTF-8 bytes. No two entries share a path; a file `a` and a
  directory `a/` count as the same path. No entry lies inside a file, that is, no path continues a
  file's path with `/`. A path and its twin in another normalization are two entries; a reader that
  unpacks them onto a file system that takes them for one name, as APFS does, settles the clash
  itself, for example by not overwriting.
- A writer stores every directory as an entry of its own, every ancestor of every file and every
  empty directory included, and refuses entries whose parent directory is not among them.
- A writer orders the entries by the bytes of their UTF-8 paths, compared as unsigned bytes, with
  the directories' trailing `/` included. A directory therefore always precedes everything inside
  it. A reader does not depend on the order and treats a missing parent directory as implied.
- When a writer builds the archive from a directory on macOS, it leaves out every entry named
  `.DS_Store` or starting with `._`, at any depth, and at the top level only `.fseventsd`,
  `.Spotlight-V100`, `.Trashes`, `.TemporaryItems`, `.DocumentRevisions-V100` and
  `.metadata_never_index`, each with everything inside it when it is a directory (the last one is a
  file).

### Name and prefix

A path of at most 100 bytes goes into `name` alone and `prefix` stays empty. A longer path is split
at one of its `/`: the part before that `/` goes into `prefix`, the part after it into `name`, and
the `/` itself is stored in neither. The writer takes the **longest** prefix that is at most 155
bytes long and leaves 1 to 100 bytes for `name`; if no `/` allows that, it refuses the path. A
reader joins a non-empty `prefix`, `/` and `name`. A `name` of exactly 100 bytes or a `prefix` of
exactly 155 bytes fills its field with no NUL after it.

For example, a file path of 195 bytes with its `/` at the offsets 8, 61, 118 and 139 has two valid
splits: a prefix of 118 bytes, leaving 76 for `name`, and one of 139 bytes, leaving 55. The writer
takes the second. The `long-path` vector holds this path, and the `full-prefix` vector paths whose
`prefix` is exactly 155 bytes long.

### Header block

Each entry starts with a 512-byte header block, followed by the file's content padded with NUL to
a multiple of 512 bytes; a directory has no content. The fields, with exactly these bytes:

| Offset | Size | Field | Contents |
|---|---|---|---|
| 0 | 100 | `name` | the path or its part after the split, NUL-filled |
| 100 | 8 | `mode` | `0000644` NUL for a file, `0000755` NUL for a directory |
| 108 | 8 | `uid` | `0000000` NUL |
| 116 | 8 | `gid` | `0000000` NUL |
| 124 | 12 | `size` | the content size as 11 octal digits, zero-padded, then NUL; `00000000000` NUL for a directory |
| 136 | 12 | `mtime` | the modification time in whole Unix seconds as 11 octal digits, zero-padded, then NUL |
| 148 | 8 | `chksum` | 6 octal digits, zero-padded, then NUL and a space |
| 156 | 1 | `typeflag` | `0` or `5` |
| 157 | 100 | `linkname` | all NUL |
| 257 | 6 | `magic` | `ustar` NUL |
| 263 | 2 | `version` | `00` |
| 265 | 32 | `uname` | all NUL |
| 297 | 32 | `gname` | all NUL |
| 329 | 8 | `devmajor` | `0000000` NUL |
| 337 | 8 | `devminor` | `0000000` NUL |
| 345 | 155 | `prefix` | the part before the split, NUL-filled |
| 500 | 12 | — | all NUL |

Octal digits are the ASCII characters `0` to `7`. `chksum` is the sum of all 512 bytes of the block,
taken as unsigned values, with the 8 bytes of `chksum` itself counted as spaces (`0x20`). A writer
takes the modification time rounded down to whole seconds and clamps it to `0` …
`8^11 − 1` (8 589 934 591).

After the last entry come exactly two blocks of 512 NUL bytes, and the archive ends there: it is
**not** padded to a 10 240-byte record. An empty vault is these two blocks alone, 1024 bytes.

### What a reader checks

A reader MUST reject an archive as `bad_archive` unless all of the following hold, and MUST ignore
everything else in it (`mode`, `uid`, `gid`, `linkname`, `uname`, `gname`, `devmajor`, `devminor`,
the 12 bytes after `prefix`, the bytes after the NUL that ends `name` or `prefix`, and the padding
after the content):

- P is a multiple of 512 and at least 1024.
- Every header block has `magic` `ustar` NUL and `version` `00`, and its `chksum` field is 6 octal
  digits, NUL and a space whose value equals the checksum computed as above.
- `typeflag` is `0` or `5`.
- `size` and `mtime` are each 11 octal digits followed by NUL; a directory's `size` is 0.
- `name` is not empty; the path (`name`, or `prefix` `/` `name`) is valid UTF-8 and follows the
  rules under [Entries and paths](#entries-and-paths), except the order, the presence of parent
  directories and the normalization.
- The content of every entry ends within the archive.
- A block of 512 NUL bytes where a header is expected is followed by exactly one more such block,
  and that block ends the archive.

## Limits

The format bounds sizes only where its fields do:

- The segment index is a `u32le` in the nonce, so a payload has at most **2^32 segments**:
  P ≤ 2^32 · 65 536 = 2^48 bytes, and a file is at most 136 + 2^32 · 65 552 =
  **281 543 696 187 528** bytes. A writer refuses a larger archive. A reader rejects a longer
  payload as `bad_header`: from the length alone, before any cryptography, when it knows the file's
  length; from its copy's length, before any segment is decrypted, when it learns the length from a
  copy; and as soon as segment number 2^32 + 1 appears when it streams.
- The `size` field of a header block holds 11 octal digits, so the content of one file is at most
  **8^11 − 1** (8 589 934 591) bytes, 8 GiB less one byte. A writer refuses a larger file.
- A reader rejects `kdf_iterations` outside **100 000 … 10 000 000** as `bad_header`, so a forged
  header cannot keep a phone busy for hours. A writer writes 600 000 and never anything outside the
  reader's range.

An implementation MAY refuse a vault larger than it can handle, for example one that does not fit
its memory. Such a limit is the implementation's, not the format's, and the implementation says so
rather than reporting one of the categories under [Opening a vault](#opening-a-vault).

## Opening a vault

A vault opens either with the password or with a CEK a device stored earlier together with the
`key_id` it came with. The checks run in this order, and the first that fails decides the error:

1. Fewer than 8 bytes, or the first 8 are not `magic`: `not_a_vault`.
2. There is a ninth byte and `version` is not 1: `unsupported_version`.
3. The file is shorter than 136 bytes: `bad_header`.
4. `kdf_iterations` is outside 100 000 … 10 000 000: `bad_header`.
5. The payload's length L gives no valid layout (see [Payload](#payload)), more than 2^32 segments
   included: `bad_header`.
6. With a stored CEK: its `key_id` differs from the header's `key_id`: `stale_key`. With a password:
   the unwrap of `wrapped_cek ‖ wrap_tag` fails its tag: `wrong_password`.
7. A segment fails its tag: `corrupt`.
8. The archive breaks the profile under [Archive](#archive): `bad_archive`.

A reader that knows the file's length up front MUST keep this order. A reader that does not can
check the payload's length — step 5 — only while or after it reads the payload, after step 6. One
that streams the file rejects the payload as `bad_header` as soon as segment number 2^32 + 1
appears, or when the payload ends in a last segment of fewer than 17 bytes; one that first copies
the file and learns the length from its copy rejects it from that length, before it decrypts any
segment. For a file whose layout is invalid either MAY therefore report `wrong_password` or
`stale_key` instead of `bad_header` when the credential is wrong as well.

| Category | Meaning |
|---|---|
| `not_a_vault` | not a `.cuevault` file at all |
| `unsupported_version` | a vault of a format version this reader does not know |
| `bad_header` | the header or the file's length is out of bounds: too short, too long, iterations out of range, no valid segment layout |
| `wrong_password` | the CEK does not unwrap: the password is wrong, or the first 61 bytes or the wrap fields of the header are damaged |
| `stale_key` | the stored CEK belongs to another `key_id` (the password changed since); the reader asks for the password instead. Device-level: it never arises when opening with a password |
| `corrupt` | a segment does not authenticate: the file is damaged, cut short at a segment boundary, reordered, or its header changed outside the wrap's range |
| `bad_archive` | every segment authenticated, but the archive breaks the profile |

A damaged file is always detected; a reader never hands out plaintext that did not verify, and
never unpacks an archive that breaks the profile. When a file has both a damaged segment and an
archive that breaks the profile, the reported category is `corrupt`: a reader that checks the
archive while it decrypts keeps decrypting to the end before it reports `bad_archive`. A file cut
short inside its last segment reads as `corrupt` when at least 17 bytes of that segment remain and
as `bad_header` when fewer do, because the layout itself is then invalid.

## Writing a vault

- **Creating.** New random `vault_id`, CEK, `key_id`, `kdf_salt`, `wrap_nonce` and `nonce_prefix`;
  `kdf_iterations` 600 000; the KEK from the password; the CEK wrapped; the archive sealed.
- **Saving changed content.** A writer that holds the CEK keeps `version`, `kdf_iterations`,
  `kdf_salt`, `vault_id`, `key_id`, `wrap_nonce`, `wrapped_cek` and `wrap_tag` of the previous
  header, sets `written_at` and a new random `nonce_prefix`, and seals the new archive. It needs
  neither the password nor the KEK.
- **Changing the password.** Like creating, except that `vault_id` stays: a new CEK, `key_id`,
  `kdf_salt`, `wrap_nonce` and `nonce_prefix`, and the whole archive sealed again. Devices that
  stored the old CEK then meet `stale_key` and ask for the new password.

## Passwords

The user always types the password; no writer generates one. A new password has at least 8
Unicode code points after NFC normalization. Only a writer enforces this, when it takes a new
password; a reader takes any password.

Every reader and writer turns the typed password into the bytes PBKDF2 takes in the same way: the
input normalized to NFC and encoded in UTF-8. Nothing else changes: case and dashes are kept,
nothing is trimmed, and whitespace counts.

NFC depends on the Unicode version an implementation knows. By Unicode's normalization stability
policy, every implementation that knows Unicode 13.0 (the version of Python 3.9) or a later one
turns a password made of characters assigned in 13.0 or earlier into the same bytes. For a character
assigned later, an implementation that knows an older version may produce other bytes, and the vault
then does not open there with that password. A writer SHOULD therefore refuse a new password that
holds a code point its own Unicode version leaves unassigned (general category `Cn`);
`scripts/cuevault.py` and Cue Vault do.

The password's strength is the user's choice, and it is what protects a vault whose file leaked:
PBKDF2 with 600 000 iterations still allows tens of thousands of guesses a second on one GPU against
a stolen file, so a short or dictionary password falls. A long passphrase is recommended.

## Test vectors

`fixtures/vault/` holds the vectors. `vectors.json` gives every input explicitly — passwords,
iterations (100 000, to keep tests fast), salts, ids, `written_at`, CEKs, nonces and entries — with
the expected KEK, header, archive size and SHA-256, and the expected file with its size and
SHA-256; the `*.cuevault` files beside it are those files. `fixtures/vault/README.md` describes the
schema and what each case covers.

- A reader MUST open every positive case both with its password and with its CEK and `key_id` as a
  stored key, and find exactly the entries the case lists. It MUST open every accepted case and find
  exactly the entries its archive holds, in their order and with their paths byte for byte. It MUST
  fail every negative case with exactly its expected category; where a case names a second category
  for streaming, a reader that does not know the file's length MAY fail with that one instead (see
  [Opening a vault](#opening-a-vault)).
- A writer MUST reproduce every positive file byte for byte from its inputs.
- Every implementation MUST turn each input of `passwords` into its expected bytes.

The positive cases cover an empty vault, names with diacritics with an empty file and an empty
directory, paths that need `prefix` (a `name` of exactly 100 bytes, a `prefix` of exactly 155 bytes
and both at once), an archive of exactly one full segment and one of three segments. The accepted
cases hold archives no writer produces but every reader opens: paths in NFD, and paths that differ
only in their normalization. The negative cases cover every category and the byte-level damage this
document describes: flipped tag and segment bits, a changed `key_id` or `written_at`, a dropped last
segment, swapped segments, cut files (also together with a wrong password or a stale stored key),
iterations out of range, an unknown version, a wrong magic, and correctly sealed archives that hold
a symbolic link, a `..` segment or a duplicate path. Every case opens with a password, except those
with a stale stored key.

The vectors change in the launcher's repository only. `scripts/cuevault.py vectors fixtures/vault`
regenerates them from `vectors.json` and `scripts/cuevault.py vectors --check fixtures/vault`
verifies them; the repository of Cue Vault keeps an identical copy of `vectors.json` and the
`*.cuevault` files, refreshed by its `tools/sync-vectors.sh`.

## Recovery

Without either app, a vault opens with `scripts/cuevault.py`, which needs Python 3.9 or newer and
the packages pinned with their hashes in `scripts/cuevault-requirements.txt`. On macOS and Linux:

```bash
python3 -m venv cuevault-venv
cuevault-venv/bin/pip install --require-hashes -r cuevault-requirements.txt
cuevault-venv/bin/python cuevault.py extract vault.cuevault -o vault.tar
tar xf vault.tar
```

On Windows, with the `py` launcher that the Python installer brings (Windows 10 and later have
`tar`):

```bat
py -m venv cuevault-venv
cuevault-venv\Scripts\pip install --require-hashes -r cuevault-requirements.txt
cuevault-venv\Scripts\python cuevault.py extract vault.cuevault -o vault.tar
tar xf vault.tar
```

The hashes cover ready-built packages for macOS on Apple silicon, Linux on x86_64 and aarch64
(glibc) and Windows on x86_64. The release of `cryptography` that
`scripts/cuevault-requirements.txt` pins has none for Intel Macs: pip builds it from source there,
which needs a Rust toolchain, as on any other platform without one.

`extract` asks for the password and writes the tar segment by segment as it verifies the file and
checks the archive, so neither has to fit in memory; on macOS and Linux the tar is readable by its
owner only, while on Windows it takes the permissions of the folder it is written to. It reads only
a regular file, as it needs the vault's size up front: a vault that comes through a pipe, such as
`/dev/stdin` or `<(…)`, is refused with exit code 1 and has to be saved to a file first. Data that
did not authenticate is never written, and when any check fails, or SIGTERM or SIGHUP ends the
command (exit code 128 plus the signal's number), the tar written so far is removed, so only a tar
that verified in full is left; without `--force` it never replaces a file, not even one that
appeared while it ran. `-o -` writes it to standard output instead, where a failure cannot take back
what was already written: trust that output only when the exit code is 0. The password can also come
from `--password-file` or the environment variable `CUEVAULT_PASSWORD`; a password that is not valid
UTF-8 is refused with exit code 1. When every segment authenticated but the archive breaks the
profile (`bad_archive`), `--keep-invalid-archive` keeps it all the same, prints what is wrong and
still exits with 9. Such an archive may hold links or paths outside the current directory, so list
it with `tar tvf` first and unpack it into an empty directory. `create DIRECTORY FILE` builds a
vault from a directory with 600 000 iterations, reading the files piece by piece straight into the
sealed file, and asks for the new password twice; it removes an unfinished file the same way. The
exit code tells the outcome: 0 success, 1 any other failure, 2 wrong usage, 3 `not_a_vault`, 4
`unsupported_version`, 5 `bad_header`, 6 `wrong_password`, 7 `stale_key`, 8 `corrupt`, 9
`bad_archive`, 10 a vector that did not check out. Code 7 is kept for the category's sake but never
occurs: the script opens a vault with a password only, never with a stored key. The step-by-step
guide for users is in the README of Cue Vault, which creates and manages the vault.

## Change policy

Format version 1 is frozen: every byte this document describes stays as it is. New test vectors may
be added without a new version. Any change to the layout, the cryptography, the archive profile, the
limits or the password normalization is a new format version, written into `version` and the header
of this document, and readers refuse every version they do not know with `unsupported_version`. A
change lands with this document, the vectors, `scripts/cuevault.py` and the Kotlin reader in this
repository, and as a paired commit with the Swift codec and its copy of the vectors in the
repository of Cue Vault.
