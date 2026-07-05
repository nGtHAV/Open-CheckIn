# Open-CheckIn

A small desktop app for checking people in and out with a QR card and a webcam. You scan the card, it logs the time. That's basically it. Built with JavaFX and Maven, data goes into a local SQLite file, and nothing ever leaves the machine.

## What it does

Each room is its own little world: its own members, its own settings, its own attendance log. A room runs in one of two modes:

- **Automatic** — scan and you're in. The dashboard shows a live count and who just walked in, plus a card studio for designing member cards.
- **Manual confirmation** — every scan pops up on a panel and someone hits Accept or Reject before it counts.

The scanner itself is a separate window. You can leave it floating or throw it fullscreen onto a second monitor (handy at a door). It shows the member's card, the live camera feed with an alignment reticle, and a picker if you've got more than one camera. There's also a "simulate scan" button so you can test the whole flow without a webcam plugged in.

Other bits:

- **Members** page — searchable list, add or edit people, attach a photo from a file chooser, and save/print any member's card as a PDF.
- **Status** page — a per-day attendance table where you can edit the check-in and check-out times by hand if something got logged wrong.
- **Settings** — per room, so different rooms can behave differently.

Whether a scan counts as a check-in or a check-out is decided by order: the first scan of the day is a check-in, anything after that is a check-out. One row per scan.

## What you need

JDK 21. The camera library (`webcam-capture`) is only really battle-tested there, and the build targets release 21 anyway. On my machine that's `/usr/lib/jvm/java-21-openjdk` — adjust `JAVA_HOME` to wherever yours lives.

## Running it

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

./mvnw clean javafx:run   # start the app
./mvnw test               # run the tests
```

`javafx:run` already carries the JVM flags the native camera code needs (`--enable-native-access`, the `--add-opens` ones), so you don't have to pass anything yourself.

## Building a standalone app

If you want something you can hand to someone who doesn't have Java installed, package it with a bundled JDK 21 runtime:

```bash
./mvnw clean package   # collects the jar + deps into target/app/

jpackage \
  --type app-image \
  --name Open-CheckIn \
  --input target/app \
  --main-jar Open-CheckIn-1.0-SNAPSHOT.jar \
  --main-class xyz.ngthav.opencheckin.Launcher \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
  --java-options "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED" \
  --dest target/dist
```

That drops a runnable app at `target/dist/Open-CheckIn/bin/Open-CheckIn`. Swap `--type` for `deb`/`rpm`, `msi`/`exe`, or `dmg`/`pkg` if you want a proper installer instead — but each one has to be built on its own OS, so the bundle I produce here only runs on Linux/x64.

## Where the data ends up

Everything is created next to the app the first time you run it:

```
data/
├─ open-checkin.db          # SQLite, schema set up automatically on startup
└─ pictures/
   └─ <roomId>/             # one folder per room
      └─ 20260705-JohnDoe.png
```

Nothing is pre-seeded. First launch with no rooms yet just shows "Create a room to get started" on every page.

## How the code is laid out

- `model` — the records: `Room`, `Member`, `Attendance`
- `db` — `Database` plus a DAO per table (SQL only; deleting a room is FK-guarded)
- `qr` — `QrPayload`, `QrEncoder`, `QrDecoder`
- `camera` — `CameraService`, which owns the one webcam and degrades quietly if there isn't one
- `card` — `CardRenderer`, turning a single node into preview / PDF / print
- `checkin` — `CheckInService` (the scan logic) and `AttendanceSummary`
- `ui` — the shell, sidebar, pages, dialogs, and scanner window
- `app` — `AppState`, `AppShell`, `Navigator`

The two things most likely to be wrong if I got them wrong — the scan decision (`CheckInService.decide`) and the MIN/MAX time inference on the Status page — are written as pure functions and covered by the unit tests, so they're easy to check against the spec.
