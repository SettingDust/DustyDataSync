package settingdust.dustydatasync.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.LongArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagByte
import net.minecraft.nbt.NBTTagByteArray
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagDouble
import net.minecraft.nbt.NBTTagEnd
import net.minecraft.nbt.NBTTagFloat
import net.minecraft.nbt.NBTTagInt
import net.minecraft.nbt.NBTTagIntArray
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagLong
import net.minecraft.nbt.NBTTagLongArray
import net.minecraft.nbt.NBTTagShort
import net.minecraft.nbt.NBTTagString
import settingdust.dustydatasync.mixin.early.serialization.NBTTagCompoundAccessor
import settingdust.dustydatasync.mixin.early.serialization.NBTTagEndAccessor
import settingdust.dustydatasync.mixin.early.serialization.NBTTagLongArrayAccessor
import settingdust.dustydatasync.serialization.tag.MinecraftTagDecoder
import settingdust.dustydatasync.serialization.tag.MinecraftTagEncoder

@ExperimentalSerializationApi
fun TagsModule(discriminator: String) = SerializersModule {
    contextual(NbtJsonContentPolymorphicSerializer(discriminator))
    contextual(NBTTagCompoundSerializer)
    contextual(NBTTagEndSerializer)
    contextual(NBTTagStringSerializer)
    contextual(NBTTagByteSerializer)
    contextual(NBTTagShortSerializer)
    contextual(NBTTagIntSerializer)
    contextual(NBTTagLongSerializer)
    contextual(NBTTagFloatSerializer)
    contextual(NBTTagDoubleSerializer)
    contextual(NBTTagListSerializer)
    val polymorphicSerializer = NbtPolymorphicSerializer(discriminator)
    contextual(polymorphicSerializer as KSerializer<NBTTagByteArray>)
    contextual(polymorphicSerializer as KSerializer<NBTTagIntArray>)
    contextual(polymorphicSerializer as KSerializer<NBTTagLongArray>)
}

