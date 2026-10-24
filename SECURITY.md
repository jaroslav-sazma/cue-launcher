# Security policy

## Reporting a vulnerability

Report privately through GitHub's **Security → Report a vulnerability** on this repository, which
opens a private advisory only the maintainer can see. If that is not available to you, email
apps@sazmovi.cz instead. Please do not open a public issue for a vulnerability.

Useful in a report: the Cue: version, the Android version, and the shortest sequence of steps or the
`adb` command that shows the problem. A proof of concept is welcome but not required — a clear
description of the mechanism is enough to start.

Cue: is a one-person project maintained in spare time, with no security team behind it. Reports are
handled on a best-effort basis: there is no promised response time, no paid bounty and no guaranteed
patch window. A fix lands as soon as the maintainer can make one, and the advisory is published once
it ships.

Supported: the latest release. Older versions receive no fixes.

## What Cue: is, in security terms

Cue: is an offline Android launcher. It has no accounts, no backend and no analytics, and nothing of
its own reports to its developer or to anyone else. The launcher's own network traffic is limited to
the Home Assistant instance the user set up, and only when they set one up: calls to the addresses
they typed in or picked, a search for the instance on the local network when they ask for it, and —
during sign-in — a socket on the phone's own loopback address that waits for the browser to come
back, plus the instance's sign-in page opened in a browser tab.

Sensitive data it can hold, all of it inside the app's private storage, kept out of cloud backup and
out of device-to-device transfer when moving to a new phone:

- the Home Assistant credential — the refresh token of a signed-in session, or a long-lived token —
  encrypted with an AES-GCM key held in the Android Keystore;
- the pairing secret for incoming Home Assistant events, which also goes into the user's own Home
  Assistant when they upload the blueprint from the launcher;
- the Home Assistant addresses, and the home Wi-Fi names or BSSIDs that tell the launcher it is at
  home;
- notification titles and texts: a notification that activates a scenario has its title and text
  stored with the tile it raised, until that tile ends (after 60 seconds unless the scenario sets
  its own duration or an event that ends it) or — when no such event is set — the notification goes
  away, together with any values the scenario's rules pulled out of it. While the notification
  listener is bound, the title and text of each posted notification from an app a scenario listens
  to are also held in memory, to tell whether one is still there when another is dismissed; other
  apps' notifications are not kept, once the scenarios are loaded shortly after the listener binds.
  A notification shown as a sample while building such a rule is held only by the open form.
  Notification count badges, when switched on, keep in memory only each notification's key and a
  few flags — never its title or text;
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
addresses nor the pairing secret, and the Bluetooth MAC addresses and Wi-Fi network names inside
them are replaced with placeholders. The rest is exported as it is — scenario names, notification
filters, Home Assistant entity ids, links, app shortcut ids, the home screen layout and the list of
hidden apps — and is worth a look before a file is shared.

## Threat model

What Cue: defends against:

- **Another app on the same device.** It can send the launcher's event broadcast, and it can hand
  the launcher a file to import. Both paths are treated as untrusted input. Until the secret
  matches, a broadcast is read for nothing but the pairing secret and — for the log of a rejected
  one — its code, both as plain values, so a malformed one is dropped rather than able to crash the
  home screen, and rejected broadcasts are rate-limited. A file is accepted only as a `content:`
  link the sender granted, and lands in a preview that shows what every imported tile would do
  before the user confirms anything. The file provider that hands an AI chat its description is not
  exported: it serves that one cached file, and only to the app a send grants a one-off read.
- **Scenario JSON pasted from an AI chat.** It is untrusted input, treated like an imported file:
  the same import gate, then a check against the phone — installed apps, their shortcuts and
  screens, paired devices, Home Assistant entities and services, widget providers, the system
  actions it can run. What the scenario's taps would run — Home Assistant calls, link targets, every
  action — is listed before anything else happens, and nothing is saved until the user saves it in
  the builder.
- **A link the user pasted in.** A deep link may be a serialized intent, which is a target and not
  just an address. It is parsed, but never started as it arrived: permission grants are stripped
  from it, a selector is dropped, and a link aimed at the launcher's own components or at a `file:`
  is refused, so pasting a link found on the web cannot turn into a way into Cue: itself.
- **A hostile network.** The address away from home is forced to `https://`, and a cleartext
  `http://` address is only accepted at all when its host is a local one — a `.local` name, a
  private or a Tailscale address — since being at home cannot protect a connection that leaves the
  network. Such an address is then contacted only while the device is at home: on a Wi-Fi network
  the user named, or with the paired instance answering on the local network, so the credential is
  not sent in the clear to whatever answers that name on a foreign LAN. The same rule holds for an
  address that arrives from the mDNS record of a discovered instance, and testing the connection
  obeys it instead of probing.
- **A lost or stolen device, offline.** The credential is sealed with a Keystore key that never
  leaves the device, and nothing is written to a backup or carried over to a new phone.

What it does not defend against, by design:

- **A rooted or physically unlocked device.** Anything with root, `adb` or the unlocked screen can
  read the app's private storage and therefore the pairing secret. Android's app sandbox is the
  boundary; Cue: does not try to be a second one.
- **Someone who deliberately impersonates the home network.** "At home" is decided by a network name
  or BSSID and by the paired instance's identity, none of which is a secret. An attacker who copies
  them on another network can receive a cleartext home address's credential. An `https://` home
  address is not exposed to this.
- **Home Assistant itself.** Whoever controls the user's HA instance can already do more than any
  launcher event would allow.
- **A malicious icon pack or app widget.** Both run as their own app with their own permissions.
  Cue: only asks the system to draw them.

## Audit

[`docs/security-audit.md`](docs/security-audit.md) records what a security review of the launcher
covered and what the state of each area is.
