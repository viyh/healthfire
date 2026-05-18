# Querying your export in BigQuery

healthfire writes your Health Connect data to a GCS bucket as enveloped JSON
Lines (see the [README](../README.md)). Because the files are immutable and
Hive-partitioned, the whole export is queryable in place with a single
BigQuery **external table** - no load job, no copy, no scheduled pipeline. New
sync files, and entirely new record types, appear automatically.

This guide is consumer-agnostic - it sets up the table and shows example
queries against your own bucket and project. It is only one way to consume the
export; the files are plain JSON Lines and any tool can read them.

## What the bucket holds

healthfire writes one object per record type per sync:

```
gs://<bucket>/hc/person_uid=<uid>/record_type=<type>/<exported_at>__<uuid>.jsonl
```

`person_uid` and `record_type` are Hive partition keys. Every line is one
Health Connect record wrapped in a fixed, flat envelope - flat except
`payload`, which carries the raw record:

| field | type | meaning |
|---|---|---|
| `schema_version` | INT64 | envelope version (currently 1) |
| `record_type` | STRING | canonical type name; also the path partition |
| `hc_record_id` | STRING | Health Connect record id (UUID) |
| `person_uid` | STRING | exporting user; also the path partition |
| `recorded_at` | TIMESTAMP | record start / instant time, true UTC |
| `recorded_at_end` | TIMESTAMP | interval end, UTC; null for instantaneous records |
| `zone_offset` | STRING | local UTC offset at recording time, e.g. `-08:00` |
| `exported_at` | TIMESTAMP | when healthfire uploaded the record |
| `source_app` | STRING | Android package that wrote the record to Health Connect |
| `source_device_type` | STRING | originating device type (`WATCH`, `SCALE`, ...) |
| `source_device_manufacturer` | STRING | originating device manufacturer |
| `source_device_model` | STRING | originating device model |
| `recording_method` | STRING | `ACTIVELY_RECORDED` / `AUTOMATICALLY_RECORDED` / `MANUALLY_ENTERED` / `UNKNOWN` |
| `hc_last_modified` | TIMESTAMP | Health Connect last-modified time |
| `app_version` | STRING | healthfire version that exported the record |
| `payload` | JSON | the complete raw Health Connect record; shape varies per `record_type` |

## Prerequisites

- The export bucket name: `terraform -chdir=terraform output -raw export_bucket`.
- A BigQuery dataset, in any project. Create one with
  `bq mk --dataset <project>:<dataset>` or in the console.
- The account creating and querying the table needs `roles/storage.objectViewer`
  on the bucket, plus the usual BigQuery job-user and data-editor roles on the
  dataset. The bucket and the dataset do not have to be in the same project.

## Create the external table

Run once - in the BigQuery console, with `bq query --use_legacy_sql=false`, or
from any client. Substitute your bucket and dataset:

```sql
CREATE OR REPLACE EXTERNAL TABLE `<project>.<dataset>.hc_records`
(
  schema_version INT64,
  hc_record_id STRING,
  recorded_at TIMESTAMP,
  recorded_at_end TIMESTAMP,
  zone_offset STRING,
  exported_at TIMESTAMP,
  source_app STRING,
  source_device_type STRING,
  source_device_manufacturer STRING,
  source_device_model STRING,
  recording_method STRING,
  hc_last_modified TIMESTAMP,
  app_version STRING,
  payload JSON
)
WITH PARTITION COLUMNS (
  person_uid STRING,
  record_type STRING
)
OPTIONS (
  format = 'NEWLINE_DELIMITED_JSON',
  uris = ['gs://<bucket>/hc/*'],
  hive_partition_uri_prefix = 'gs://<bucket>/hc',
  ignore_unknown_values = TRUE
)
```

Two things worth knowing:

- **`record_type` and `person_uid` are declared only as partition columns**,
  not in the column list. They appear in both the file path and the JSON
  envelope, and a column cannot be both a partition key and a data column. The
  column list omits the in-file copies, and `ignore_unknown_values = TRUE`
  tells BigQuery to skip them - and any future envelope field you have not
  added to the schema - rather than erroring.
