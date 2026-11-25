# Security policy

## Reporting a vulnerability

Report privately through GitHub's **Security → Report a vulnerability** on this repository, which
opens a private advisory only the maintainer can see. If that is not available to you, email
apps@sazmovi.cz instead. Please do not open a public issue for a vulnerability.

Useful in a report: the version of Cue: React & Act, the Android version, and the shortest sequence
of steps or the `adb` command that shows the problem. A proof of concept is welcome but not required
— a clear description of the mechanism is enough to start.

Cue: React & Act is a one-person project maintained in spare time, with no security team behind it.
Reports are handled on a best-effort basis: there is no promised response time, no paid bounty and
no guaranteed patch window. A fix lands as soon as the maintainer can make one, and the advisory is
published once it ships.

Supported: the latest release. Older versions receive no fixes.

## What Cue: React & Act is, in security terms

Cue: React & Act is an offline Android launcher. It has no accounts, no backend and no analytics,
and nothing of its own reports to its developer or to anyone else. The launcher's own network
traffic is limited to the Home Assistant instance the user set up, and only when they set one up:
calls to the addresses they typed in or picked, a search for the instance on the local network when
they ask for it, and — during sign-in — a socket on the phone's own loopback address that waits for
the browser to come back, plus the instance's sign-in page opened in a browser tab. The optional Mac
bridge, off by default, adds the user's own paired Mac: TLS connections to it on the local network,
and a Bluetooth link while it is in range, which also carries those connections where the two share
no network. The optional vault adds no traffic of its own: it reads one encrypted file the user
picked, through Android's document access, from the app that holds it — the Google Drive app.

Sensitive data it can hold, all of it inside the app's private storage, kept out of cloud backup and
out of device-to-device transfer when moving to a new phone:

- the Home Assistant credential — the refresh token of a signed-in session, or a long-lived token —
  encrypted with an AES-GCM key held in the Android Keystore;
- the pairing secret for incoming Home Assistant events, which also goes into the user's own Home
  Assistant when they upload the blueprint from the launcher;
- the Home Assistant addresses, and the home Wi-Fi names or BSSIDs that tell the launcher it is at
  home;
- the Mac bridge's pairing: the paired Mac's certificate fingerprint, name and address, and the key
  that seals its Bluetooth frames, itself sealed with an AES-GCM key held in the Android Keystore.
  The phone's own TLS key is generated inside the Keystore and never leaves it. All of it is deleted
  when the bridge is turned off or unpaired. Notifications and clipboard text pass through memory on
  their way between the phone and the Mac and are not stored; a clip from the Mac that arrives while
  the phone is locked waits in memory until the unlock, until the phone has been out of the Mac's
  range for about a minute or until the companion service's window closes, whichever comes first;
- the vault: the reference to the vault file the user picked, with its persisted read grant, and the
  vault's content key (CEK), sealed with an AES-256-GCM key held in the Android Keystore that opens
  only after a biometric or screen-lock authentication for that one use; the file's vault and key
  ids beside it. While the vault is unlocked, a copy of the still encrypted file sits in the app's
  private storage, outside every backup, and the CEK in memory; only what an app reads is decrypted,
  into that read's buffer and a cache of at most sixteen 64 KiB segments. The lock, the automatic
  lock or the end of the process deletes the copy and drops the key and the cache; nothing decrypted
  is ever written to storage. *Forget vault* deletes the reference, the sealed key and the Keystore
  key, and resets the vault's settings;
- notification titles and texts: a notification that activates a scenario has its title and text
  stored with the tile it raised, until that tile ends (after 60 seconds unless the scenario sets
  its own duration or an event that ends it) or — when no such event is set and no state the
  scenario also checks holds the tile — the notification goes away, together with any values the
  scenario's rules pulled out of it. While the notification listener is bound, the title and text of
  each posted notification from an app a scenario listens to are also held in memory, to tell
  whether one is still there when another is dismissed; other apps' notifications are not kept, once
  the scenarios are loaded shortly after the listener binds. A notification shown as a sample while
  building such a rule is held only by the open form. Notification count badges, when switched on,
  keep in memory only each notification's key and a few flags — never its title or text;
- a remembered place: its coordinates and the time it was saved, read once at the user's tap and
  stored with the tile that shows it until that tile expires or is closed; never exported;
- the Home Assistant event log (the last 100 events with their payloads, clearable), the apps the
  user hid, and per app the time of its last launch and how often it was opened from search
  (clearable);
- read but not stored: contacts and calendar entries, only after the user granted the matching
  permission and only while a feature needs them, and what the media widget's player is playing —
  title, artist, cover — held in memory only while the widget is on screen;
