package settingdust.dustydatasync.serialization

import com.google.gson.GsonBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.also
import kotlin.collections.single
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import com.google.gson.JsonArray as GsonJsonArray
import com.google.gson.JsonElement as GsonJsonElement
import com.google.gson.JsonNull as GsonJsonNull
import com.google.gson.JsonObject as GsonJsonObject
import com.google.gson.JsonPrimitive as GsonJsonPrimitive

internal val gson =
    GsonBuilder()
        .create()

class GsonElementAsStringSerializer(private val json: Json = Json) : KSerializer<GsonJsonElement> {
    override val descriptor =
        PrimitiveSerialDescriptor("gson.JsonElementAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GsonJsonElement {
        return json.parseToJsonElement(decoder.decodeString()).asGson()
    }

    override fun serialize(encoder: Encoder, value: GsonJsonElement) {
        encoder.encodeString(json.encodeToString(value.asKotlin()))
    }
}

/** Kotlin bridge with Gson json elements. Have to use a Json format */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object GsonElementSerializer : KSerializer<GsonJsonElement> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("gson.JsonElement", PolymorphicKind.SEALED) {
            // Resolve cyclic dependency in descriptors by late binding
            element("JsonPrimitive",
                defer { GsonPrimitiveSerializer.descriptor })
            element("JsonNull", defer { GsonNullSerializer.descriptor })
            element("JsonObject",
                defer { GsonObjectSerializer.descriptor })
            element("JsonArray",
                defer { GsonArraySerializer.descriptor })
        }

    override fun deserialize(decoder: Decoder): GsonJsonElement {
        require(decoder is JsonDecoder)
        return decoder.decodeJsonElement().asGson()
    }

    override fun serialize(encoder: Encoder, value: GsonJsonElement) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.asKotlin())
    }
}

object GsonObjectSerializer : KSerializer<GsonJsonObject> {
    private object JsonObjectDescriptor :
        SerialDescriptor by MapSerializer(String.serializer(), GsonElementSerializer).descriptor {
        @ExperimentalSerializationApi
        override val serialName: String = "gson.JsonObject"
    }

    override val descriptor: SerialDescriptor = JsonObjectDescriptor

    override fun deserialize(decoder: Decoder): GsonJsonObject {
        MapSerializer(String.serializer(), GsonElementSerializer).deserialize(decoder).let {
            val obj = com.google.gson.JsonObject()
            it.forEach { (k, v) -> obj.add(k, v) }
            return obj
        }
    }

    override fun serialize(encoder: Encoder, value: GsonJsonObject) {
        MapSerializer(String.serializer(), GsonElementSerializer).serialize(encoder, value.toMap())
    }
}

object GsonArraySerializer : KSerializer<GsonJsonArray> {
    object GsonArrayDescriptor :
        SerialDescriptor by ListSerializer(GsonElementSerializer).descriptor {
        @ExperimentalSerializationApi
        override val serialName: String = "gson.JsonArray"
    }

    override val descriptor = GsonArrayDescriptor

    override fun deserialize(decoder: Decoder): GsonJsonArray {
        val list = ListSerializer(GsonElementSerializer).deserialize(decoder)
        val array = com.google.gson.JsonArray()
        list.forEach { array.add(it) }
        return array
    }

    override fun serialize(encoder: Encoder, value: GsonJsonArray) {
        val list = mutableListOf<GsonJsonElement>()
        value.forEach { list.add(it) }
        ListSerializer(GsonElementSerializer).serialize(encoder, list)
    }
}

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
object GsonPrimitiveSerializer : KSerializer<GsonJsonPrimitive> {
    override val descriptor = buildSerialDescriptor("gson.JsonPrimitive", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GsonJsonPrimitive {
        require(decoder is JsonDecoder)
        val value = decoder.decodeJsonElement() as JsonPrimitive
        return value.asGson()
    }

    override fun serialize(encoder: Encoder, value: GsonJsonPrimitive) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.asKotlin())
    }
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object GsonNullSerializer : KSerializer<GsonJsonNull> {
    override val descriptor = buildSerialDescriptor("gson.JsonNull", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder) =
        GsonJsonNull.INSTANCE.also { decoder.decodeNull() }!!

    override fun serialize(encoder: Encoder, value: GsonJsonNull) {
        encoder.encodeNull()
    }
}

private val jsonObjectMembers =
    GsonJsonObject::class
        .memberProperties
        .single { it.name == "members" }
        .also { it.isAccessible = true }

// Gson below 2.10 no `asMap`
@Suppress("UNCHECKED_CAST")
fun GsonJsonObject.toMap() = jsonObjectMembers.get(this) as MutableMap<String, GsonJsonElement>

fun GsonJsonPrimitive.asKotlin(): JsonPrimitive {
    return when {
        isString -> JsonPrimitive(asString)
        isNumber -> JsonPrimitive(asNumber)
        isBoolean -> JsonPrimitive(asBoolean)
        else -> JsonPrimitive(asString)
    }
}

fun GsonJsonArray.asKotlin(): JsonArray {
    return JsonArray(map { it.asKotlin() })
}

fun GsonJsonObject.asKotlin(): JsonObject {
    return JsonObject(toMap().map { it.key to it.value.asKotlin() }.toMap())
}

fun GsonJsonElement.asKotlin(): JsonElement {
    return when (this) {
        is GsonJsonArray -> asKotlin()
        is GsonJsonObject -> asKotlin()
        is GsonJsonNull -> JsonNull
        is GsonJsonPrimitive -> asKotlin()
        else -> throw IllegalStateException("Unknown type: $this")
    }
}

fun JsonPrimitive.asGson(): GsonJsonPrimitive {
    if (isString) return com.google.gson.JsonPrimitive(content)
    longOrNull?.let {
        return com.google.gson.JsonPrimitive(it)
    }
    content.toBigIntegerOrNull()?.let {
        return com.google.gson.JsonPrimitive(it)
    }
    doubleOrNull?.let {
        return com.google.gson.JsonPrimitive(it)
    }
    content.toBigDecimalOrNull()?.let {
        return com.google.gson.JsonPrimitive(it)
    }
    booleanOrNull?.let {
        return com.google.gson.JsonPrimitive(it)
    }
    return com.google.gson.JsonPrimitive(content)
}

fun JsonArray.asGson(): GsonJsonArray {
    val array = com.google.gson.JsonArray()
    forEach { array.add(it.asGson()) }
    return array
}

fun JsonObject.asGson(): GsonJsonObject {
    return com.google.gson.JsonObject().also { json -> forEach { (k, v) -> json.add(k, v.asGson()) } }
}

fun JsonElement.asGson(): GsonJsonElement {
    return when (this) {
        is JsonArray -> asGson()
        is JsonObject -> asGson()
        is JsonNull -> GsonJsonNull.INSTANCE
        is JsonPrimitive -> asGson()
        else -> throw IllegalStateException("Unknown type: $this")
    }
}
