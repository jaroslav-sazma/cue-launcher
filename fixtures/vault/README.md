# Vault test vectors

Shared test vectors of the Cue: vault container format. The normative specification is
`docs/vault-format.md` in the repository of Cue: React & Act; this file describes the schema of
`vectors.json`, what each case covers and where the values come from. The launcher's repository
holds the canonical copy of this directory: `scripts/cuevault.py` generates and checks it, CI runs
the check, and the launcher's Kotlin tests read it in place. The repository of
Cue: Vault & View (for Google Drive), Cue Vault for short, keeps an identical copy of `vectors.json`
and the `*.cuevault` files, not of this README, refreshed by its `tools/sync-vectors.sh`; its Swift
tests read that copy and, while the launcher's repository is checked out beside it, fail on any file
that differs from the canonical one. Change the vectors in the launcher's repository only.

## License

The files in this directory, like the specification and `scripts/cuevault.py`, are dedicated to the
public domain under CC0 1.0 Universal (`SPDX-License-Identifier: CC0-1.0`; the text is
`LICENSES/CC0-1.0.txt` at the repository root), unlike the rest of either repository, which is
GPL-3.0-or-later. Anyone may use them to implement or test the format, on any platform and under
any license. The dedication grants no trademark rights to the names and the icon of Cue:.

## Schema of `vectors.json`

All byte values are lowercase hex, all times Unix seconds, all strings UTF-8. Combining marks are
written as `\u` escapes, so that the inputs meant to be in NFD stay so; a JSON decoder reads them
like any other character.

The top level has `description` and four arrays:

| Member | Contents |
|---|---|
| `passwords` | password normalization cases: `input` (the password as typed), `description` and `expected`, the bytes PBKDF2 takes |
| `positive` | vaults that open: `name`, `description`, `inputs` and `expected` |
| `negative` | files that must fail: `name`, `description`, `base`, `mutation`, `credential` and `expected` |
| `accepted` | files no writer produces that every reader must open: the same members as a negative case |

A positive case's `inputs` hold everything a writer needs, and its `expected` what it must produce:

| Member | Contents |
|---|---|
| `inputs.password` | the password as typed, normalized as the specification describes |
| `inputs.iterations` | `kdf_iterations`, 100 000 in every case |
| `inputs.salt`, `vault_id`, `key_id` | 16 bytes each |
| `inputs.written_at` | `written_at` |
| `inputs.cek` | the 32-byte CEK |
| `inputs.wrap_nonce`, `nonce_prefix` | 12 and 7 bytes |
| `inputs.entries` | the archive's entries, in any order (see below) |
| `expected.file` | the file holding the container, `<name>.cuevault` |
| `expected.size`, `sha256` | the container's size and SHA-256 |
| `expected.kek` | the derived KEK |
| `expected.header` | the container's first 136 bytes |
| `expected.segments` | n, the number of segments |
| `expected.archive_size`, `archive_sha256` | P and the SHA-256 of the plaintext archive |

