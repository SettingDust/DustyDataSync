package settingdust.dustydatasync.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

@OptIn(ExperimentalSerializationApi::class)
abstract class JsonContentPolymorphicSerializer<T : Any>(private val baseClass: KClass<T>) : KSerializer<T> {
    /**
     * A descriptor for this set of content-based serializers.
     * By default, it uses the name composed of [baseClass] simple name,
     * kind is set to [PolymorphicKind.SEALED] and contains 0 elements.
     *
     * However, this descriptor can be overridden to achieve better representation of custom transformed JSON shape
     * for schema generating/introspection purposes.
     */
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("JsonContentPolymorphicSerializer<${baseClass.simpleName}>", PolymorphicKind.SEALED)

    final override fun serialize(encoder: Encoder, value: T) {
        val actualSerializer =
            encoder.serializersModule.getPolymorphic(baseClass, value)
                ?: encoder.serializersModule.serializerOrNull(value::class.createType())
                ?: throwSubtypeNotRegistered(value::class, baseClass)
        encoder.encodeSerializableValue(actualSerializer, value)
    }

    final override fun deserialize(decoder: Decoder): T {
        require(decoder is JsonDecoder)
        val tree = decoder.decodeJsonElement()

        val actualSerializer = selectDeserializer(tree) as KSerializer<T>
        return decoder.json.decodeFromJsonElement(actualSerializer, tree)
    }

    /**
     * Determines a particular strategy for deserialization by looking on a parsed JSON [element].
     */
    protected abstract fun selectDeserializer(element: JsonElement): DeserializationStrategy<T>

    private fun throwSubtypeNotRegistered(subClass: KClass<*>, baseClass: KClass<*>): Nothing {
        val subClassName = subClass.simpleName ?: "$subClass"
        val scope = "in the scope of '${baseClass.simpleName}'"
        throw SerializationException(
            "Class '${subClassName}' is not registered for polymorphic serialization $scope.\n" +
                    "Mark the base class as 'sealed' or register the serializer explicitly.")
    }

}