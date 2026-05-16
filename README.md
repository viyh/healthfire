# healthfire

healthfire is an Android app that reads Android **Health Connect** on the
phone and exports every granted record type to a cloud storage bucket as
enveloped JSON Lines. It is the on-device first stage of a personal
health-data pipeline.

Health Connect is the on-device hub that mobile health sources (scales, blood
pressure monitors, watches, rings) write to. It has no cloud API, so an app on
the phone is the only way to read it. healthfire is that app.

## How it works

```
Health Connect (on-device)
  -> healthfire: read granted record types, wrap each in a fixed envelope
  -> Firebase Storage (a GCS bucket you own): hive-partitioned JSON Lines
```

Each record becomes one JSON line: a flat envelope (record type, timestamps,
source device, recording method) plus a `payload` object holding the raw
Health Connect record. Files land at
`hc/dt=<date>/record_type=<type>/<exported_at>__<uuid>.jsonl`.

## Configuration

healthfire embeds **no** cloud credentials or project identifiers. On first
run you import your own Firebase configuration (`google-services.json`); the
app initializes Firebase at runtime and signs you in with Google. One build
works against anyone's own Firebase / GCP project.

## Building

The build runs entirely in Docker - you only need a Docker-compatible runtime
(Docker Engine, Colima, Rancher Desktop, OrbStack, Podman with the docker
shim). The `./healthfire` wrapper handles everything else:

```
./healthfire build         build the debug APK
./healthfire run           build, install and launch on a paired device
./healthfire release       build the release APK
./healthfire logcat        stream filtered logs
./healthfire               list all commands
```

APKs land in `app/build/outputs/apk/`.

## Status

Early development.
