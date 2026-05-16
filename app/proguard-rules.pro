# healthfire R8 / ProGuard rules.
#
# The payload serializer (hc/RecordSerializer.kt) reflects over Health Connect
# record and unit classes with kotlin-reflect. Keep their members so R8 does
# not rename or strip the properties it reads.
-keep class androidx.health.connect.client.records.** { *; }
-keep class androidx.health.connect.client.units.** { *; }
