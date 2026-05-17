output "export_bucket" {
  description = "GCS bucket healthfire exports into."
  value = google_storage_bucket.export.name
}

output "android_app_id" {
  description = "Firebase Android app ID (mobilesdk_app_id)."
  value = google_firebase_android_app.healthfire.app_id
}

# The google-services.json the app imports on first run.
data "google_firebase_android_app_config" "healthfire" {
  provider = google-beta
  project = var.project_id
  app_id = google_firebase_android_app.healthfire.app_id
}

output "google_services_json_base64" {
  description = "google-services.json contents, base64-encoded. Decode to a file for the app's first-run import."
  value = data.google_firebase_android_app_config.healthfire.config_file_contents
  sensitive = true
}
