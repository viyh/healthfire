variable "project_id" {
  description = "ID of the existing GCP project that hosts Firebase."
  type = string
}

variable "location" {
  description = "Location for the export bucket - a GCS region or multi-region."
  type = string
  default = "US"
}

variable "bucket_name" {
  description = "Globally unique name of the GCS bucket healthfire exports into."
  type = string
}

variable "android_package_name" {
  description = "Application ID of the healthfire Android app."
  type = string
  default = "io.github.viyh.healthfire"
}