- **One table covers every record type.** `payload` is typed `JSON`, so it
  absorbs whatever shape each record type has. A brand-new Health Connect
  record type simply appears as a new `record_type` value with no DDL change.

The table is metadata only - it reads the JSONL objects in place. New sync
files are picked up on the next query; there is nothing to refresh. To force
every query to prune by partition (a cost guard), add
`require_hive_partition_filter = TRUE` to `OPTIONS`.

## Sample queries

Always filter on `record_type` (a partition key): BigQuery then scans only
that type's objects instead of the whole export.

### What is in your export

```sql
SELECT record_type,
       COUNT(*) AS records,
       MIN(recorded_at) AS earliest,
       MAX(recorded_at) AS latest
FROM `<project>.<dataset>.hc_records`
GROUP BY record_type
ORDER BY records DESC;
```

### Blood pressure readings

```sql
SELECT recorded_at,
       LAX_FLOAT64(payload.systolic.value) AS systolic_mmhg,
       LAX_FLOAT64(payload.diastolic.value) AS diastolic_mmhg,
       source_app
FROM `<project>.<dataset>.hc_records`
WHERE record_type = 'blood_pressure'
ORDER BY recorded_at DESC;
```

### Body weight

Health Connect quantities serialize as `{"unit": "...", "value": ...}`
objects. Mass is in **grams**, energy in kilocalories, length in metres,
power in watts - read `.value` and convert:

```sql
SELECT recorded_at,
       LAX_FLOAT64(payload.weight.value) / 1000 AS weight_kg
FROM `<project>.<dataset>.hc_records`
WHERE record_type = 'weight'
ORDER BY recorded_at DESC
LIMIT 20;
```

### One day of heart rate (nested sample series)

Series record types (`heart_rate`, `speed`) nest many samples under one
record. Unnest `payload.samples`:

```sql
SELECT TIMESTAMP(JSON_VALUE(sample.time)) AS sample_time,
       LAX_INT64(sample.beatsPerMinute) AS bpm
FROM `<project>.<dataset>.hc_records`,
     UNNEST(JSON_QUERY_ARRAY(payload, '$.samples')) AS sample
WHERE record_type = 'heart_rate'
  AND DATE(recorded_at) = '2026-05-06'
ORDER BY sample_time;
```

### Sleep stage minutes per session

`sleep_session` nests a `stages` array of typed intervals. Health Connect
stage codes: 1 awake, 4 light, 5 deep, 6 REM.

```sql
SELECT hc_record_id,
       recorded_at AS sleep_start,
       SUM(IF(LAX_INT64(stage.stage) = 5, minutes, 0)) AS deep_min,
       SUM(IF(LAX_INT64(stage.stage) = 4, minutes, 0)) AS light_min,
       SUM(IF(LAX_INT64(stage.stage) = 6, minutes, 0)) AS rem_min
FROM `<project>.<dataset>.hc_records`,
     UNNEST(JSON_QUERY_ARRAY(payload, '$.stages')) AS stage,
     UNNEST([TIMESTAMP_DIFF(
       TIMESTAMP(JSON_VALUE(stage.endTime)),
       TIMESTAMP(JSON_VALUE(stage.startTime)), SECOND) / 60.0]) AS minutes
WHERE record_type = 'sleep_session'
GROUP BY hc_record_id, sleep_start
ORDER BY sleep_start DESC;
```

## Notes on `payload`

- **Units** are `{"unit", "value"}` objects (grams, kcal, metres, watts,
  m/s, litres, mmHg, Celsius, percent). Read `.value`.
- **Timestamps** inside `payload` are RFC 3339 strings; wrap them in
  `TIMESTAMP(...)`.
- **Enums** are sometimes integers (e.g. sleep `stage`, nutrition `mealType`,
  blood-pressure `bodyPosition`) and sometimes names. Check the
  [Health Connect record reference](https://developer.android.com/reference/androidx/health/connect/client/records/package-summary)
  for a record type's fields.
- **Series** record types put their samples in an array under `payload`
  (`samples`, `stages`, `deltas`); unnest with `JSON_QUERY_ARRAY`.
- `recorded_at` and `recorded_at_end` are already extracted from the record's
  start/instant and end times, so most date filtering needs only the envelope,
  not `payload`.
