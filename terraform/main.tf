provider "google" {
  project = var.project_id
}

provider "google-beta" {
  project = var.project_id
}

# APIs healthfire's backend needs. disable_on_destroy = false keeps a
# `terraform destroy` from knocking out services other things may use.
locals {
  apis = [
    "cloudresourcemanager.googleapis.com",
    "serviceusage.googleapis.com",
    "firebase.googleapis.com",
    "firebasestorage.googleapis.com",
    "firebaserules.googleapis.com",
    "storage.googleapis.com",
    "identitytoolkit.googleapis.com",
  ]
}

resource "google_project_service" "apis" {
  for_each = toset(local.apis)
  project = var.project_id
  service = each.value
  disable_on_destroy = false
}

# Turns the GCP project into a Firebase project.
resource "google_firebase_project" "healthfire" {
  provider = google-beta
  project = var.project_id
  depends_on = [google_project_service.apis]
}

# The bucket healthfire writes JSONL exports into. It holds health data,
# so keep it private: uniform access and enforced public-access prevention.
resource "google_storage_bucket" "export" {
  project = var.project_id
  name = var.bucket_name
  location = var.location
  uniform_bucket_level_access = true
  public_access_prevention = "enforced"
  depends_on = [google_project_service.apis]
}

# Registers the bucket with Firebase so the Firebase SDK and the Storage
# security rules below apply to it.
resource "google_firebase_storage_bucket" "export" {
  provider = google-beta
  project = var.project_id
  bucket_id = google_storage_bucket.export.name
  depends_on = [google_firebase_project.healthfire]
}

# Storage security rules, deployed straight from the repo's storage.rules:
# a signed-in user may read and write only under their own person_uid prefix.
resource "google_firebaserules_ruleset" "storage" {
  provider = google-beta
  project = var.project_id

  source {
    files {
      name = "storage.rules"
      content = file("${path.module}/../storage.rules")
    }
  }

  depends_on = [google_firebase_storage_bucket.export]
}

resource "google_firebaserules_release" "storage" {
  provider = google-beta
  project = var.project_id
  name = "firebase.storage/${google_storage_bucket.export.name}"
  ruleset_name = google_firebaserules_ruleset.storage.name
}

# The Android app registration. Its config is the google-services.json the
# app imports on first run - see the google_services_json output.
resource "google_firebase_android_app" "healthfire" {
  provider = google-beta
  project = var.project_id
  display_name = "healthfire"
  package_name = var.android_package_name
  depends_on = [google_firebase_project.healthfire]
}