An entry has `path` (a directory's ending in `/`), `type` (`file` or `dir`) and `mtime`; a file also
has `content` in exactly one of four forms:

| Form | Bytes |
|---|---|
| `{"utf8": "text"}` | the text in UTF-8 |
| `{"hex": "00ff"}` | the bytes given |
| `{"repeat": {"byte": "a5", "count": 700}}` | that byte `count` times |
| `{"sequence": {"count": 63388}}` | `count` bytes where byte i is i mod 256 |

Opening a positive case's file, with its password or with its `cek` and `key_id` as a stored key,
yields exactly its entries, ordered by the bytes of their paths.

A negative case names the positive case it derives from in `base`, how it was derived in
`mutation`, what it is opened with in `credential` — `{"password": "…"}` or
`{"stored_key": {"key_id": "…", "cek": "…"}}` — and in `expected` the file to open, its SHA-256 and
the `error` category it must fail with. A case may also name `error_streaming`, the category a
reader that does not know the file's length up front — it streams the file, or learns the length
from a copy — may report instead, because it checks the credential before it can see the payload's
layout: such a reader passes with either, a reader that knows the length only with `error`. A
reader needs no more than the file and the credential; `mutation` records how the file came about,
and the generator reproduces it from that. With a `mutation` of `null` the file is the base case's
own. The mutations:

| `kind` | Members | Applied to the base case's file |
|---|---|---|
| `xor` | `offset`, `mask` | the mask's bytes XORed into the file from `offset` on |
| `set_bytes` | `offset`, `hex` | the bytes from `offset` on replaced by `hex` |
| `truncate` | `length` | the file cut to `length` bytes |
| `swap_segments` | `first`, `second` | the two sealed segments, both full, swapped in place |
| `replace_entries` | `entries` | a new container from all of the base case's inputs except the entries: these are written in the order given and without any of the profile's checks, so they may hold a `symlink` entry (with a `linkname`), a `..` segment, an NFD path or a duplicate, and are then sealed correctly |

An accepted case has the same members, but its `expected` holds only `file` and `sha256`, and its
`mutation` is always `replace_entries`. Opening its file with its credential yields exactly the
mutation's `entries`, in their order and with their paths byte for byte, unnormalized.

## Cases

The `passwords` cases show that the bytes are the input in NFC and nothing else: spaces, a tab,
dashes and case are kept, nothing is trimmed, the same text typed in NFD and in NFC gives the same
bytes, a compatibility character such as the ligature `ﬁ` is not decomposed (NFC, not NFKC), a
password of 8 code points after NFC has more bytes than code points, and the empty password gives
no bytes.

| Case | Covers |
|---|---|
| `empty` | a new vault: the archive is the two zero blocks, one segment of 1024 bytes; a passphrase with spaces |
| `few-files` | names with Czech diacritics in NFC, a subdirectory, an empty file and an empty directory; a password with diacritics |
| `long-path` | a root file name of exactly 100 bytes, and directory and file paths of up to 195 bytes split into `prefix` and `name`, one with two valid splits where the longest prefix wins; a password with dashes |
| `full-prefix` | a `prefix` of exactly 155 bytes with no NUL after it, once with a short `name` and once with a `name` of exactly 100 bytes, a path of 256 bytes that fills both fields; a mixed-case password with spaces |
| `exact-segment` | an archive of exactly 65 536 bytes, so its only segment is full: a directory, a file of 63 388 bytes (124 blocks, the last padded) and the end marker make 128 blocks; a password of exactly 8 code points, three of them two bytes in UTF-8 |
| `multi-segment` | an archive of 143 360 bytes in segments of 65 536, 65 536 and 12 288 bytes; the password typed in NFD |
| `wrong-password` | `few-files` with its password's first letter in lower case: `wrong_password` |
| `stored-key-stale` | `few-files` with a stored key of another `key_id`, as after a password change on another device: `stale_key` |
| `wrap-tag-flipped` | one bit of `wrap_tag` flipped: `wrong_password` |
| `key-id-changed` | `key_id` replaced, which is inside the wrap's AAD: `wrong_password` |
| `written-at-changed` | `written_at` replaced, which is outside the wrap's AAD but inside every segment's: `corrupt` |
| `segment-byte-flipped` | one bit flipped in the only segment: `corrupt` |
| `last-segment-dropped` | `multi-segment` without its last segment: `corrupt` |
| `segments-swapped` | `multi-segment` with its first two segments swapped: `corrupt` |
| `payload-cut-short` | 10 bytes of payload, less than a segment's minimum of 17: `bad_header` |
| `header-cut` | the file cut inside the header: `bad_header` |
| `cut-short-wrong-password` | `few-files` cut to 8 bytes of payload, opened with another password: `bad_header`, or `wrong_password` in a reader that streams a file of unknown length |
| `cut-short-stale-key` | `multi-segment` cut 5 bytes into its second segment, opened with a stale stored key: `bad_header`, or `stale_key` in a reader that streams a file of unknown length |
| `iterations-too-low`, `iterations-too-high` | `kdf_iterations` of 99 999 and 10 000 001: `bad_header` |
| `version-unknown` | `version` 2: `unsupported_version` |
| `magic-wrong` | the last byte of `magic` changed: `not_a_vault` |
| `too-short-for-magic` | five bytes: `not_a_vault` |
| `archive-symlink` | a correctly sealed archive with a symbolic link: `bad_archive` |
| `archive-dot-dot` | a correctly sealed archive with the path `../mimo.txt`: `bad_archive` |
| `archive-duplicate` | a correctly sealed archive with the same path twice: `bad_archive` |
| `archive-nfd-path` | accepted: a correctly sealed archive with a directory and a file path in NFD, read back byte for byte |
| `archive-normalization-twins` | accepted: an NFC file beside its NFD twin, and an NFC file beside an NFD directory of the same name with a file in it; paths compare as bytes, so these are five entries and nothing lies inside a file |

## Provenance

The inputs were written once by hand and by a throwaway script, and are not regenerated. Every
random-looking value is the first bytes of SHA-256 over an ASCII label
`cue-vault/vector/<case>/<field>` — for example `salt` of `empty` is the first 16 bytes of
SHA-256(`cue-vault/vector/empty/salt`) — and so are the stored key of `stored-key-stale`
(`stale/key_id`, `stale/cek`, also used by `cut-short-stale-key`), the `key_id` of `key-id-changed`
(`changed/key_id`) and the 32 bytes after `3082` in `few-files`'s `Certifikáty/osobní.p12`
(`few-files/p12`). The expected values are computed by `scripts/cuevault.py`, and the archives read
cleanly with bsdtar and Python's `tarfile`. The Kotlin reader and the Swift codec, which derives
and seals on CommonCrypto and CryptoKit, MUST recompute every expected value themselves rather than
copy anything from the Python code; that recomputation is the cross-check between the
implementations.

Every key and password in these files is public test material and must never protect anything
else. Several cases share a CEK and a nonce prefix with their base case, which a real writer never
does.

## Regenerating and checking

From the repository root, with the dependencies installed as `CONTRIBUTING.md` describes:

```bash
build/cuevault-venv/bin/python scripts/cuevault.py vectors fixtures/vault          # rewrite the expected values and the files
build/cuevault-venv/bin/python scripts/cuevault.py vectors --check fixtures/vault  # verify, as CI does
```

The generator rewrites every `expected` member of `vectors.json` (keeping each negative case's
`error` and `error_streaming`), writes every `*.cuevault` file and deletes those no case names. The
check rebuilds everything in memory and fails on any byte that differs, on a file no case names, on
a positive case that does not open to its entries with its password and its stored key, on a
negative case that opens or fails with another category than `error`, and on an accepted case that
does not open to its mutation's entries. It runs every case through the streaming reader of
`extract` as well: a positive or accepted case has to stream to the archive read in memory, a
negative one has to fail with `error` or its `error_streaming`, and a `bad_archive` case kept as
`--keep-invalid-archive` keeps it has to give its whole archive. Each positive archive, sealed again
under its header in writes of 1 and of 777 bytes, has to give its file byte for byte, and a file
that does not hold the bytes its size states — cut short in the header or a segment, or a byte too
long — has to stop the streaming reader with an input error rather than a category.

## Changing a vector

Format version 1 is frozen, so its vectors are too: a new case may be added, but an expected value
that changes means a new format version under the specification's change policy. A changed vector
lands with the specification and `scripts/cuevault.py`, and its copy reaches the repository of
Cue Vault through `tools/sync-vectors.sh` in the paired commit there.
