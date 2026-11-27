# Privacy Policy — Koyomi (こよみ)

_Last updated: 14 July 2026_

Koyomi ("the app") is a calendar app for Android. This policy explains what
the app does and does not do with your information. It is written to be read
by anyone, not only lawyers.

日本語版は [こちら / privacy-policy.ja.md](./privacy-policy.ja.md) をご覧ください。

## The short version

- Koyomi does **not** collect, transmit, or sell any personal data.
- Koyomi has **no** servers, analytics, advertising, or tracking of any kind.
- Your calendars and events stay on your device and in whatever accounts you
  have already added to Android (e.g. Google Calendar). Koyomi only reads and
  writes them locally through the Android calendar system.

## What data Koyomi accesses

**Calendar data (on-device).**
Koyomi reads and writes your device's calendars and events through Android's
standard Calendar Provider, so it can show your schedule and let you create,
edit, move, duplicate, and delete events. This includes event titles, times,
locations, descriptions, colors, reminders, and which calendar an event
belongs to. Koyomi accesses this only on your device. It does **not** copy
this data anywhere, and it has no ability to send it over the network.

**App settings (on-device).**
Your preferences — theme, theme pack, week-start day, widget appearance,
which calendars are shown, sync interval, and similar — are stored locally on
your device using Android DataStore. Tasks you create in Koyomi are stored in
a local database on your device. None of this leaves your device except
through Android's own optional backup (see below).

## What Koyomi does NOT do

- It does not have its own account system or require sign-in.
- It does not contain analytics, crash reporting SDKs, advertising, or
  third-party tracking libraries.
- It does not make its own network connections to send your data anywhere.
- It does not access your contacts, location, microphone, camera, or files
  beyond the calendar and, if you choose, an `.ics` file you explicitly pick
  for import or export.

## Network use

Koyomi itself does not connect to the internet to move your data. When you
ask Koyomi to refresh, it asks Android's system to sync your existing
accounts (for example Google Calendar). That synchronization is performed by
Android and the account provider you already set up — not by Koyomi — and is
governed by that provider's own privacy policy.

## Import and export

You may export your events to an `.ics` file or import events from an `.ics`
file. These files are read from and written to the location **you** choose
through the Android file picker. Koyomi does not upload or retain copies of
them.

## Permissions

- **Calendar (read/write):** to display and manage your events. This is the
  core function of the app.
- **Notifications (Android 13+):** to show event and task reminders you set.

You can revoke these at any time in Android Settings; the app will simply be
unable to perform the corresponding function.

## Backup

If you have Android's system backup enabled, your Koyomi **settings** (not
your calendar data, which Koyomi does not store) may be included in that
backup by Android. This is handled entirely by the operating system.

## Children

Koyomi is a general-purpose calendar app and does not target children, nor
does it knowingly collect any data from anyone, including children.

## Changes to this policy

If this policy changes, the updated version will be published at the same
URL with a new "last updated" date.

## Contact

Questions about this policy: **souru67.giants.6@gmail.com**
