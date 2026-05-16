package io.github.viyh.healthfire.hc

import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Converts an arbitrary Health Connect record into a [JsonElement] by walking
 * its Kotlin properties with reflection. There is no per-record-type code:
 * adding a record type, or a device that writes an existing type, needs no
 * change here.
 *
 * The result becomes the `payload` of an export envelope. Leaf values map to
 * portable JSON: instants to RFC 3339 / ISO-8601 strings, Health Connect unit
 * values to `{"unit","value"}` objects, enums to their name.
 */
object RecordSerializer {

    /**
     * Per-class property lists. A class is reflected once and reused, so
     * serializing many records of one type does not re-introspect the class.
     */
    private val propertyCache = ConcurrentHashMap<KClass<*>, List<KProperty1<Any, *>>>()

    /** Serializes [value] and everything reachable from it into a [JsonElement]. */
    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Char -> JsonPrimitive(value.toString())
        is Int, is Long, is Short, is Byte -> JsonPrimitive(value as Number)
        is Float -> doubleJson(value.toDouble())
        is Double -> doubleJson(value)
        is Enum<*> -> JsonPrimitive(value.name)
        is Instant -> JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(value))
        is LocalDate -> JsonPrimitive(value.toString())
        is LocalDateTime -> JsonPrimitive(value.toString())
        is LocalTime -> JsonPrimitive(value.toString())
        is ZonedDateTime -> JsonPrimitive(value.toString())
        is OffsetDateTime -> JsonPrimitive(value.toString())
        is ZoneOffset -> JsonPrimitive(value.id)
        is ZoneId -> JsonPrimitive(value.id)
        is Duration -> JsonPrimitive(value.toString())
        is Period -> JsonPrimitive(value.toString())
        is Length -> unitJson("m", value.inMeters)
        is Mass -> unitJson("g", value.inGrams)
        is Energy -> unitJson("kcal", value.inKilocalories)
        is Power -> unitJson("W", value.inWatts)
        is Pressure -> unitJson("mmHg", value.inMillimetersOfMercury)
        is Temperature -> unitJson("Cel", value.inCelsius)
        is TemperatureDelta -> unitJson("Cel", value.inCelsius)
        is Velocity -> unitJson("m/s", value.inMetersPerSecond)
        is Volume -> unitJson("L", value.inLiters)
        is BloodGlucose -> unitJson("mmol/L", value.inMillimolesPerLiter)
        is Percentage -> unitJson("percent", value.value)
        is Map<*, *> ->
            JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map { toJson(it) })
        is Array<*> -> JsonArray(value.map { toJson(it) })
        else -> objectToJson(value)
    }

    private fun unitJson(unit: String, amount: Double): JsonElement =
        JsonObject(mapOf("unit" to JsonPrimitive(unit), "value" to doubleJson(amount)))

    /** JSON cannot represent NaN or infinity, so such values become null. */
    private fun doubleJson(value: Double): JsonElement =
        if (value.isFinite()) JsonPrimitive(value) else JsonNull

    /** Reflects over an object's Kotlin properties - the generic fallback. */
    private fun objectToJson(value: Any): JsonElement {
        val properties = propertiesOf(value::class)
            ?: return JsonPrimitive(value.toString())
        val fields = LinkedHashMap<String, JsonElement>(properties.size)
        for (property in properties) {
            fields[property.name] =
                runCatching { toJson(property.get(value)) }.getOrDefault(JsonNull)
        }
        return JsonObject(fields)
    }

    @Suppress("UNCHECKED_CAST")
    private fun propertiesOf(kClass: KClass<*>): List<KProperty1<Any, *>>? {
        propertyCache[kClass]?.let { return it }
        val properties = runCatching {
            kClass.memberProperties.map { property ->
                (property as KProperty1<Any, *>).apply { isAccessible = true }
            }
        }.getOrNull()
        if (properties != null) propertyCache[kClass] = properties
        return properties
    }
}