- the last description written for an AI chat, when the user creates a scenario that way: the
  scenario format plus an inventory of the phone — app labels and packages with their links,
  activities, widget providers and the system actions the phone cannot run, and only the parts the
  user ticked: the apps' shortcuts with their capabilities, paired Bluetooth device names and
  addresses, Home Assistant entity ids, names and services. It is one file in the app's cache,
  replaced by the next, and leaves the app only when the user shares it with an app of their choice
  or saves it.

Exported scenario and configuration files carry neither the credential, the Home Assistant
addresses, the pairing secret nor anything of the Mac bridge, and the Bluetooth MAC addresses and
Wi-Fi network names inside them are replaced with placeholders. The rest is exported as it is —
scenario names, notification filters, Home Assistant entity ids, links, app shortcut ids, the home
screen layout and the list of hidden apps — and is worth a look before a file is shared.

## Threat model

What Cue: React & Act defends against:

- **Another app on the same device.** It can send the launcher's event broadcast, and it can hand
  the launcher a file to import. Both paths are treated as untrusted input. Until the secret
  matches, a broadcast is read for nothing but the pairing secret and — for the log of a rejected
  one — its code, both as plain values, so a malformed one is dropped rather than able to crash the
  home screen, and rejected broadcasts are rate-limited. A file is accepted only as a `content:`
  link the sender granted, and lands in a preview that shows what every imported tile would do
  before the user confirms anything. The file provider that hands an AI chat its description is not
  exported: it serves that one cached file, and only to the app a send grants a one-off read. While
  a Mac is paired, an app can also hand *Send to Mac* a text, as the selection menu and the share
  sheet do; the text goes to the user's own Mac clipboard, read as plain text only and capped at
  100 KB, and the Mac announces it. While the vault is unlocked, its documents provider is exported,
  but guarded by `MANAGE_DOCUMENTS`, which only the system's document UI holds: another app reaches
  a vault file only through the read grant a picker hands it for the file the user picked, never the
  whole vault, and only until the vault locks: document ids hold for one unlock, so a grant the app
  kept, or a link it stored, finds nothing in a later one, and the lock revokes every such grant.
  Nothing in it can be written, created, renamed or deleted.
- **Scenario JSON pasted from an AI chat.** It is untrusted input, treated like an imported file:
  the same import gate, then a check against the phone — installed apps, their shortcuts and
  screens, paired devices, Home Assistant entities and services, widget providers, the system
  actions it can run. What the scenario's taps would run — Home Assistant calls, link targets, every
  action — is listed before anything else happens, and nothing is saved until the user saves it in
  the builder.
- **A link the user pasted in.** A deep link may be a serialized intent, which is a target and not
  just an address. It is parsed, but never started as it arrived: permission grants are stripped
  from it, a selector is dropped, and a link aimed at the launcher's own components or at a `file:`
  is refused, so pasting a link found on the web cannot turn into a way into Cue: React & Act
  itself.
- **The network and the radio between the phone and the Mac.** Pairing trusts nothing it is told:
  the phone finds the Mac by a Bonjour flag that only says where to look, and both sides accept any
  certificate on the pairing connection. The phone shows a six-digit code derived from both
  certificate fingerprints and both ephemeral X25519 keys; the Mac's user types it in, and the Mac
  compares it with its own in constant time, so the code is what binds the Mac's side of the pairing
  to the phone that showed it. The phone commits to its key before it learns the Mac's, the Mac's
  key is fresh for each attempt, and each attempt ends the Mac's pairing mode. A second device that
  asks to pair while a pairing runs is told the Mac is busy. Each side's `pair-confirm` carries an
  HMAC proof keyed by the shared secret and checked in constant time: the phone sends its own right
  after revealing its key, and the Mac sends its own once it has accepted the typed code and
  completes once the phone's has verified too. The phone completes the pairing when the Mac's proof
  verifies and pins the certificate it observed on the pairing connection. Both sides show the first
  eight characters of the Mac's certificate — the phone in its bridge settings, the Mac in its
  pairing window — so the two can be compared. After pairing, every connection over the network is
  TLS 1.3 with mutual authentication, and each side accepts only the leaf certificate it pinned. A
  connection over the Bluetooth L2CAP channel, which the phone uses where the two share no network,
  is authenticated by possession of the pairing key instead: each side sends a fresh X25519 key, and
  the record keys are derived from that exchange together with the pairing key, so a device without
  the pairing can neither seal nor open a single record, and a recorded connection stays unreadable
  even if the pairing key leaks later. Its records are sealed with ChaCha20-Poly1305 under a key per
  direction and a counter that never repeats under it. Bluetooth frames are sealed with
  ChaCha20-Poly1305 under a key derived during pairing, with a nonce bound to the direction and a
  sequence number that is never used twice under the key and accepted at most once, so a frame that
  is forged, replayed or reflected back is dropped. A foreign Bluetooth device can connect,
  subscribe and open the channel, but reads nothing and is never taken for the phone: the Mac counts
  a subscriber as the phone only once a frame that subscriber wrote itself has opened, and sends a
  stranger nothing but a sealed wake-up call — never a closing, a reply or a clip. The phone,
  likewise, counts a Bluetooth connection as one to the Mac only once a frame from the Mac has
  opened over it. While the bridge is off its companion service and the *Send to Mac* entry are
  disabled components, so nothing of it can be reached or bound.
