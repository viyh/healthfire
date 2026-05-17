<img src="design/logo.png" alt="HealthFire" width="110">

# healthfire

healthfire is an Android app that reads Android **Health Connect** on the
phone and exports every granted record type to a cloud storage bucket you own,
as enveloped JSON Lines. It is the on-device first stage of a personal
health-data pipeline.

Health Connect is the on-device hub that mobile health sources (scales, blood
pressure monitors, watches, rings) write to. It has no cloud API, so an app on
the phone is the only way to read it. healthfire is that app.

## How it works

```
Health Connect (on-device)
  -> healthfire: read granted record types, wrap each in a fixed envelope
  -> Firebase Storage (a GCS bucket you own): one JSON Lines file per type
```

Each record becomes one JSON line: a flat envelope (record type, timestamps,
source device, recording method) plus a `payload` object holding the raw
Health Connect record. Files land at
`hc/person_uid=<uid>/record_type=<type>/<exported_at>__<uuid>.jsonl` - one file
per record type per sync, with large types split into size-capped chunks.

The first sync backfills all history; later syncs export only what Health
Connect reports as changed.

## Setup

healthfire embeds **no** cloud credentials or project identifiers. You bring
your own Firebase / GCP project, so one build works for anyone.

### 1. Provision the backend

Use the Terraform in [`terraform/`](terraform/) to set up the GCP side - the
Firebase project, the export bucket, the Storage security rules, and the
Android app registration. See [terraform/README.md](terraform/README.md).

One step Terraform can't do: in the Firebase console, enable **Google**
sign-in (Authentication -> Sign-in method). It auto-provisions the OAuth
client the app needs.

Then export your `google-services.json`:

```
cd terraform
terraform output -raw google_services_json_base64 | base64 -d > google-services.json
```

### 2. Build and install

See [Building](#building) below. Copy the `google-services.json` onto the
phone (e.g. to Downloads) before the first run.

### 3. First run

On first launch the app walks three steps:

1. **Import config** - pick your `google-services.json`.
2. **Sign in** - with the Google account that owns the Firebase project.
3. **Grant Health Connect access** - choose which record types to export.

Then tap **Sync now** to backfill, and optionally turn on automatic background
sync (every 6 hours). Your health data goes only to your own bucket - never to
the app's authors.

## Building

The build runs in Docker, so no JDK or Android SDK is needed on the host -
just a Docker-compatible runtime (Docker Engine, Colima, Rancher Desktop,
OrbStack, Podman with the docker shim). Installing onto a phone also needs
`adb` on the host (Android platform-tools): a Docker container cannot reach a
LAN-attached device, the host can.

The `./healthfire` wrapper drives both:

```
./healthfire build              build the debug APK (Docker)
./healthfire connect <ip:port>  connect to the phone (Wireless debugging)
./healthfire run                build, install and launch on the phone
./healthfire release            build the release APK (Docker)
./healthfire logcat             stream filtered logs
./healthfire                    list all commands
```

APKs land in `app/build/outputs/apk/`.

## License

GNU General Public License v3.0 - see [LICENSE](LICENSE).

## Status

Functionally complete: reads Health Connect, backfills and incrementally
syncs, exports to Firebase Storage, with a first-run setup flow and a
status/metrics home screen. Not published to an app store - sideload a signed
APK.
