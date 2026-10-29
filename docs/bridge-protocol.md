# Cue: bridge protocol

`bridgeProtocolVersion: 1`

This document and the test vectors in `fixtures/bridge/` are dedicated to the public domain under
CC0 1.0 Universal (`SPDX-License-Identifier: CC0-1.0`, full text in `LICENSES/CC0-1.0.txt`), apart
from the GPL-3.0-or-later that covers the rest of the repository: anyone may implement the protocol,
on any platform and under any license. The dedication waives no trademark rights; the names and the
icon of Cue: stay as `NOTICE` reserves them.

This is the canonical, normative specification of the protocol between Cue: React & Act on Android
(the phone) and Cue: Relay & Reply on macOS (the Mac). Both repositories implement it and neither
restates it: the launcher's Kotlin codec and the bridge's Swift codec are tested against the same
vectors in `fixtures/bridge/`, and each side asserts that its own protocol version equals the one in
the header above.

The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are to be read as described in RFC 2119 and
RFC 8174 when, and only when, they appear in all capitals.

## Conventions

- Multi-byte integers are big-endian. `u8`, `u16be` and `u32be` are unsigned integers of one, two
  and four bytes; `‖` is byte concatenation.
- Hex is lowercase with no separators. Base64 is the standard alphabet with padding (RFC 4648 §4).
- Text is UTF-8 without a byte order mark. JSON follows RFC 8259; an `int` is a JSON number in the
  signed 32-bit range, a `long` one in the signed 64-bit range.
- SHA-256 is FIPS 180-4, HMAC-SHA256 is RFC 2104, HKDF-SHA256 is RFC 5869, X25519 is RFC 7748 and
  ChaCha20-Poly1305 is the AEAD of RFC 8439.
- Times are in milliseconds; an instant is milliseconds since the Unix epoch.

## Terminology and roles

| Term | Meaning |
|---|---|
| Mac | Cue: Relay & Reply, the bridge app on macOS. It is the hub: the TLS server, the Bonjour advertiser and the BLE peripheral. It never initiates a TCP connection. |
| Phone | Cue: React & Act, the launcher on Android. It roams: the TLS client, and the BLE central bound by the operating system's companion-device presence. |
| Peer | The other side of a pairing, from either side's point of view. |
| Fingerprint | SHA-256 of a leaf certificate in DER form: 32 bytes, written as 64 hex digits. |
| Pin | The peer's fingerprint, stored when a pairing commits. |
| Pairing mode | The time in which the Mac takes a pairing: from its user starting it until a ceremony ends, or until 120 seconds pass without one. |
| Commitment | SHA-256 over the phone's ephemeral public key, sent before the phone learns the Mac's. |
| SAS | The six-digit short authentication string the phone shows and the Mac's user types in during pairing. |
| `K_ble` | The 32-byte key that seals BLE frames, derived during pairing. |
| Pairing connection | A TCP connection that runs the pairing ceremony and nothing else. |
| Session connection | A TCP or L2CAP connection between paired devices that carries everything else. |
| Signal | One BLE frame, carried over GATT or inside a `signal` message. |
| DevPresence | The debug-only transport for the emulator, where BLE does not exist: signals travel inside `signal` messages on a session connection. |

## Versioning

`bridgeProtocolVersion` is an integer, `1` for this document. It appears in three places: the
header of this document, the `v` field of `hello` and the `v` key of the Bonjour TXT record. Each
side's code carries it as one constant, and a contract test on each side fails if the constant and
the header above differ.

There is no negotiation and no downgrade. Both sides require the peer's version to equal their own:

- The phone does not connect to a Bonjour instance whose TXT `v` differs from its own version.
- A side that receives a `hello` with a different `v` sends `bye` with the reason `version-mismatch`
  and closes the connection.

### Change policy

