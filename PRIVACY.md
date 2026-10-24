# Privacy Policy — Cue: React & Act

*Last updated: 2026-10-11. Applies to the Android app `app.cue.launcher`, developed by Jaroslav
Sazma ([contact](#contact)).*

**Cue: itself sends nothing to its developer or to any third party.** The app has no telemetry, no
analytics, no ads, no crash reporting and no accounts. Everything it reads is processed on your
device. The only data the app sends goes to your own Home Assistant, if you set one up, and is
listed under *Network* below.

## On-device processing

To evaluate the rules you create, the app can — each only after you grant the permission — read
Bluetooth connection state, the Wi-Fi network name, the local network (to find the Home Assistant
instance you pick and to tell whether you are at home), calendar events and the list of your
calendars (so you can pick which ones the calendar widget reads), contacts (for the drawer's contact
search), notifications (for notification triggers, including the end of one, for showing you an
app's current notifications as a sample when you build a rule that pulls a value out of them, and,
if you switch them on, the count badges on app icons), what the music player you picked for the
media widget is playing — title, artist, cover — so the widget can show and control it, and the
phone's location at the moment you tap a tile that remembers a place. This information is used
solely to decide which tiles to show, what to display on them and which icons carry a badge, and to
open a remembered place in your maps app when you ask for it. The location is read once per tap,
never in the background and never followed. A sample notification is held only while the rule's
form is open and is never saved; the rule keeps only the word or two it looks for, never anything
from the notification's title, where a sender's name usually is. Denying a permission only disables
the feature that needs it.

If you mark a Home Assistant action as needing confirmation, the app asks Android's biometric
prompt and receives only whether the check passed. No fingerprint, face or screen-lock data ever
reaches the app.

## Creating scenarios in an AI chat

This is optional, marked experimental and off by default: it takes switching on *Create scenarios in
an AI chat* in *Launcher settings → Appearance & features*. When you then choose *Create in an AI
chat*, nothing is sent yet: you first tick what the description may include. The app then assembles,
on the phone, a description of its scenario format and of this phone. It always holds the installed
apps with their screens and links, and the widgets they offer — all defined by the apps themselves.
Only where you leave them ticked does it hold the shortcuts your apps publish, which include
shortcuts to contacts and conversations and may carry contacts' names, together with the
capabilities that come with them; the names and addresses of paired Bluetooth devices, offered only
while the app has the Bluetooth permission; and your Home Assistant entities and the services your
instance offers, offered only once you set one up. What you untick, and what is not offered, is not
read at all for the description. The app hands the description to an app your phone chooses through
Android's sharing, or, if you ask, saves it as a file where you choose. For it, Cue: itself contacts
nothing but your own Home Assistant, if you set one up and leave it ticked, to read its entity and
service lists, and sends nothing anywhere. A scenario you paste back is checked against all of it,
unticked parts included: on the phone, asking nothing but your own Home Assistant, if you set one
up. It becomes one of your scenarios only if you save it in the builder.

## Stored on your device

The app keeps its data in its private storage on the phone:

- your scenarios, home screen layout, settings and the list of apps you hid;
- the Bluetooth device addresses and Wi-Fi network names your scenarios refer to, and the home Wi-Fi
  names you gave for Home Assistant;
- the title and text of a notification that activated one of your scenarios, and the identifier
  Android gave it, kept with the tile it raised until that tile ends (after 60 seconds unless the
  scenario sets its own duration or an event that ends it) or the notification goes away; for a
  scenario that fires when a notification ends, its last title and text, kept until the tile ends;
- a place you asked the app to remember — its coordinates and when you saved it — kept with the tile
  that shows it and deleted when that tile expires or you close it. It is never exported;
- per app, when you last opened it and how often you opened it from search, to order search results
  — clearable in *Launcher settings → Appearance & features*, under *Drawer*;
- the last 100 events your Home Assistant sent, with their contents — clearable on the Home
  Assistant screen;
- your Home Assistant addresses, the pairing secret, and the credential, which is encrypted with a
  key in the Android Keystore;
- the last description assembled for an AI chat, as a file in the app's cache, replaced by the next
  one, and, in the settings, what you chose to include and whether a pasted answer is still awaited.

Contacts, calendar entries and what is playing are read when needed and not stored. None of the
above is included in Android's cloud backup or carried over when you move to a new phone.

Exporting scenarios or the configuration writes a file to the place you choose. The file never
contains the Home Assistant addresses, the credential or the pairing secret, and Bluetooth addresses
and Wi-Fi names in it are replaced with placeholders. It does contain your scenarios as written —
names, the apps a notification rule listens to and the text it looks for, Home Assistant entities
and the data sent with them, links, and app shortcut ids (a messaging app's shortcut often names a
contact) — and, for a whole configuration, the home screen layout and the list of hidden apps.

## Network

The app's own network traffic goes only to your Home Assistant instance, and only if you set one
up:

- **Finding the instance.** When you tap *Find my Home Assistant*, Android searches the local
  network for Home Assistant instances and shows you the ones it finds; the app learns only the one
  you pick.
- **Signing in** happens in a browser tab against your own instance. The browser hands the result
  back to the app on the phone itself; it never passes through another server.
- **Using it.** The app sends the service calls you configured, reads the states of the entities
  your tiles show and your scenarios' conditions watch while the home screen is visible and, while
  you are also at home, follows those entities live. When an event — a notification, an alarm, a
  Bluetooth disconnect, a Home Assistant event — fires a scenario that also holds itself to such a
  state, the app reads the states of only the entities the fired scenarios' conditions name, once,
  and gives up after three seconds. While you edit a Home Assistant action or condition it also
  reads the list of your entities once, and for an action the services your instance offers — with
  their Czech names too when the app is in Czech — to offer them in the form, and for a condition
  the current state of the one entity you typed in, to show it to you as you edit; the lists stay in
  memory only while that form is open, the Czech service names, which are Home Assistant's own
  translations, for up to a day. Creating a scenario in an AI chat reads the same two lists for the
  description and to check the scenario you paste back, and keeps them in memory only while that
  screen is open, for at most two minutes. Each request carries your credential and goes only to the
  address you configured.
- **Uploading the blueprint.** When you tap *Upload the blueprint to automations*, the app writes
  its event blueprint into your instance's configuration, with your pairing secret and the phone's
  name filled in. Its file name carries a code derived from the device's Android ID by a salted
  one-way hash, so that each phone's blueprint can be told apart; the code stays the same for this
  phone and app, but the Android ID itself is not sent.
- **Ending a session.** When you replace or remove the connection, the app asks your instance to end
  the old session.

All of this travels over HTTPS, with one exception you choose yourself: the address at home may be
plain HTTP when it is a local one — a `.local` name, a name without a dot, or a private, link-local
or Tailscale IP address. The app then contacts that address only while it recognises your home
network, by a Wi-Fi name you listed or by the paired instance answering on the local network;
anywhere else it uses the address away from home, which the app accepts only as HTTPS.

No other servers are contacted by the app itself. Two rows in the app drawer can pass what you typed
to another app when you tap them: the web row opens a Google search in your browser, and the
assistant row hands the text to the Gemini app. From there, that app's own privacy policy applies.
*Report a bug* in the About sheet only opens GitHub's new-issue form in your browser, with the phone
model, the Android build and the app version in the page address: GitHub receives them when the page
opens, which happens only after you tap the button, and nothing is filed until you submit the form.
GitHub's own privacy statement applies to that page.

## Retention and deletion

All app data lives on your device. Clearing the launch history or the event log in the app deletes
them; deleting a scenario deletes what it stored. Uninstalling the app removes everything. What the
app wrote into your Home Assistant — the blueprint and the sign-in session — stays there until you
remove it in Home Assistant.

## Contact

Cue: is developed by Jaroslav Sazma. Questions about this policy: apps@sazmovi.cz
