# healthfire infrastructure (Terraform)

Codifies the Google Cloud / Firebase backend healthfire exports into: the
enabled APIs, the Firebase project, the export bucket, its Storage security
rules (deployed from the repo's `storage.rules`), and the Android app
registration.

## Prerequisites

- An existing GCP project with billing enabled. This config manages resources
  *inside* a project; it does not create the project.
- `gcloud auth application-default login` (or a service account) with rights
  to manage the project.
- Terraform >= 1.5.

## Usage

```
cd terraform
cp terraform.tfvars.example terraform.tfvars   # then edit
terraform init
terraform plan
terraform apply
```

Get the `google-services.json` for the app's first-run import:

```
terraform output -raw google_services_json_base64 | base64 -d > google-services.json
```

## Adopting infrastructure created by hand

If you set Firebase up in the console first, `terraform import` the existing
resources so Terraform manages them instead of trying to recreate them:

```
terraform import google_storage_bucket.export <bucket-name>
terraform import google_firebase_android_app.healthfire <app-id>
```

Then `terraform plan` and reconcile any differences.

## Not covered: Firebase Auth

Enabling Google sign-in is a one-time **console** step - it auto-provisions an
OAuth client (the web client ID the app uses for Credential Manager), and
Terraform cannot create OAuth 2.0 clients. In the Firebase console:
Authentication -> Sign-in method -> enable Google.

Firebase resource attributes vary between `google-beta` provider versions -
run `terraform plan` and adjust if yours differs.