A change is **additive** when an implementation of the unchanged protocol still interoperates with
it: a new optional field, a new message type, a new BLE frame type, a new `bye` or `pair-abort`
reason, a new vector. Additive changes keep the version; receivers already ignore what they do not
know (see [Malformed and unknown input](#malformed-and-unknown-input)).

A change is **breaking** when anything else changes: a field is removed, renamed or becomes
required, a meaning or a limit changes, or anything in the framing, the cryptography or the pairing
ceremony changes. A breaking change bumps `bridgeProtocolVersion` and lands as a pair of commits,
one in each repository, that reference the same version: the launcher commit updates this document,
the vectors and the Kotlin codec; the Bridge commit updates the Swift codec and its copy of the
vectors.

## Discovery

The Mac advertises one Bonjour (DNS-SD) service while the bridge is enabled and the Mac is paired or
in pairing mode:

| Property | Value |
|---|---|
| Service type | `_cuebridge._tcp` |
| Instance name | The Mac's hostname, as macOS already advertises it through its own services |
| Port | The port the TLS listener is bound to |
| TXT record | `v=1` — the protocol version as decimal digits — and, only while the Mac is in pairing mode, `pair=1` |

The Mac updates the TXT record in place when pairing mode starts and when it ends, on the same port.
Nothing else is advertised, and no identity is sent before a TLS handshake completes. A receiver
ignores TXT keys it does not know.

To pair, the phone browses `_cuebridge._tcp` for at most 10 seconds and keeps the instances whose
TXT record carries both its own version as `v` and `pair=1`. With exactly one, it connects to it;
with several, its user picks one by instance name; with none, it tells its user to start pairing
mode on the Mac, on the same network. The launcher stops browsing 2 seconds after the first instance
it keeps, and releases the browse and every resolve when it stops. The flag only says where to look:
trust never derives from discovery, and a false advertisement leads at worst into a ceremony whose
code the Mac's user never gets to type in (see [Pairing ceremony](#pairing-ceremony)).

After pairing, the phone connects to the endpoint (host and port) saved when the pairing committed;
it MAY resolve the saved instance name to refresh that endpoint. The launcher does so when a
connection to the saved endpoint fails, tries the resolved endpoint once, and replaces the saved one
only after the pinned handshake succeeded there, so a wrong or spoofed advertisement leads at worst
to a connection that fails the pinned handshake. In DevPresence nothing is browsed or resolved: the
endpoint is entered by hand in the launcher's debug settings, for the pairing and afterwards.

## Transport security

Every TCP connection is TLS 1.3 with mutual authentication; earlier TLS versions MUST be refused.
The Mac is the TLS server, the phone the TLS client. A session connection on the L2CAP carrier uses
no TLS: its security is the record layer keyed from the pairing, defined in
[L2CAP transport](#l2cap-transport).

Each side owns one long-lived, self-signed X.509 v3 certificate over an EC P-256 key, signed with
ECDSA-SHA256. Its subject, issuer, serial number, validity and extensions carry no meaning. A side
keeps its certificate while it is paired; replacing it then breaks the pairing, which has to be
repeated. The phone deletes its certificate when it unpairs and creates a new one for the next
pairing.

Peer authentication is exclusively an exact comparison of the peer's fingerprint — SHA-256 over the
DER encoding of the leaf certificate the peer presented in the handshake — with the expected value.
Chain building, expiry, hostname, key usage and every other X.509 property are ignored. Only the
leaf certificate counts.

| Side | Connection | Expected peer fingerprint | On mismatch |
|---|---|---|---|
| Phone | Pairing | Any certificate; its fingerprint is recorded | — |
| Phone | Session | The pinned Mac fingerprint | Close before sending anything |
| Mac | Outside pairing mode | The pinned phone fingerprint | Fail the handshake |
| Mac | In pairing mode | Any certificate; its fingerprint is recorded | — |

On a pairing connection neither side pins: each records the leaf certificate the other presented,
and the ceremony binds both fingerprints into the code (see [Pairing ceremony](#pairing-ceremony)).
Pinning both ways starts when the pairing commits. While in pairing mode the Mac accepts any client
certificate, but a connection whose first message after `hello` is not `pair-request` is a session
connection and still requires the pinned fingerprint; the Mac closes it on mismatch.

The client sends no server name indication. Neither side uses session resumption or 0-RTT data:
every connection performs a full handshake, so both certificates are observed on every connection.
TLS 1.3 encrypts the certificates, and the first application data on a connection is `hello`, so no
identity is visible on the wire.

## Connections

A connection is opened by the phone and authenticated — by TLS, or on the L2CAP carrier by its
[record layer](#records) — and then each side immediately sends `hello` without waiting for the
peer's. The first frame each side receives MUST be `hello`; anything else is malformed. If the
versions differ, the receiving side follows [Versioning](#versioning).

The first message the phone sends after `hello` fixes the kind of the connection for its whole life:

- `pair-request` makes it a **pairing connection**. It carries only `hello`, the `pair-*` messages
  and `bye`, and ends when the ceremony commits or aborts.
- Anything else makes it a **session connection**. The phone sends its `active-set` unprompted as
  soon as it can read its notification shade, normally right after the `hello` exchange: that is the
  reconnect state-sync, in which the phone's notification shade is the source of truth and the Mac
  diffs its mirror against it. Other session messages may precede the first `active-set`, and a
  short-lived connection the phone opens only to deliver a message — the Send to Mac push, or a
  `signal` carrying a `lock` — may carry none at all: the Mac's mirror then stays as it is until an
  `active-set` arrives. Outside pairing mode the Mac does not wait for any of that: it treats the
  session as established once the pinned handshake and the `hello` exchange have succeeded. In
  pairing mode it learns the kind from the first message.

Either side ends a connection by sending `bye` and closing; a connection that drops without `bye`
means the same. Nothing is queued across connections: a reconnect starts again with `hello` and
`active-set`, and anything missed in between is caught up by that state-sync. The exceptions are the
Mac's: a `reply-text` waits for the next session (see [`reply-text`](#reply-text)), a dismissal
stays pending until an `active-set` settles it (see [Loop prevention](#loop-prevention)), and a
clip too large for a BLE frame waits as well (see [Frame types](#frame-types)).

There are no keep-alive messages. Both sides SHOULD turn on TCP keepalive on a TCP session
connection, with the first probe after about 30 seconds without traffic, so that a connection whose
peer went away without closing it — a Mac that went to sleep, a phone that roamed to another
network — ends within about a minute instead of swallowing what is sent on it. Both implementations
probe after 30 idle seconds, every 10 seconds, and give up after 3 unanswered probes. An L2CAP
channel needs none: it ends with the Bluetooth link under it.

| Message | Direction | Connection |
|---|---|---|
| `hello` | both | both |
| `bye` | both | both |
| `pair-request` | phone → Mac | pairing |
| `pair-response` | Mac → phone | pairing |
| `pair-reveal` | phone → Mac | pairing |
| `pair-confirm` | both | pairing |
| `pair-abort` | both | pairing |
| `signal` | both | session |
| `sync-request` | Mac → phone | session |
| `active-set` | phone → Mac | session |
| `notif-detail` | phone → Mac | session |
| `icon-get` | Mac → phone | session |
| `icon-data` | phone → Mac | session |
| `clipboard-set` | both | session |
| `reply-text` | Mac → phone | session |

A known message received in a direction or on a connection kind the table does not allow is a
protocol error: the receiver closes the connection (on a pairing connection after sending
`pair-abort` with the reason `protocol-error`).

## TCP framing

A connection carries a stream of frames in both directions:

```
frame   = length ‖ body [‖ sidecar]
length  = u32be, the size of body in bytes, 1 ≤ length ≤ 1_048_576
body    = a UTF-8 JSON object with a string member "type"
sidecar = exactly binaryBytes raw bytes, present only when body declares "binaryBytes"
```

The `type` member names the message; every other member is defined per message below. A body MAY
declare `"binaryBytes": n`, an integer with 1 ≤ n ≤ 1_048_576: then exactly n raw bytes follow the
body and precede the next frame. The sidecar is not counted in `length`. In version 1 only
`icon-data` uses a sidecar, but a reader consumes the sidecar of every frame that declares one — of
an unknown type and of a known type that defines none, in which case the bytes are discarded — so
the stream stays in step.

Encoders SHOULD write compact JSON. Member order and whitespace are free, and receivers MUST NOT
depend on them. An optional member that is absent and one that is `null` mean the same; senders omit
them. This includes `binaryBytes`: a `null` there declares no sidecar. Base64 and hex members follow
the [conventions](#conventions).

### Malformed and unknown input

- Unknown members are ignored.
- After `hello`, a frame with an unknown `type` is ignored (its sidecar skipped) and logged. This is
  what lets a newer peer add message types without a version bump. Before it, an unknown `type` is
  malformed like every first frame that is not `hello` (see [Connections](#connections)).
- A malformed frame closes the connection without a reply. A frame is malformed when its length is 0
  or over the limit, the stream ends inside it, the body is not valid UTF-8 or not a JSON object,
  `type` is missing or not a string, `binaryBytes` is present, not `null` and not an integer in
  range, or a known message lacks a required member or carries a member of the wrong JSON type
  (including `null` for a required member). Types are strict: `"1"` is not an int, `"true"` is not
  a boolean and `1` is not a string.

## TCP messages

Each message is listed with its members besides `type`.

### `hello`

The first frame each side sends on every connection, right after the TLS handshake, or on the
L2CAP carrier right after the key exchange.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `v` | int | yes | The sender's `bridgeProtocolVersion` |
| `clipboardFromMac` | boolean | no | Phone → Mac on a session connection: whether the phone takes automatic clips from the Mac; absent means `true` |

The phone's switches tell the Mac what it may leave undone. With `clipboardFromMac` set to `false`
the Mac neither watches its pasteboard nor sends `clipboard-small`, `clipboard-pending` or an
automatic `clipboard-set`. The Mac reads the switch from every session connection's `hello` — a Send
to Mac connection's included — and keeps the last value between sessions, so the GATT path follows
it too; a switch the phone changes takes effect from its next session connection. Until a pairing's
first session the Mac counts the switch as `false`, and a new pairing or an unpairing forgets it.
The Mac sends no switches, and a pairing connection's `hello` needs none.

```json
{"type":"hello","v":1}
```

```json
{"type":"hello","v":1,"clipboardFromMac":false}
```

### `bye`

A graceful close: the sender closes the connection after writing it, and the receiver closes its
side on reading it.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `reason` | string | no | Why the connection ends; informational except for the reasons defined below |

Version 1 defines two reasons:

- `version-mismatch`: the peer's `hello` carried a different version (see
  [Versioning](#versioning)).
- `replaced`: a newer session connection from the same peer supersedes this one. The receiver MUST
  NOT reconnect automatically to restore the superseded link: it connects again only when something
  asks for a connection anew, such as a `sync-request` frame over GATT, never on a timer.

Other values MAY be sent; a receiver only logs them, and they never change its behaviour beyond
ending the connection.

```json
{"type":"bye","reason":"version-mismatch"}
```

### `pair-request`

Phone → Mac, the first message after `hello` on a pairing connection.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `commit` | string | yes | The phone's commitment to its fresh ephemeral X25519 public key, 64 lowercase hex digits (see [SAS derivation](#sas-derivation)) |

The phone sends its commitment before it learns the Mac's key, and its key only after. A `commit`
that is not 64 lowercase hex digits is a protocol error.

```json
{"type":"pair-request","commit":"1c2112fb9a375d8ecbe70b69acfbbc2ace06e2f5872832aa2c6261e00ca8bae5"}
```

### `pair-response`

Mac → phone, the answer to `pair-request`.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `pk` | string | yes | Base64 of the Mac's ephemeral X25519 public key, 32 bytes, generated for this ceremony when the `pair-request` arrived |

```json
{"type":"pair-response","pk":"hSDwCYkwp1R0i33ctD73Wg2/Og0mOBr066SpjqqbTmo="}
```

### `pair-reveal`

Phone → Mac, the answer to `pair-response`: the key the phone committed to.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `pk` | string | yes | Base64 of the phone's ephemeral X25519 public key, 32 bytes |

The Mac recomputes the commitment from `pk` and compares it in constant time with the `commit` of
the `pair-request`. A `pk` that is not base64 of 32 bytes, or that does not match the commitment, is
a protocol error: the Mac sends `pair-abort` with `protocol-error` and derives nothing.

```json
{"type":"pair-reveal","pk":"3p7bfXt9wbTTW2HC7OQ1Nz+DQ8hbeGdNrfx+FG+IK08="}
```

### `pair-confirm`

Either direction, once from each side: the sender proves that it holds the pairing's shared secret.
The Mac sends it once its user has typed in the code the phone shows and the Mac has accepted it.
The phone sends it right after `pair-reveal`, as key confirmation: the code is compared on the Mac,
so the phone has no step of its own to wait for.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `proof` | string | yes | The sender's key-confirmation proof, 64 lowercase hex digits (see [SAS derivation](#sas-derivation)) |

The receiver recomputes the proof it expects from the peer and compares it in constant time
**before** it treats the confirmation as received. A `proof` that is missing, malformed or different
is a protocol error: the receiver sends `pair-abort` with `protocol-error` and aborts, even if it
has already sent its own `pair-confirm`.

```json
{"type":"pair-confirm","proof":"0efa409a591773f940352d63970c93c2917c55f48895020f3c0ebd4e464f13b1"}
```

### `pair-abort`

Either direction: the ceremony ends without a pairing. Both sides wipe the ephemeral state and
close.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `reason` | string | yes | `sas-mismatch`, `cancelled`, `timeout`, `protocol-error` or `busy` |

`sas-mismatch` means a user said the codes differ, `cancelled` that a user left the ceremony,
`timeout` that a [timeout](#timeouts) expired, `protocol-error` that a message was invalid or out of
order, and `busy` — sent by the Mac only — that the Mac is already pairing over another connection.
An unknown reason is treated as `cancelled`. `sas-mismatch` remains a valid reason that a receiver
accepts, but no current implementation sends it: the Mac lets its user type a wrong code again, and
the phone has no step at which its user judges the code.

```json
{"type":"pair-abort","reason":"sas-mismatch"}
```

### `signal`

Either direction on a session connection: one complete [BLE frame](#ble-frames), carried over TCP.
The receiver processes it exactly as a frame received over GATT — the same key, the same
per-direction sequence numbers and the same replay window. This is the DevPresence transport for the
emulator.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `frame` | string | yes | Base64 of the whole frame: type, seq and ciphertext |

```json
{"type":"signal","frame":"AQAAAABlr37dEwYCZtX/IZjX87brvV6tepbYXze0IABOowhurpRJaC7xPVMp"}
```

### `sync-request`

Mac → phone: send your `active-set` now. No members. The Mac sends it whenever its mirror may have
drifted — when a `notif-posted` doorbell rings and when its display wakes from sleep — and the
phone answers with `active-set` on the same connection. The Mac keeps at most one `sync-request`
outstanding on a session: one more asked for while one is outstanding folds into exactly one, sent
when the awaited `active-set` arrives, so a burst of doorbells and wakes cannot storm the phone.
Without a session it sends the [`sync-request` frame](#frame-types) over GATT instead, which makes
the phone connect. That summon MAY wait: while the Mac's display sleeps nobody sees the mirror, so
the Mac holds every summon back and lets the display's wake ask once for all of them, and it MAY
leave out the summon that answers a confirmation (see [GATT transport](#gatt-transport)). Signals
that arrive meanwhile are still applied.

```json
{"type":"sync-request"}
```

### `active-set`

Phone → Mac: the phone's complete current set of mirrored notifications, after the phone's filters.
The phone sends it unprompted after `hello` on every session connection and in answer to every
`sync-request`. A set that would not fit one frame (see [TCP framing](#tcp-framing)) leaves out the
oldest notifications by `when` until it fits; the Mac reads them as removed, like any key missing
from the set.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `notifications` | array of notification objects | yes | Every notification the phone currently mirrors; may be empty |

A notification object has the members of [`notif-detail`](#notif-detail) without `type`; a receiver
ignores a `type` member in it. The Mac diffs the set against its mirror: a key it mirrors that is
missing from the set is removed, a key it does not mirror is added, and a key in both is updated in
place.

```json
{"type":"active-set","notifications":[]}
```

### `notif-detail`

Phone → Mac: the full content of one notification that was posted or updated, and the shape of
every element of `active-set`.

Once a session connection's first `active-set` has gone out, the phone sends a `notif-detail` for
every post or update that passes its filters instead of ringing the [`notif-posted`](#frame-types)
doorbell, which would only make the Mac ask for the whole set again. Removals, and a post that no
longer passes the filters, still go as `notif-removed` signals. Before that first set a post sends
nothing, since the set covers it; without a session it rings `notif-posted`, and so does a post
whose content the phone cannot read. The Mac applies a `notif-detail` like the same element of an
`active-set` — added when it does not mirror the key, updated in place when it does — but a key
missing from it means nothing, and the next `active-set` still decides the whole mirror.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `key` | string | yes | The notification's key (`StatusBarNotification.getKey()`), opaque to the Mac |
| `pkg` | string | yes | The posting app's package name |
| `appLabel` | string | yes | The posting app's label as the phone shows it |
| `title` | string | yes | The notification title; may be empty |
| `text` | string | yes | The notification text; may be empty |
| `when` | long | yes | When the notification was posted, an instant in milliseconds |
| `threadId` | string | no | The conversation or group the Mac threads it under |
| `canReply` | boolean | yes | The notification offers an inline reply |
| `isClearable` | boolean | yes | The user may dismiss the notification |
| `iconHash` | string | no | Lowercase hex SHA-256 of the app icon's PNG bytes, as `icon-data` delivers them |
| `r` | long | no | The notification's revision: which post of the key this is |

```json
{"type":"notif-detail","key":"0|com.example.chat|42|null|10123","pkg":"com.example.chat","appLabel":"Example Chat","title":"Alice","text":"Are you coming?","when":1791820800000,"threadId":"com.example.chat/alice","canReply":true,"isClearable":true,"iconHash":"c2d4b446a44ce54fab8e01150e24dd24f3d850c7c14dcfe31f6321341dd86874","r":1791820800000}
```

A key reposted with new content is an update of the same notification, never a second one. Its `r`
SHOULD change with every post of the key, so that two versions of one notification can be told
apart; the value is opaque to the Mac, which only compares it for equality and sends it back in a
[`dismiss`](#frame-types). The launcher sends the post time of the version it reads
(`StatusBarNotification.getPostTime()`, an instant in milliseconds). A sender that tracks no
revision omits `r`, and the Mac then tells versions apart by their content alone.

### `icon-get`

Mac → phone: send the icon with this hash.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `hash` | string | yes | An `iconHash` the phone sent earlier, 64 hex digits |

A phone that no longer holds the icon does not answer, and the Mac shows the notification without
it.

```json
{"type":"icon-get","hash":"4c4b6a3be1314ab86138bef4314dde022e600960d8689a2c8f8631802d20dab6"}
```

### `icon-data`

Phone → Mac: an icon, as PNG bytes in the sidecar.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `hash` | string | yes | Lowercase hex SHA-256 of the sidecar bytes |
| `binaryBytes` | int | yes | The size of the PNG sidecar, at least 1 |

The Mac verifies that the sidecar hashes to `hash` and discards the icon otherwise; icons are cached
by hash, so each is fetched once.

```json
{"type":"icon-data","hash":"4c4b6a3be1314ab86138bef4314dde022e600960d8689a2c8f8631802d20dab6","binaryBytes":8}
```

### `clipboard-set`

Either direction: put this text on your clipboard.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `text` | string | yes | The clipboard text |
| `sensitive` | boolean | yes | The source marked the clip as sensitive; the receiver marks it the same way and never shows it in a preview |
| `announce` | boolean | no | The user sent this text on purpose rather than an automatic sync copying it; absent means `false` |
| `ageMs` | long | no | Milliseconds since the sender last observed this copy on its clipboard, at least 0; absent means the copy is fresh |

A clip that an automatic sync picks up from the sender's clipboard goes out without `announce`. A
text the user pushes explicitly — the phone's Send to Mac action — carries `"announce": true`, and
the receiver MAY show a short notice that the text has arrived. Either way the receiver puts the
text on its clipboard, subject only to the delivery rule below; `announce` never changes that. A
receiver that shows no notices ignores the member, and the phone shows none.

A clip sent as soon as the sender saw it omits `ageMs`. A clip that waited for a connection — such
as the Mac's clip that did not fit a BLE frame, sent once a session is up (see
[`clipboard-pending`](#frame-types)) — carries the time since the sender saw the copy, so the
receiver can judge its whole age. The Mac's `clipboard-small` frame carries the same age in its `a`
member, so a clip that waited and fits a frame may go that way too. A receiver reads a negative
`ageMs` as 0. A sender that pushes a text on its user's explicit action never sends `ageMs`.

Delivery rule: a receiver MAY defer applying a clip until its user can receive it — the phone waits
for the user to unlock it — and keeps only the newest deferred clip. A receiver MAY drop a clip
whose age, `ageMs` plus the time it waited on the receiver, exceeds a limit the receiver's user
configured; the phone's limit is between 1 and 60 minutes. A receiver MAY also ignore every
automatic clip while its user has turned them off. None of this is visible to the sender, with one
exception: the phone tells the Mac in the `clipboardFromMac` switch of its [`hello`](#hello) that it
takes no automatic clips, so the Mac stops sending them from the next session on, and the phone
ignores those still on their way.

A sender never sends a clip of more than 102,400 UTF-8 bytes (100 KB); it skips larger clips. A
receiver MAY likewise drop an oversized `clipboard-set` without applying it.

```json
{"type":"clipboard-set","text":"https://example.com/a?b=c","sensitive":false}
```

```json
{"type":"clipboard-set","text":"Meeting moved to 3 pm","sensitive":false,"announce":true}
```

```json
{"type":"clipboard-set","text":"Notes from the call","sensitive":false,"ageMs":95000}
```

### `reply-text`

Mac → phone: the text of a reply that did not fit a BLE frame. The Mac sends a `reply` frame with
the `tcp` flag first and then this message on the current session connection when one is open,
otherwise on the next one, provided that one starts before the reply's answer is overdue (see
[Frame types](#frame-types)); a text that waited longer is dropped. A phone that receives a `reply`
with the `tcp` flag and has no session connection opens one, as it does for a `sync-request` frame.
The phone acts on this message alone, as it carries the full key and the text; the `reply` frame
with the `tcp` flag only announces it. The phone answers it like an inline reply: with a
[`reply-ok`](#frame-types) signal once the text is in the notification's reply action, or with a
[`reply-error`](#frame-types) signal when it could not be.

| Member | JSON type | Required | Meaning |
|---|---|---|---|
| `key` | string | yes | The full notification key, as in `notif-detail` |
| `text` | string | yes | The reply text |

```json
{"type":"reply-text","key":"0|com.example.chat|42|null|10123","text":"A reply too long for one BLE frame."}
```

## Pairing ceremony

Pairing binds the two long-lived certificates to each other through an ephemeral X25519 exchange
that the users verify with a six-digit code: the phone shows it, and the Mac's user types it into
the Mac. It produces the pins and `K_ble`.

1. The user puts the Mac into pairing mode. The Mac accepts any client certificate, adds `pair=1`
   to its TXT record and waits for a phone for 120 seconds.
2. The phone finds the Mac (see [Discovery](#discovery)) and opens a TLS connection to it, accepting
   any server certificate and recording its fingerprint. It sends `hello`, generates a fresh
   ephemeral X25519 key pair and sends `pair-request` with the commitment to its public key.
3. The `pair-request` starts the ceremony and takes pairing mode: the Mac answers any other one with
   `busy`. Only now does the Mac generate its own fresh ephemeral X25519 key pair, and it sends the
   public key in `pair-response`.
4. The phone checks the Mac's key and sends `pair-reveal` with the public key it committed to. The
   Mac checks the key against the commitment.
5. Both sides now hold both leaf-certificate fingerprints **as observed in the TLS handshake** —
   never taken from a message body — and both ephemeral public keys. Both compute `ss`, the
   transcript hash, `K_ble`, the SAS and the confirmation key as described in
   [SAS derivation](#sas-derivation). The phone displays the SAS, and the Mac asks its user to type
   it in.
6. Right after `pair-reveal` the phone sends `pair-confirm` with its proof. The Mac compares the
   typed code with its own SAS in constant time. A wrong code is refused and may be typed again
   until the confirmation timer expires; the right one is the Mac's confirmation, and the Mac then
   sends `pair-confirm` with its proof.
7. A side verifies the proof in the peer's `pair-confirm` as soon as it arrives. The Mac **commits**
   once it has both its user's accepted code and the phone's verified `pair-confirm`, whichever
   comes last; the phone commits as soon as the Mac's `pair-confirm` verifies. Committing persists
   the peer's fingerprint as the pin, `K_ble` and fresh sequence-number state (see
   [Sequence numbers](#sequence-numbers)) — the phone also the Mac's instance name, host and port —
   and wipes `ss`, the confirmation key and both ephemeral private keys. The phone then sends `bye`
   and closes; the pairing connection never turns into a session.

The code is what authenticates the exchange to the Mac: its user types the code the phone shows and
the Mac confirms only on a constant-time match. A device in the middle of the network has to run one
exchange with each side and present its own certificate or ephemeral key to at least one of them,
which changes that side's transcript and therefore its code. The order of the messages keeps it from
steering the two codes together: in the exchange where it poses as the Mac it has to send its key
before the phone reveals its own, and in the exchange where it poses as the phone it has committed
to its key before the Mac's fresh key arrives. Whatever keys and certificates it picks, the two
codes agree with a probability of one in 10⁶, and every try costs a whole ceremony, which ends
pairing mode until the Mac's user starts it again. Because the Mac's user types in the code rather
than comparing it, a device that raced the phone to `pair-request` is never confirmed out of habit:
its code is not the one on the user's phone, and the phone itself is answered `busy`.

The proofs are key confirmation: each shows its receiver that the sender derived the same `ss`. The
Mac sends its proof only after its user has typed in the right code, and the phone completes once
that proof verifies; the phone sends its proof without waiting, since it has nothing to compare. A
side whose peer derived a different `ss` never commits and aborts with `protocol-error` on the
peer's `pair-confirm`.

### State machine

```
Mac
  IDLE        --user starts pairing------------------------------------> WAITING      (pair=1 in the TXT record)
  WAITING     --hello exchange, then pair-request------------------------> REVEALING    (sends pair-response)
  REVEALING   --pair-reveal whose key matches the commitment------------> CONFIRMING   (asks for the code)
  CONFIRMING  --typed code differs from the SAS--------------------------> CONFIRMING   (asks again)
  CONFIRMING  --typed code equals the SAS---------------------------------> CONFIRMING   (sends pair-confirm with the own proof)
  CONFIRMING  --own confirmation and the phone's verified pair-confirm--> COMMITTED

Phone
  IDLE        --Mac found, or an endpoint entered in DevPresence---------> CONNECTING
  CONNECTING  --handshake (fingerprint recorded), hello, pair-request---> REQUESTED
  REQUESTED   --pair-response with a usable key, sends pair-reveal------> CONFIRMING   (shows the SAS, sends pair-confirm with the own proof)
  CONFIRMING  --the Mac's verified pair-confirm--------------------------> COMMITTED    (sends bye, closes)

Both
  CONFIRMING  --pair-confirm whose proof does not verify--> ABORTED (sends pair-abort protocol-error)
  any state before COMMITTED --pair-abort sent or received, timeout, connection lost--> ABORTED
  ABORTED: wipe ss, the confirmation key and the ephemeral keys, close; the Mac leaves pairing mode
```

The Mac serves one pairing at a time. From the first `pair-request` until the ceremony ends it still
accepts any client certificate and keeps `pair=1`, but every other connection that could still
become a pairing connection is closed — after a `pair-abort` with `busy` once its TLS handshake is
complete — and a `pair-request` on a connection accepted later gets `pair-abort` with `busy` before
the Mac closes it. Pairing mode ends with the ceremony, whether it commits or aborts: an abort or a
dropped connection after a `pair-request` ends it until the Mac's user starts it again. Every
ceremony uses fresh ephemeral keys on both sides, destroyed when it ends and never reused.

A side sends `pair-abort` with `protocol-error` and aborts when a `commit` is not 64 lowercase hex
digits, when a `pk` is not base64 of exactly 32 bytes, when the revealed key does not match the
commitment, when X25519 yields the all-zero shared secret, when the proof in a `pair-confirm` does
not verify, or when a message arrives out of order. The phone checks the Mac's key before it reveals
its own. A `pair-abort` or a dropped connection aborts the ceremony on the receiving side as well.
Messages that arrive after a side has committed are ignored.

### Timeouts

| Timer | Length | Starts | Expires into |
|---|---|---|---|
| Pairing mode | 120 s | When the Mac's listener is ready in pairing mode | The Mac leaves pairing mode and drops `pair=1`. An unpinned phone then cannot connect or fails the TLS handshake; a pinned phone's connection becomes a session after `hello`, and a late `pair-request` on it is a protocol error that closes the connection |
| Step | 15 s | When a side starts waiting for the peer's `hello`, for `pair-request` after the `hello` exchange, for `pair-response` after `pair-request`, or for `pair-reveal` after `pair-response` | `pair-abort` with `timeout` |
| Confirmation | 120 s | When a side has derived the SAS: the phone displays it, the Mac asks for it | `pair-abort` with `timeout` unless the side has committed |

### Partial commits, unpairing and re-pairing

If the connection drops after one side has committed but before the other has received its
`pair-confirm`, the pairing is half-done: the committed side holds pins the other side does not
have. This needs no repair step, because a peer that fails the pinned handshake is treated as
unpaired: the phone shows the pairing as lost and offers to pair again.

Unpairing is local. A side wipes the pin, `K_ble` and its sequence-number state; the phone also
deletes its TLS identity and calls `disassociate()` for its companion-device association. Turning
the bridge off on the phone unpairs it the same way. Nothing is sent to the peer, which finds out on
its next connection attempt. Re-pairing runs the full ceremony and replaces the pins,
`K_ble` and the sequence-number state.

`K_ble` MUST be kept in platform-protected storage: sealed with an Android Keystore key on the
phone, in the Keychain on the Mac. The pin, the phone's saved endpoint and the sequence-number state
live in app-private storage and MAY be protected the same way. A side MUST be able to tell its own
sequence-number state from a missing, stale or foreign one, so the state records which `K_ble` it
counts for. Committing writes the fresh state before the new key, so a commit cut short leaves a key
without state of its own — unusable — and never a key next to counters back at 0.

## SAS derivation

```
lp(x)   = u16be(len(x)) ‖ x
commit  = SHA-256(lp(UTF-8 "app.cue.bridge/commit/v1") ‖ lp(pkPhone))
label   = UTF-8 "app.cue.bridge/pair/v1"
th      = SHA-256(lp(label) ‖ lp(fpMac) ‖ lp(fpPhone) ‖ lp(pkMac) ‖ lp(pkPhone))
ss      = X25519(own ephemeral private key, peer ephemeral public key)
K_ble   = HKDF-SHA256(IKM = ss, salt = th, info = UTF-8 "app.cue.bridge/kble/v1", L = 32)
sasOkm  = HKDF-SHA256(IKM = ss, salt = th, info = UTF-8 "app.cue.bridge/sas/v1", L = 4)
sas     = u32be(sasOkm) mod 1_000_000, written as 6 decimal digits with leading zeros
confirmKey = HKDF-SHA256(IKM = ss, salt = th, info = UTF-8 "app.cue.bridge/confirm/v1", L = 32)
proofPhone = HMAC-SHA256(key = confirmKey, data = UTF-8 "phone")
proofMac   = HMAC-SHA256(key = confirmKey, data = UTF-8 "mac")
```

- `fpMac` and `fpPhone` are the 32 raw bytes of the fingerprints observed in the TLS handshake of
  the pairing connection; `pkMac` and `pkPhone` are the 32 raw bytes of the ephemeral public keys
  (RFC 7748 encoding). The order is always Mac first, whichever side computes it.
- The transcript is therefore 160 bytes: 2 + 22 bytes of label, then four times 2 + 32 bytes. The
  input of the commitment is 60 bytes: 2 + 24 bytes of label, then 2 + 32 bytes of `pkPhone`; it
  travels in `pair-request` as 64 lowercase hex digits, and the Mac compares it in constant time.
- `ss` is 32 bytes and is the same on both sides; a side that gets the all-zero value aborts.
- The transcript ties the SAS to both certificates and both ephemeral keys: an attacker in the
  middle has to present a different certificate or key to at least one side, which changes that
  side's transcript and therefore its code.
- The SAS MAY be displayed in two groups of three digits; the Mac's user types the six digits.
- The phone sends `proofPhone` and the Mac sends `proofMac` in `pair-confirm`, each as lowercase
  hex. The two inputs differ, so a proof reflected back to its sender never verifies. A receiver
  compares in constant time, and the confirmation key is wiped with `ss`.

The labels `app.cue.bridge/commit/v1`, `app.cue.bridge/pair/v1`, `app.cue.bridge/kble/v1`,
`app.cue.bridge/sas/v1` and `app.cue.bridge/confirm/v1` and the proof inputs `phone` and `mac` are
part of the protocol and change only with a version bump.

## BLE frames

Signals are tiny encrypted frames. They travel over GATT, and also inside [`signal`](#signal)
messages, with identical semantics.

```
frame      = type ‖ seq ‖ ciphertext
type       = u8, the frame type
seq        = u32be, the sender's sequence number in this direction
ciphertext = ChaCha20-Poly1305(key = K_ble, nonce, aad = type, plaintext) — the plaintext plus a 16-byte tag
nonce      = direction ‖ 00 00 00 00 ‖ seq          (12 bytes)
direction  = 00 00 00 01 for phone → Mac, 00 00 00 02 for Mac → phone
```

A frame is at most 180 bytes, so a plaintext is at most 180 − 5 − 16 = 159 bytes. The direction in
the nonce means that a frame can never be accepted by its own sender, and the type byte is
authenticated as additional data, so it cannot be changed in flight. The GATT carrier is specified
in [GATT transport](#gatt-transport); the frame format above is final.

### Sequence numbers

- Each direction has its own sequence: after a pairing commits, the first frame in each direction
  has `seq` 0.
- A sender MUST NOT use a `seq` twice under one `K_ble` — not across restarts and not between GATT
  and `signal`, which share one sequence per direction — so every frame it seals gets a `seq` above
  all it has used before. Before a frame leaves, a bound above its `seq` MUST be persisted, so that
  a sender that restarts continues above everything it may have sent.
- A sender MAY reserve ahead: persist a bound some numbers above the next `seq`, hand out the
  numbers below it from memory and persist a new bound only when they run out. Both implementations
  reserve blocks of 64. A restart skips whatever was reserved and not used.
- Gaps are expected, from restarts and from frames that never arrived: a receiver MUST NOT require
  consecutive numbers. A missed signal is harmless, because state-sync catches up.
- A receiver keeps a [replay window](#replay-window) per pairing and persists it with every frame it
  accepts.
- A side whose `K_ble` is stored but whose sequence-number state is missing, unreadable or not that
  key's MUST NOT seal or accept frames under it and MUST NOT start the sequence again from 0: the
  pairing is unusable until it is repeated, and committing a pairing writes fresh state for the new
  `K_ble` (see [re-pairing](#partial-commits-unpairing-and-re-pairing)).
- The last usable `seq` is 4294967295. A sender that has used it sends no more frames and the
  pairing has to be repeated.

### Replay window

A receiver's state per pairing is the highest accepted `seq`, `H` — none before the first accepted
frame — and a 64-bit map in which bit i is set when `H − i` has been accepted; bit 0 stands for `H`
itself. A frame's `seq` `s` passes the window when

- no frame has been accepted yet, or
- `s > H`, or
- `H − s < 64` and bit `H − s` is clear.

Any other `seq` — one 64 or more below `H`, or one whose bit is set — is a replay. When a frame that
passed is accepted, its `seq` is recorded: for `s > H` the map shifts left by `s − H` bits, and is
cleared when `s − H ≥ 64`, then bit 0 is set and `H` becomes `s`; otherwise bit `H − s` is set.

The window lets each `seq` in at most once and still tolerates reordering: the two carriers can
deliver frames out of order, and a frame up to 63 numbers older than the newest accepted one is
still taken. One that falls further behind is dropped, which state-sync makes up for.

### Receiving

A receiver handles a frame in this order and drops it silently at the first failure; a dropped frame
changes no state:

1. The size is between 21 and 180 bytes.
2. `seq` passes the [replay window](#replay-window).
3. The AEAD opens with the nonce of the receiving direction and the type byte as additional data.

A frame that passes is accepted and its `seq` is recorded in the window. Then the type is looked at:
an unknown type, a type sent in a direction the table below does not allow, or a plaintext that does
not match its type is ignored — but the `seq` stays recorded.

### Frame types

| Code | Type | Direction | Plaintext | Meaning |
|---|---|---|---|---|
| 1 | `notif-posted` | phone → Mac | `{"k":"<key hash>"}` | A mirrored notification was posted or updated; the Mac fetches its content over TCP. A session whose first `active-set` is out sends `notif-detail` instead |
| 2 | `notif-removed` | phone → Mac | `{"k":"<key hash>"}` | A mirrored notification left the phone's shade, for any reason |
| 3 | `dismiss` | Mac → phone | `{"k":"<key hash>"}` or `{"k":"<key hash>","r":<revision>}` | The user dismissed the mirror on the Mac, or clicked it and the click opened something there (an app or a link); the phone cancels the notification if it still shows that revision |
| 4 | `reply` | Mac → phone | `{"k":"<key hash>","t":"<text>"}` or `{"k":"<key hash>","tcp":true}` | The user replied on the Mac; with `tcp` the text follows as `reply-text` |
| 5 | `clipboard-small` | Mac → phone | `{"t":"<text>","s":<boolean>}` or `{"t":"<text>","s":<boolean>,"a":<age>}` | Put this text on the clipboard; `s` marks it sensitive, `a` is its age |
| 7 | `sync-request` | Mac → phone | `{}` | The phone connects if it is not connected and sends `active-set` |
| 8 | `reply-error` | phone → Mac | `{"k":"<key hash>"}` or `{"k":"<key hash>","r":"<reason>"}` | The phone could not deliver a reply into the notification; the Mac tells its user that the reply failed |
| 9 | `ping` | both | `{}` | Proves to the receiver that its GATT peer holds the pairing: from the phone, that the subscribed central is the paired phone; from the Mac, the answer to that confirmation (see [GATT transport](#gatt-transport)); carries nothing |
| 10 | `clipboard-pending` | Mac → phone | `{}` | The Mac holds a clip that did not fit a BLE frame and has no session to send it on; the phone connects when its user can receive the clip |
| 11 | `reply-ok` | phone → Mac | `{"k":"<key hash>"}` | The phone put a reply into the notification's reply action; the Mac stops waiting for its answer |
| 12 | `lock-state` | Mac → phone | `{"l":<boolean>}` | Whether the Mac's screen is locked, `true` when it is; sent when that changes, to a subscriber the Mac confirms, on every session connection and in answer to every `lock` |
| 13 | `lock` | phone → Mac | `{}` | The Mac locks its screen if it is unlocked and it can; it answers with a `lock-state` |

Code 6 was `dnd-state`, retired with the dropped DND sync; it stays unassigned, and a frame carrying
it is an unknown type.

- The key hash is the first 8 bytes of SHA-256 over the UTF-8 notification key, as 16 hex digits. It
  identifies a notification without revealing its key; the Mac maps it back through its mirror.
- Plaintexts are compact UTF-8 JSON objects. Member order is free and unknown members are ignored.
- The `r` of a `dismiss` is the [revision](#notif-detail) of the copy the user closed, as the
  phone's `active-set` or `notif-detail` gave it; the Mac sends it whenever it knows one. The phone
  cancels the notification only when the `dismiss` carries no `r` or its `r` equals the revision of
  the notification it shows under that key now. A `dismiss` for another revision was meant for a
  post the phone has since replaced: the phone ignores it and rings `notif-posted` for the key, so
  that the Mac's next `active-set` shows it the current revision. A `dismiss` for a key the phone no
  longer shows is ignored, so one sent twice is harmless. The Mac sends it again, at the revision
  then listed, while the phone's `active-set` keeps the key with what the user closed (see
  [Loop prevention](#loop-prevention)).
- A `reply` carries exactly one of `t` and `"tcp": true`. A sender uses the `tcp` flag whenever the
  inline form would exceed 159 bytes.
- The phone answers every reply it acts on — a `reply` with `t`, or a `reply-text` — with exactly
  one signal: `reply-ok` once the text is in the notification's reply action, `reply-error` when it
  could not put it there. The `k` of either is the key hash of that notification, for a
  `reply-text` the hash of its `key`. The Mac waits 30 seconds from sending a reply for its answer;
  a reply still unanswered then is reported to its user as not confirmed, since it may or may not
  have reached the notification, and a `reply-text` still waiting for a session then is dropped. An
  answer that comes later, or for a reply the Mac no longer waits for, is ignored.
- The optional `r` of a `reply-error` is a short reason string and informational only: a receiver
  MAY use it in a log or in the wording of its message and accepts any value. Version 1 defines
  `gone` (the notification is no longer active, or was never one the phone mirrored — the phone does
  not tell the two apart), `no-reply-action` (the notification offers no usable reply action) and
  `send-failed` (filling the reply or sending the action's intent failed).
- A `clipboard-small` text that does not fit is sent as `clipboard-set` over TCP instead. The
  optional `a` of a `clipboard-small` is the clip's age in milliseconds, with the meaning and the
  rules of the `ageMs` of [`clipboard-set`](#clipboard-set): absent for a clip sent as soon as the
  Mac saw it, present for one that waited for a way to the phone. The phone applies its freshness
  limit to it exactly as to a `clipboard-set`.
- A `clipboard-set` that has to wait for a session is announced with one `clipboard-pending`, sent
  over GATT to a confirmed subscriber only and at most once per waiting clip. The Mac sends the
  clip, with `ageMs`, on the next session connection, whoever opened it, and forgets a clip that
  waited longer than 60 minutes, the longest limit a phone can set. A phone that receives
  `clipboard-pending` opens a session at once if its user can receive the clip, otherwise when the
  user can, or not at all if the clip has outgrown the phone's limit by then or the phone ignores
  clips from the Mac. A newer copy on the Mac replaces the waiting one.
- A `lock-state` carries the state of the Mac's screen lock when it was sealed. The Mac sends one
  whenever that state changes, over GATT to every confirmed subscriber and as a `signal` on the
  session connection when one is up; right after it confirms a subscriber, beside the `sync-request`
  or `ping` that answers the confirmation; after the `hello` exchange on every session connection
  it accepts; and in answer to every `lock` it acts on.
- The phone keeps the state of the last `lock-state` it applied only while the Mac is present. Until
  the first one arrives the state is unknown, and the phone forgets it when the presence ends and
  when the pairing is wiped. The [replay window](#replay-window) lets frames in out of order, so a
  receiver MUST apply a `lock-state` only when its `seq` is above that of the last `lock-state` it
  applied under the same `K_ble`; an older one is accepted, so its seq is recorded, and ignored.
- A `lock` asks the Mac to lock its screen and is idempotent: a Mac whose screen is already locked,
  or that cannot lock it, changes nothing. The Mac answers every `lock` it acts on with a
  `lock-state` that gives the state after the attempt, even when it is unchanged, so the phone
  learns whether the Mac is locked now. The phone sends `lock` over GATT while it is subscribed,
  otherwise as a `signal` on the session connection, and when none is up it opens a short-lived one
  for it, as for the Send to Mac push. On a connection the Mac MUST answer every `lock` it accepted
  before it honours a `bye` received after it. The answer may lag the `lock` by a few hundred
  milliseconds while the Mac reads the state after the attempt, so a phone that sends `bye` right
  behind its `lock` reads on until the Mac closes the connection.

## GATT transport

The Mac exposes one GATT service while the bridge is enabled, in pairing mode and paired alike, and
advertises exactly its UUID: no local name and no other data. The phone neither scans nor advertises
— the operating system's companion-device presence watches the advertisement for it — and connects
as the central while the Mac is present.

| Element | UUID |
|---|---|
| Service | `e8f0e1fc-e5f4-40ba-a53f-87911835c3bb` |
| `doorbell` characteristic | `0978ea53-a36b-43a4-a38d-5a26bc34b549` |
| `command` characteristic | `eec765d8-f473-40be-a4b4-6998d3ef5602` |
| `psm` characteristic | `012d7eb8-5ac2-4600-b849-a9475da8a1d8` |

`doorbell` and `command` are always there; `psm` exists only while the Mac is paired and its
[L2CAP channel](#l2cap-transport) is published.

- `doorbell` has the single property *write* (with response), phone → Mac. One write carries exactly
  one complete [BLE frame](#ble-frames). The write response confirms ATT delivery, never acceptance:
  the Mac applies the [receiving rules](#receiving) and drops a failing frame silently.
- `command` has the single property *notify*, Mac → phone. One notification carries exactly one
  complete BLE frame. The phone subscribes after discovery. The Mac notifies each frame to its
  audience only, as the confirmation rules below define it.
- Frames are never fragmented. After connecting, the phone requests an ATT MTU of at least 185, so
  that a maximal frame fits one ATT payload (the payload of a write or notification is MTU − 3
  bytes). A side sends over GATT only a frame that fits the negotiated payload; a signal whose frame
  does not fit travels as a [`signal`](#signal) message over TCP instead. The Mac leaves a
  subscriber whose payload is below 182 bytes — an MTU below 185 — out of every audience, neither
  sizing frames for it nor notifying it, so a central with a smaller MTU cannot hold back the frames
  the phone receives.
- The held link carries a few small frames at a time, so both sides SHOULD ask for a slow connection
  interval while it only waits and a faster one while an [L2CAP channel](#l2cap-transport) on it
  carries a session. The launcher requests low-power connection priority once it is subscribed and
  balanced priority while a channel is open; the Mac asks for high latency per central, and for low
  latency while that central has a channel open. Either is a wish the Bluetooth stacks may refuse,
  and nothing in the protocol depends on it.
- The characteristics require no ATT security: no bonding and no link encryption. Authenticity and
  confidentiality rest solely on the AEAD under `K_ble`, and the per-direction
  [sequence numbers](#sequence-numbers) are shared between GATT and the `signal` message, so a frame
  is accepted at most once however it arrives. A write by a foreign central fails the AEAD and is
  dropped like any other invalid frame.
- A subscription is not an identity: any central can subscribe to `command`. A subscriber is
  **confirmed** as the phone only by something it wrote itself that the Mac accepts: a frame written
  to `doorbell` that passes the [receiving rules](#receiving) — the `ping` or any other, whatever
  its type — or a [record](#records) that opens on an L2CAP channel that same central opened. The
  confirmation lasts until that subscription ends. Nothing else confirms a subscriber: not a
  session connection, which says nothing about who is subscribed, and not a frame another central
  wrote. The Mac MUST NOT report the phone as nearby, nor base any behaviour beyond a summon on an
  unconfirmed subscription.
- The phone therefore writes a [`ping`](#frame-types) to `doorbell` after every subscription it
  makes, and again whenever a session connection starts while it is subscribed, so a `ping` that
  got lost is made up for by the next session at the latest.
- Every frame that carries something for the user or about the Mac — `dismiss`, `reply`,
  `clipboard-small`, `clipboard-pending` and `lock-state` — and the Mac's `ping` go over GATT to
  confirmed subscribers only; without one they take a session or wait, as their own rules say. Only
  a `sync-request` may go to a subscriber that is not confirmed, and only while none is confirmed
  and no session is up: it is sealed, so it reveals nothing, and it is the one way to summon a phone
  whose `ping` is still on its way. A frame queued for a set of subscribers goes to those of them
  still subscribed when it leaves, or nowhere.
- The Mac answers a confirmation at once over GATT, which is how the phone learns that its Bluetooth
  peer is the paired Mac: with a `sync-request` while no session is up, unless the confirming frame
  has already made it send one, and with a `ping` while a session is up, and either way with a
  `lock-state` (see [Frame types](#frame-types)). It answers with a `ping` instead of the
  `sync-request` while it holds summons back: while its display sleeps, and after an `active-set`
  that came in less than 3 minutes before with no display sleep since — a link that comes and goes
  at the edge of range would otherwise open a session at every reconnect to repeat a set the Mac
  already has, and what the phone posts meanwhile rings its own doorbell. The phone counts its GATT
  link as one to the Mac only after a frame from the Mac has been accepted over it, and only until
  that connection ends; a bare connection to the Mac's address proves no more than the advertisement
  does. The phone never answers the Mac's `ping`, and the Mac answers only a frame that confirmed a
  subscription, so the exchange cannot loop.
- `psm` has the single property *read*. Its value is exactly 2 bytes, the `u16be` PSM of the
  published L2CAP channel. It requires no ATT security either: the PSM is not a secret, and a
  foreign central that connects to the channel cannot complete its record layer.

## L2CAP transport

The L2CAP transport carries a session connection over Bluetooth LE when the phone cannot reach the
Mac over IP, for example on a Wi-Fi that isolates its clients. It is additive at version 1: a Mac
without it exposes no `psm` characteristic, and a phone without it never reads one, so either
interoperates with the other exactly as before.

While it is paired, the Mac publishes one LE credit-based L2CAP channel and exposes its PSM in the
[`psm` characteristic](#gatt-transport). The phone SHOULD prefer TCP and MAY fall back to the
channel when TCP is unreachable: it reads the PSM over its GATT connection and opens the channel as
the central. One L2CAP connection carries exactly one session connection, opened by the phone like a
TCP one. Pairing never runs here: pairing connections are TCP only, so an L2CAP connection is always
a session connection, and a `pair-request` on it is a protocol error.

The Mac MUST treat a session established here exactly like one established over TLS. Everything
[Connections](#connections) says about session connections applies unchanged — replace semantics,
no queuing, idle behaviour left to the implementation — and a new session connection replaces the
current one whichever carrier each of them uses.

### Handshake

The phone sends its fresh ephemeral X25519 public key, 32 raw bytes, as the first bytes on the
channel. The Mac answers with its own fresh ephemeral X25519 public key, 32 raw bytes. Nothing else
is exchanged in the clear. A side that waits more than 15 seconds for the peer's key, or after the
keys for the peer's first record, closes the channel. A side whose X25519 result is the all-zero
value closes the channel, as in pairing. Each side wipes its ephemeral private key and `ss` once the
record keys are derived.

### Key derivation

```
ss      = X25519(own ephemeral private key, peer ephemeral public key)
salt    = SHA-256(lp(K_ble) ‖ lp(pkPhone) ‖ lp(pkMac))
okm     = HKDF-SHA256(IKM = ss, salt = salt, info = UTF-8 "app.cue.bridge/l2cap/v1", L = 64)
K_pm    = okm[0..31]      the phone → Mac record key
K_mp    = okm[32..63]     the Mac → phone record key
```

- `lp` is the length prefix of the [SAS derivation](#sas-derivation). `pkPhone` and `pkMac` are the
  32 raw bytes of the ephemeral public keys exchanged in the handshake, and the order is always
  phone first, whichever side computes it. The salt's input is therefore 102 bytes: three times
  2 + 32.
- Mixing `K_ble` into the salt makes possession of the pairing the authentication: a man in the
  middle can swap the ephemeral keys, but without `K_ble` it can neither produce nor open a single
  record. Fresh ephemeral keys give the channel forward secrecy, as TLS 1.3 does on the TCP path.
- The label `app.cue.bridge/l2cap/v1` is part of the protocol and changes only with a version bump.

### Records

After the handshake the byte stream in each direction is a sequence of records:

```
record     = length ‖ ciphertext
length     = u16be, the size of ciphertext in bytes, 17 ≤ length ≤ 16_400
ciphertext = ChaCha20-Poly1305(key = the direction's key, nonce, aad = empty, plaintext) — the plaintext plus a 16-byte tag
nonce      = 00 00 00 00 ‖ u64be(counter)          (12 bytes)
```

A plaintext is 1 to 16_384 bytes. Each direction has its own counter: a side's first record has
counter 0, and the counter grows by one with every record it sends. The keys are new on every
connection, so a counter never repeats under a key and, unlike a BLE `seq`, is never persisted.

A record whose length is out of range, a record the AEAD does not open with the receiving
direction's key and the next counter, and a stream that ends inside a record are fatal: the receiver
closes the channel without a reply. A stream that ends between records ends the connection, like a
TCP connection that closes. The first record a side opens is what authenticates the peer; before it,
the side has received nothing but a public key.

### Payload

The concatenated plaintexts of one direction form exactly the byte stream of
[TCP framing](#tcp-framing): length-prefixed JSON frames with their optional sidecars, `hello` first
from each side, then session messages and `bye` to end, under the same limits and the same
[malformed-input rules](#malformed-and-unknown-input). A record boundary has no meaning: a frame may
span records and a record may carry several frames. The launcher seals each frame it sends as one
record and splits a frame of more than 16_384 bytes into full records and the rest.

## Loop prevention

Every action one side applies on behalf of the other produces a local event that would otherwise be
sent straight back. Each side therefore keeps short-lived tombstones — in memory, for at least 5
seconds — for what it has just applied on the peer's behalf, and never sends an event that matches a
live tombstone:

- **Dismissals.** The phone tombstones the key it cancels because of a `dismiss`, so the removal it
  then observes is not sent back as `notif-removed`. The Mac tombstones the mirror it removes
  because of `notif-removed` or a state-sync, so the removal is not sent back as `dismiss`.
- **Clipboard.** A side that sends its clipboard changes automatically tombstones the text it puts
  on its clipboard because of `clipboard-small` or `clipboard-set`, so the clipboard change it then
  observes is not sent back. The phone sends a clip only on its user's explicit action and needs
  none.

Tombstones are a local concept and never appear on the wire.

A `dismiss` can get lost on its way, or meet a newer post of its key on the phone, so the Mac keeps
every dismissal **pending** — whether it went out or found no session and no confirmed GATT
subscriber to go out on — with the revision and the content the user closed. Every `active-set`
decides each pending dismissal:

- A key listed at that revision, or at another revision with the same visible content (app label,
  title and text), still shows what the user closed: the Mac sends its `dismiss` again, at the
  listed revision, does not add the key back to its mirror and keeps it pending.
- A key listed with other content is new to the user: the dismissal ends and the key is added.
- A key not listed is settled: the dismissal ends.

A `notif-detail` of a pending key is decided the same way, except that it never settles one. The
phone ignores a `dismiss` for a key or a revision it no longer shows, so sending it again is
harmless, and a `dismiss` that got lost costs at most the time until the next `active-set`. The
Mac's implementation keeps pending dismissals in memory, at most 64 of them and each for a day.

## Logging

Both sides log protocol events at debug level with the direction, the message or frame type and the
key hash. Notification content, reply text and clipboard text are never logged above debug and are
redacted by default.

## Test vectors

The files in `fixtures/bridge/` are shared by both implementations. The launcher's contract tests in
`app/src/test/kotlin/app/cue/launcher/system/bridge/` read them from this repository, which holds
the canonical copy. The Bridge repository keeps an identical copy of the JSON files in its own
`fixtures/bridge/`, synced from here, and its tests fail when the two copies differ while both
repositories are checked out side by side. All byte values are lowercase hex.
`fixtures/bridge/README.md` records where each value comes from.

| File | Contents |
|---|---|
| `chacha20poly1305.json` | AEAD known-answer vectors from RFC 8439 §2.8.2 and Appendix A.5: key, nonce, AAD, plaintext, ciphertext and tag |
| `x25519.json` | RFC 7748 §5.2 scalar multiplications (one with the high bit of u set), the iterated vectors after 1 and 1,000 iterations, and the §6.1 Diffie-Hellman exchange |
| `hkdf.json` | RFC 5869 Appendix A.1 to A.3: IKM, salt, info, L, PRK and OKM |
| `pairing.json` | The commitment and derivation labels; two test certificates with their fingerprints; pairing cases with both ephemeral key pairs (private keys included, for testing only), both fingerprints and the expected commitment input and commitment, `ss`, transcript, transcript hash, `K_ble`, SAS output bytes, SAS, confirmation key and both proofs — one case swaps the roles, one has a SAS with a leading zero; low-order public keys whose exchange yields the all-zero secret, with the expected outcome `zero-shared-secret`; commitment checks of a revealed key against a commitment, with the expected outcome `valid`, `mismatch` or `invalid`; whole ceremonies as the messages each side sends, marked with the sides whose implementation runs them as the honest party, the expected outcome `committed`, `aborted` or `busy`, and for a commit the SAS the Mac's user types and the `K_ble` — two committed, each with the phone's `pair-confirm` right after its reveal and the Mac's after it, a reveal of another key, a Mac key of low order and a second phone answered `busy`; and every `pair-abort` reason with the reason it reads as, an unknown one as `cancelled` |
| `frames.json` | A fixed `K_ble` (the one the first pairing case derives); notification keys with their key hashes; frames for every type and both directions with the nonce and the expected bytes, each marked whether its plaintext is a `valid` signal, an `unknown-type`, `invalid`, or a type sent in the `wrong-direction` (accepted, so its seq is recorded, then ignored) — among them a `dismiss` with its revision and a `clipboard-small` with its age, each also with that member as a string, `reply-ok` both ways and with a malformed key hash, `lock-state` both ways, locked and unlocked and with its state as a string, and `lock` both ways; rejections — a flipped tag or ciphertext bit, a changed type or seq, the other direction's nonce, replays given with the seqs accepted before (the highest one, one inside the window, one 64 below the highest, and one with a bad tag as well, which fails as `replay` because seq is checked before the AEAD) and wrong sizes — with the expected reason `authentication`, `replay` or `malformed`; and the replay window: its size and sequences of seqs offered in order, each expected as `accept` or `replay` — in order, reordered, duplicated, below the window, after a far jump, across the gaps restarts leave and at the last usable seq |
| `messages.json` | The version and limits; at least one example per message type with its body and the whole length-prefixed frame (plus the sidecar for `icon-data`) — `hello` also with `clipboardFromMac`, `clipboard-set` four times: plain, with `announce`, sensitive and with `ageMs`, and `notif-detail` and `active-set` each also with `r`; and byte streams with the expected sequence of results — `message`, `ignore` or `reject` — covering unknown types and members, skipped sidecars, missing and null members (a null `binaryBytes` reads as no sidecar, a null `announce`, `ageMs`, `r` or `clipboardFromMac` as absent), members of the wrong JSON type (an int or a boolean sent as a string, `announce`, `ageMs`, `r` and `clipboardFromMac` among them, and a string sent as a number), a `pair-confirm` whose `proof` is missing or not a string, a `pair-request` without `commit` or with the former `pk` instead, a `pair-response` without `pk` and a `pair-reveal` whose `pk` is not a string, bad lengths, invalid JSON and UTF-8, and truncated streams |
| `filters.json` | The phone's mirror-filter decision table, read by the launcher only: the order of the checks, the notification attributes they look at with their defaults, and cases of attributes with the expected outcome `mirror` or `skip` and the reason — `own-package`, `excluded` (an app the user keeps off the Mac), `secret` (one the lock screen does not show), `group-summary`, `ongoing`, `promoted-ongoing`, `media`, `not-clearable` or `low-importance` — including the debug-build exemption of the fixture generator's channels; and `lockscreenCases`, how the lock screen's view of a notification, which decides `secret` and the redaction of a private one, follows from its own visibility, the user's choice for its channel and whether the lock screen hides sensitive content |
| `state-sync.json` | The Mac's state-sync diff cases, read by the Mac only: the mirror before, the `active-set` received, and the expected additions, in-place updates, removals and untouched entries |
| `l2cap.json` | The L2CAP key label and limits; derivation cases with `K_ble`, both ephemeral key pairs (private keys included, for testing only) and the expected `ss`, salt, OKM and both record keys — one case swaps which key pair plays the phone, one changes only `K_ble` — and a low-order Mac key with the expected outcome `zero-shared-secret`; record seals with key, counter, nonce, plaintext and the whole record, at counters 0, 1 and 2^32 + 5 and for the largest plaintext (given by its pattern, with the record's size, head, tag and SHA-256); open failures with the expected reason `authentication` or `malformed`; and one exchange with the bytes each side writes — the handshake keys, then records carrying `hello` both ways, a frame split across two records, `sync-request` and `bye` |

A decoder MUST accept every example and frame, and reach every expected outcome. An encoder MUST
reproduce the BLE frames and the L2CAP records byte for byte; for TCP bodies it may differ from the
examples in member order and whitespace only.
