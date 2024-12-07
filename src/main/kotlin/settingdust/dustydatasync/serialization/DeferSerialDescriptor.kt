package settingdust.dustydatasync.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind

@OptIn(ExperimentalSerializationApi::class)
internal fun defer(deferred: () -> SerialDescriptor): SerialDescriptor =
    object : SerialDescriptor {

        private val original: SerialDescriptor by lazy(deferred)

        override val serialName: String
            get() = original.serialName

        override val kind: SerialKind
            get() = original.kind

        override val elementsCount: Int
            get() = original.elementsCount

        override fun getElementName(index: Int): String = original.getElementName(index)

        override fun getElementIndex(name: String): Int = original.getElementIndex(name)

        override fun getElementAnnotations(index: Int): List<Annotation> =
            original.getElementAnnotations(index)

        override fun getElementDescriptor(index: Int): SerialDescriptor =
            original.getElementDescriptor(index)

        override fun isElementOptional(index: Int): Boolean = original.isElementOptional(index)
    }