- **A hostile network.** The address away from home is forced to `https://`, and a cleartext
  `http://` address is only accepted at all when its host is a local one — a `.local` name, a
  private or a Tailscale address — since being at home cannot protect a connection that leaves the
  network. Such an address is then contacted only while the device is at home: on a Wi-Fi network
  the user named, or with the paired instance answering on the local network, so the credential is
  not sent in the clear to whatever answers that name on a foreign LAN. The same rule holds for an
  address that arrives from the mDNS record of a discovered instance, and testing the connection
  obeys it instead of probing. A discovered address never replaces a filled-in one silently — a
  notice under the field names the old address until it is saved or edited — and the test sends a
  stored credential only to addresses the user has saved: any other address is reported as untried
  and nothing is sent.
- **Someone in the user's Google account.** The vault file in Google Drive is encrypted on the Mac
  with a key that only the master password unwraps, so the account yields one unreadable file;
  changing the password changes the key, so an old password that leaked opens only the copies from
  before the change. An offline attack on the file is limited by PBKDF2 with 600 000 iterations and
  the password's own strength. A file tampered with in the account is detected before anything of it
  is shown: the phone opens only a file whose every segment authenticates.
- **A lost or stolen device, offline.** The credential and the bridge's frame key are sealed with
  Keystore keys that never leave the device, the bridge's TLS key lives in the Keystore itself, and
  nothing is written to a backup or carried over to a new phone. The vault's content key is sealed
  with a Keystore key that every use has to unlock with the user's fingerprint, face or screen lock,
  so a locked vault stays closed to whoever holds the phone without them; while it is locked,
  nothing of its content is on the phone at all.

What it does not defend against, by design:

- **A rooted or physically unlocked device.** Anything with root, `adb` or the unlocked screen can
  read the app's private storage and therefore the pairing secret. Android's app sandbox is the
  boundary; Cue: React & Act does not try to be a second one.
- **Whoever knows the phone's screen lock.** The vault's key opens with the fingerprint, the face or
  the screen lock alike, so anyone who knows the PIN, pattern or password can unlock the vault on
  that phone, as they can unlock the phone itself.
- **What other apps do with a vault file.** A file the user opens from the vault in another app — a
  viewer, a mail draft — is that app's to keep: a cache, a recent-files entry, a copy. The vault
  being read-only does not stop copying, and while it is unlocked, code that has taken over the
  phone can read it. The system's file picker and file manager may also keep the names of vault
  files they showed among their recent files after the lock: the ids beside them name no path and
  resolve to nothing once the vault locks, but a file's name is what a picker shows, so it stays
  readable there.
- **An outdated copy.** The phone reads the version of the file the Google Drive app last
  downloaded, which can be older than the Mac's; the vault screen shows when the Mac last wrote the
  file it opened, so the user can tell.
- **Someone who deliberately impersonates the home network.** "At home" is decided by a network name
  or BSSID and by the paired instance's identity, none of which is a secret. An attacker who copies
  them on another network can receive a cleartext home address's credential. An `https://` home
  address is not exposed to this.
- **Home Assistant itself.** Whoever controls the user's HA instance can already do more than any
  launcher event would allow.
- **The paired Mac itself.** The bridge trusts the Mac the user paired as the user's own device:
  whoever controls it sees the mirrored notifications and can close them, reply through the phone's
  apps and set the phone's clipboard. It reaches only the notifications it was shown: those of apps
  the user keeps off the Mac and those the lock screen does not show are out of its reach.
- **A malicious icon pack or app widget.** Both run as their own app with their own permissions.
  Cue: React & Act only asks the system to draw them.

## Audit

[`docs/security-audit.md`](docs/security-audit.md) records what a security review of the launcher
covered and what the state of each area is. The Mac app's own side of the bridge — what it stores,
its signing and its residual risks — is described in its repository's
[`docs/security.md`](https://github.com/jaroslav-sazma/cue-bridge/blob/main/docs/security.md). The
vault's Mac side — the Mac app Cue: Vault & View (for Google Drive), which creates and writes the
vault file — is described in the same way in its repository's
[`docs/security.md`](https://github.com/jaroslav-sazma/cue-vault/blob/main/docs/security.md).