// https://github.com/BenWoodworth/knbt/blob/main/src/commonMain/kotlin/internal/StringifiedNbtReader.kt
private val DOUBLE = Regex("""^([-+]?(?:\d+\.?|\d*\.\d+)(?:e[-+]?\d+)?)d${'$'}""", RegexOption.IGNORE_CASE)
private val FLOAT = Regex("""^([-+]?(?:\d+\.?|\d*\.\d+)(?:e[-+]?\d+)?)f${'$'}""", RegexOption.IGNORE_CASE)
private val BYTE = Regex("""([-+]?(?:0|[1-9]\d*))b""", RegexOption.IGNORE_CASE)
private val LONG = Regex("""([-+]?(?:0|[1-9]\d*))l""", RegexOption.IGNORE_CASE)
private val SHORT = Regex("""([-+]?(?:0|[1-9]\d*))s""", RegexOption.IGNORE_CASE)
private val INT = Regex("""([-+]?(?:0|[1-9]\d*))""")
private val STRING = Regex(""""(.*?)"""")

@OptIn(ExperimentalSerializationApi::class)
class NbtPolymorphicSerializer(discriminator: String) : SealedClassSerializer<NBTBase>(
    "nbt",
    NBTBase::class,
    arrayOf(NBTTagByteArray::class, NBTTagIntArray::class, NBTTagLongArray::class),
    arrayOf(
        PolymorphicSurrogateSerializer(NBTTagByteArraySerializer),
        PolymorphicSurrogateSerializer(NBTTagIntArraySerializer),
        PolymorphicSurrogateSerializer(NBTTagLongArraySerializer)
    ),
    discriminator
)

class NbtJsonContentPolymorphicSerializer(val discriminator: String) :
    JsonContentPolymorphicSerializer<NBTBase>(NBTBase::class) {

    @OptIn(ExperimentalSerializationApi::class)
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<NBTBase> {
        if (element is JsonPrimitive) {
            val input = element.content
            return when {
                BYTE.matches(input) -> NBTTagByteSerializer
                INT.matches(input) -> NBTTagIntSerializer
                SHORT.matches(input) -> NBTTagShortSerializer
                LONG.matches(input) -> NBTTagLongSerializer
                DOUBLE.matches(input) -> NBTTagDoubleSerializer
                FLOAT.matches(input) -> NBTTagFloatSerializer
                else -> NBTTagStringSerializer
            }
        }
        if (element is JsonArray) {
            return NBTTagListSerializer
        }
        if (element is JsonObject) {
            val type = element[discriminator]?.jsonPrimitive?.contentOrNull
            return when (type) {
                NBTTagByteArraySerializer.descriptor.serialName -> NbtPolymorphicSerializer(discriminator)
                NBTTagIntArraySerializer.descriptor.serialName -> NbtPolymorphicSerializer(discriminator)
                NBTTagLongArraySerializer.descriptor.serialName -> NbtPolymorphicSerializer(discriminator)
                else -> NBTTagCompoundSerializer
            }
        }
        return NbtFormatSerializer(discriminator)
    }
}

@ExperimentalSerializationApi
@OptIn(InternalSerializationApi::class)
class NbtFormatSerializer(discriminator: String) : KSerializer<NBTBase> {
    companion object {
        val descriptor: SerialDescriptor =
            buildSerialDescriptor("nbt", PolymorphicKind.SEALED) {
                element(
                    NBTTagCompound::class.simpleName!!, defer { NBTTagCompoundSerializer.descriptor })
                element(NBTTagEnd::class.simpleName!!, defer { NBTTagEndSerializer.descriptor })
                element(NBTTagString::class.simpleName!!, defer { NBTTagStringSerializer.descriptor })
                element(NBTTagByte::class.simpleName!!, defer { NBTTagByteSerializer.descriptor })
                element(NBTTagDouble::class.simpleName!!, defer { NBTTagDoubleSerializer.descriptor })
                element(NBTTagFloat::class.simpleName!!, defer { NBTTagFloatSerializer.descriptor })
                element(NBTTagInt::class.simpleName!!, defer { NBTTagIntSerializer.descriptor })
                element(NBTTagLong::class.simpleName!!, defer { NBTTagLongSerializer.descriptor })
                element(NBTTagShort::class.simpleName!!, defer { NBTTagShortSerializer.descriptor })
                element(NBTTagList::class.simpleName!!, defer { NBTTagListSerializer.descriptor })
                element(
                    NBTTagByteArray::class.simpleName!!, defer { NBTTagByteArraySerializer.descriptor })
                element(
                    NBTTagIntArray::class.simpleName!!, defer { NBTTagIntArraySerializer.descriptor })
                element(
                    NBTTagLongArray::class.simpleName!!, defer { NBTTagLongArraySerializer.descriptor })
            }
    }

    val polymorphicSerializer = NbtJsonContentPolymorphicSerializer(discriminator)
    override val descriptor = NbtFormatSerializer.descriptor

    override fun deserialize(decoder: Decoder): NBTBase {
        return if (decoder is MinecraftTagDecoder) decoder.decodeTag()
        else polymorphicSerializer.deserialize(decoder)
    }

    override fun serialize(encoder: Encoder, value: NBTBase) {
        if (encoder is MinecraftTagEncoder)
            when (value) {
                is NBTTagCompound -> encoder.encodeSerializableValue(NBTTagCompoundSerializer, value)
                is NBTTagEnd -> encoder.encodeSerializableValue(NBTTagEndSerializer, value)
                is NBTTagString -> encoder.encodeSerializableValue(NBTTagStringSerializer, value)
                is NBTTagByte -> encoder.encodeSerializableValue(NBTTagByteSerializer, value)
                is NBTTagDouble -> encoder.encodeSerializableValue(NBTTagDoubleSerializer, value)
                is NBTTagFloat -> encoder.encodeSerializableValue(NBTTagFloatSerializer, value)
                is NBTTagInt -> encoder.encodeSerializableValue(NBTTagIntSerializer, value)
                is NBTTagLong -> encoder.encodeSerializableValue(NBTTagLongSerializer, value)
                is NBTTagShort -> encoder.encodeSerializableValue(NBTTagShortSerializer, value)
                is NBTTagList -> encoder.encodeSerializableValue(NBTTagListSerializer, value)
                is NBTTagByteArray -> encoder.encodeSerializableValue(NBTTagByteArraySerializer, value)
                is NBTTagIntArray -> encoder.encodeSerializableValue(NBTTagIntArraySerializer, value)
                is NBTTagLongArray -> encoder.encodeSerializableValue(NBTTagLongArraySerializer, value)
                else -> throw IllegalArgumentException("Unknown tag type: $value")
            }
        else polymorphicSerializer.serialize(encoder, value)
    }
}

@ExperimentalSerializationApi
object NBTTagCompoundSerializer : KSerializer<NBTTagCompound> {
    override val descriptor =
        SerialDescriptor(
            "n_comp",
            mapSerialDescriptor(String.serializer().descriptor, NbtFormatSerializer.descriptor)
        )

    override fun deserialize(decoder: Decoder) =
        NBTTagCompound().apply {
            MapSerializer(String.serializer(), decoder.serializersModule.serializer<NBTBase>())
                .deserialize(decoder)
                .forEach { setTag(it.key, it.value) }
        }

    override fun serialize(encoder: Encoder, value: NBTTagCompound) {
        MapSerializer(String.serializer(), encoder.serializersModule.serializer<NBTBase>())
            .serialize(encoder, (value as NBTTagCompoundAccessor).tagMap)
    }
}

@ExperimentalSerializationApi
object NBTTagListSerializer : KSerializer<NBTTagList> {
    override val descriptor =
        SerialDescriptor("n_list", listSerialDescriptor(NbtFormatSerializer.descriptor))

    override fun deserialize(decoder: Decoder) =
        NBTTagList().apply {
            for (nbt in
            ListSerializer(decoder.serializersModule.serializer<NBTBase>())
                .deserialize(decoder)) {
                appendTag(nbt)
            }
        }

    override fun serialize(encoder: Encoder, value: NBTTagList) =
        ListSerializer(encoder.serializersModule.serializer<NBTBase>())
            .serialize(encoder, value.toList())
}

@ExperimentalSerializationApi
object NBTTagByteArraySerializer : KSerializer<NBTTagByteArray> {
    private val serializer = ByteArraySerializer()
    override val descriptor = SerialDescriptor("n_bytes", serializer.descriptor)

    override fun deserialize(decoder: Decoder) = NBTTagByteArray(serializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: NBTTagByteArray) =
        serializer.serialize(encoder, value.byteArray)
}

@ExperimentalSerializationApi
object NBTTagIntArraySerializer : KSerializer<NBTTagIntArray> {
    private val serializer = IntArraySerializer()
    override val descriptor = SerialDescriptor("n_ints", serializer.descriptor)

    override fun deserialize(decoder: Decoder) = NBTTagIntArray(serializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: NBTTagIntArray) =
        serializer.serialize(encoder, value.intArray)
}

@ExperimentalSerializationApi
object NBTTagLongArraySerializer : KSerializer<NBTTagLongArray> {
    private val serializer = LongArraySerializer()
    override val descriptor = SerialDescriptor("n_longs", serializer.descriptor)

    override fun deserialize(decoder: Decoder) = NBTTagLongArray(serializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: NBTTagLongArray) =
        serializer.serialize(encoder, (value as NBTTagLongArrayAccessor).data)
}

@ExperimentalSerializationApi
object NBTTagEndSerializer : KSerializer<NBTTagEnd> {
    override val descriptor = PrimitiveSerialDescriptor("n_end", PrimitiveKind.BYTE)

    override fun deserialize(decoder: Decoder) =
        NBTTagEndAccessor.construct().also {
            val byte = decoder.decodeByte()
            require(byte == 0.toByte()) { "NBTTagEnd require value 0b but ${byte}b" }
        }

    override fun serialize(encoder: Encoder, value: NBTTagEnd) = encoder.encodeByte(0)
}

@ExperimentalSerializationApi
object NBTTagStringSerializer : KSerializer<NBTTagString> {
    override val descriptor = PrimitiveSerialDescriptor("n_stri", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): NBTTagString {
        val input = decoder.decodeString()
        val result = STRING.find(input)
        return if (result != null) NBTTagString(result.groupValues[1])
        else NBTTagString(input)
    }

    override fun serialize(encoder: Encoder, value: NBTTagString) =
        encoder.encodeString("\"${value.string}\"")
}

/** NumericTag */
@ExperimentalSerializationApi
object NBTTagByteSerializer : KSerializer<NBTTagByte> {
    override val descriptor = PrimitiveSerialDescriptor("n_byte", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): NBTTagByte {
        val input = decoder.decodeString()
        val result = BYTE.find(input)
        require(result != null) { "Input '$input' isn't suitable for current type" }
        require(result.groupValues.size == 2) { "Can't find single input from '$input'" }
        return NBTTagByte(result.groupValues[1].toByte())
    }

    override fun serialize(encoder: Encoder, value: NBTTagByte) {
        encoder.encodeString("${value.byte}b")
    }
}

@ExperimentalSerializationApi
object NBTTagDoubleSerializer : KSerializer<NBTTagDouble> {
    override val descriptor = PrimitiveSerialDescriptor("n_doub", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): NBTTagDouble {
        val input = decoder.decodeString()
        val result = DOUBLE.find(input)
        require(result != null) { "Input '$input' isn't suitable for current type" }
        require(result.groupValues.size == 2) { "Can't find single input from '$input'" }
        return NBTTagDouble(result.groupValues[1].toDouble())
    }

    override fun serialize(encoder: Encoder, value: NBTTagDouble) =
        encoder.encodeString("${value.double}d")
}

@ExperimentalSerializationApi
object NBTTagFloatSerializer : KSerializer<NBTTagFloat> {
    override val descriptor = PrimitiveSerialDescriptor("n_floa", PrimitiveKind.FLOAT)

    override fun deserialize(decoder: Decoder): NBTTagFloat {
        val input = decoder.decodeString()
        val result = FLOAT.find(input)
        require(result != null) { "Input '$input' isn't suitable for current type" }
        require(result.groupValues.size == 2) { "Can't find single input from '$input'" }
        return NBTTagFloat(result.groupValues[1].toFloat())
    }

    override fun serialize(encoder: Encoder, value: NBTTagFloat) {
        encoder.encodeString("${value.float}f")
    }
}

@ExperimentalSerializationApi
object NBTTagIntSerializer : KSerializer<NBTTagInt> {
    override val descriptor = PrimitiveSerialDescriptor("n_int", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): NBTTagInt {
        val input = decoder.decodeString()
        val result = INT.find(input)
        require(result != null) { "Input '$input' isn't suitable for current type" }
        require(result.groupValues.size == 2) { "Can't find single input from '$input'" }
        return NBTTagInt(result.groupValues[1].toInt())
    }

    override fun serialize(encoder: Encoder, value: NBTTagInt) {
        encoder.encodeString(value.int.toString())
    }
}

@ExperimentalSerializationApi
object NBTTagLongSerializer : KSerializer<NBTTagLong> {
    override val descriptor = PrimitiveSerialDescriptor("n_long", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): NBTTagLong {
        val input = decoder.decodeString()
        val result = LONG.find(input)
        require(result != null) { "Input '$input' isn't suitable for current type" }
        require(result.groupValues.size == 2) { "Can't find single input from '$input'" }
        return NBTTagLong(result.groupValues[1].toLong())
    }

    override fun serialize(encoder: Encoder, value: NBTTagLong) {
        encoder.encodeString("${value.long}l")
    }
}

@ExperimentalSerializationApi
object NBTTagShortSerializer : KSerializer<NBTTagShort> {
    override val descriptor = PrimitiveSerialDescriptor("n_short", PrimitiveKind.SHORT)

    override fun deserialize(decoder: Decoder): NBTTagShort {
        val input = decoder.decodeString()
        val result = SHORT.find(input)
        require(result != null) { "Input '$input' isn't suitable for current type" }
        require(result.groupValues.size == 2) { "Can't find single input from '$input'" }
        return NBTTagShort(result.groupValues[1].toShort())
    }

    override fun serialize(encoder: Encoder, value: NBTTagShort) {
        encoder.encodeString("${value.short}s")
    }
}
