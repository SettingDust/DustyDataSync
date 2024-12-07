package settingdust.dustydatasync.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlin.collections.get
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
open class SealedClassSerializer<T : Any>(
    serialName: String,
    override val baseClass: KClass<T>,
    subclasses: Array<KClass<out T>>,
    subclassSerializers: Array<KSerializer<out T>>,
    val discriminator: String = "_t"
) : AbstractPolymorphicSerializer<T>() {

    /**
     * This constructor is needed to store serial info annotations defined on the sealed class.
     * Support for such annotations was added in Kotlin 1.5.30; previous plugins used primary
     * constructor of this class directly, therefore this constructor is secondary.
     *
     * This constructor can (and should) became primary when Require-Kotlin-Version is raised to at
     * least 1.5.30 to remove necessity to store annotations separately and calculate descriptor via
     * `lazy {}`.
     *
     * When doing this change, also migrate secondary constructors from [PolymorphicSerializer] and
     * [ObjectSerializer].
     */
    @PublishedApi
    internal constructor(
        serialName: String,
        baseClass: KClass<T>,
        subclasses: Array<KClass<out T>>,
        subclassSerializers: Array<KSerializer<out T>>,
        classAnnotations: Array<Annotation>
    ) : this(serialName, baseClass, subclasses, subclassSerializers) {
        this._annotations = classAnnotations.asList()
    }

    private var _annotations: List<Annotation> = emptyList()

    override val descriptor: SerialDescriptor by
        lazy(LazyThreadSafetyMode.PUBLICATION) {
            buildSerialDescriptor(serialName, PolymorphicKind.SEALED) {
                element(discriminator, String.serializer().descriptor)
                val elementDescriptor =
                    buildSerialDescriptor(
                        "kotlinx.serialization.Sealed<${baseClass.simpleName}>",
                        SerialKind.CONTEXTUAL) {
                            // serialName2Serializer is guaranteed to have no duplicates — checked
                            // in `init`.
                            serialName2Serializer.forEach { (name, serializer) ->
                                element(name, serializer.descriptor)
                            }
                        }
                element("value", elementDescriptor)
                annotations = _annotations
            }
        }

    private val class2Serializer: MutableMap<KClass<out T>, KSerializer<out T>>
    private val serialName2Serializer: Map<String, KSerializer<out T>>

    init {
        if (subclasses.size != subclassSerializers.size) {
            throw IllegalArgumentException(
                "All subclasses of sealed class ${baseClass.simpleName} should be marked @Serializable")
        }

        // Note: we do not check whether different serializers are provided if the same KClass
        // duplicated in the `subclasses`.
        // Plugin should produce identical serializers, although they are not always strictly equal
        // (e.g. new ObjectSerializer
        // may be created every time)
        class2Serializer = subclasses.zip(subclassSerializers).toMap().toMutableMap()
        serialName2Serializer =
            class2Serializer.entries
                .groupingBy { it.value.descriptor.serialName }
                .aggregate { key, accumulator: Map.Entry<KClass<*>, KSerializer<out T>>?, element, _
                    ->
                    if (accumulator != null) {
                        error(
                            "Multiple sealed subclasses of '$baseClass' have the same serial name '$key':" +
                                " '${accumulator.key}', '${element.key}'")
                    }
                    element
                }
                .mapValues { it.value.value }
    }

    override fun findPolymorphicSerializerOrNull(
        decoder: CompositeDecoder,
        klassName: String?
    ): DeserializationStrategy<T>? {
        return serialName2Serializer[klassName]
            ?: super.findPolymorphicSerializerOrNull(decoder, klassName)
    }

    override fun findPolymorphicSerializerOrNull(
        encoder: Encoder,
        value: T
    ): SerializationStrategy<T>? {
        return (class2Serializer[value::class]
            ?: class2Serializer.keys
                .firstOrNull { k -> k.isSuperclassOf(value::class) }
                ?.let {
                    val serializer = class2Serializer[it]
                    class2Serializer[value::class] = serializer as KSerializer<out T>
                    serializer
                }
            ?: super.findPolymorphicSerializerOrNull(encoder, value))
            as SerializationStrategy<T>?
    }
}
