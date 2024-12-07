package settingdust.dustydatasync.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

@Serializable
data class PolymorphicSurrogate<T>(val value: T)

inline fun <reified T> PolymorphicSurrogateSerializer() =
    PolymorphicSurrogateSerializer(serializer<T>())

inline fun <reified T> SerializersModule.PolymorphicSurrogateSerializer() =
    PolymorphicSurrogateSerializer(serializer<T>())

class PolymorphicSurrogateSerializer<T>(private val serializer: KSerializer<T>) : KSerializer<T> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor =
        SerialDescriptor(
            serializer.descriptor.serialName,
            PolymorphicSurrogate.serializer(serializer).descriptor,
        )

    override fun serialize(encoder: Encoder, value: T) =
        encoder.encodeSerializableValue(PolymorphicSurrogate.serializer(serializer), PolymorphicSurrogate(value))

    override fun deserialize(decoder: Decoder) =
        decoder.decodeSerializableValue(PolymorphicSurrogate.serializer(serializer)).value
}
