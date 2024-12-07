package settingdust.dustydatasync.serialization.tag.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.LongArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.internal.NamedValueEncoder
import kotlinx.serialization.modules.SerializersModule
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagByte
import net.minecraft.nbt.NBTTagByteArray
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagDouble
import net.minecraft.nbt.NBTTagFloat
import net.minecraft.nbt.NBTTagInt
import net.minecraft.nbt.NBTTagIntArray
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagLong
import net.minecraft.nbt.NBTTagLongArray
import net.minecraft.nbt.NBTTagShort
import net.minecraft.nbt.NBTTagString
import settingdust.dustydatasync.mixin.early.serialization.NBTBaseAccessor
import settingdust.dustydatasync.mixin.early.serialization.NBTTagByteArrayAccessor
import settingdust.dustydatasync.mixin.early.serialization.NBTTagIntArrayAccessor
import settingdust.dustydatasync.mixin.early.serialization.NBTTagLongArrayAccessor
import settingdust.dustydatasync.serialization.NbtFormatSerializer
import settingdust.dustydatasync.serialization.tag.MinecraftTag
import settingdust.dustydatasync.serialization.tag.MinecraftTagEncoder

@OptIn(ExperimentalSerializationApi::class)
internal fun <T> MinecraftTag.writeTag(value: T, serializer: SerializationStrategy<T>): NBTBase {
    lateinit var result: NBTBase
    val encoder = CompoundTagEncoder(this) { result = it }
    encoder.encodeSerializableValue(serializer, value)
    return result
}

@OptIn(InternalSerializationApi::class)
@ExperimentalSerializationApi
private sealed class TagTreeEncoder(
    final override val nbt: MinecraftTag,
    protected val tagConsumer: (NBTBase) -> Unit,
) : NamedValueEncoder(), MinecraftTagEncoder {
    override val serializersModule: SerializersModule
        get() = nbt.serializersModule

    protected val configuration = nbt.configuration

    override fun encodeTag(tag: NBTBase) =
        encodeSerializableValue(NbtFormatSerializer(configuration.classDiscriminator), tag)

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int) =
        configuration.encodeDefaults

    override fun composeName(parentName: String, childName: String) = childName

    abstract fun putTag(key: String, tag: NBTBase)

    abstract fun getCurrent(): NBTBase

    // There isn't null in NBT
    override fun encodeNotNullMark() {}

    override fun encodeNull() {}

    override fun encodeTaggedNull(tag: String) {}

    override fun encodeTaggedInt(tag: String, value: Int) = putTag(tag, NBTTagInt(value))

    override fun encodeTaggedByte(tag: String, value: Byte) = putTag(tag, NBTTagByte(value))

    override fun encodeTaggedShort(tag: String, value: Short) = putTag(tag, NBTTagShort(value))

    override fun encodeTaggedLong(tag: String, value: Long) = putTag(tag, NBTTagLong(value))

    override fun encodeTaggedFloat(tag: String, value: Float) = putTag(tag, NBTTagFloat(value))

    override fun encodeTaggedDouble(tag: String, value: Double) = putTag(tag, NBTTagDouble(value))

    override fun encodeTaggedBoolean(tag: String, value: Boolean) =
        putTag(tag, NBTTagByte(if (value) 1 else 0))

    override fun encodeTaggedChar(tag: String, value: Char) = putTag(tag, NBTTagInt(value.code))

    override fun encodeTaggedString(tag: String, value: String) = putTag(tag, NBTTagString(value))

    override fun encodeTaggedEnum(tag: String, enumDescriptor: SerialDescriptor, ordinal: Int) =
        putTag(tag, NBTTagString(enumDescriptor.getElementName(ordinal)))

    override fun encodeTaggedValue(tag: String, value: Any) =
        when (value) {
            is ByteArray -> putTag(tag, NBTTagByteArray(value))
            is IntArray -> putTag(tag, NBTTagIntArray(value))
            is LongArray -> putTag(tag, NBTTagLongArray(value))
            else -> putTag(tag, NBTTagString(value.toString()))
        }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val consumer =
            if (currentTagOrNull == null) {
                tagConsumer
            } else {
                { tag -> putTag(currentTag, tag) }
            }
        val encoder =
            when (descriptor.kind) {
                StructureKind.LIST ->
                    when (descriptor) {
                        ByteArraySerializer().descriptor ->
                            CollectionTagEncoder(nbt, NBTTagByteArray(emptyList()), consumer)

                        IntArraySerializer().descriptor ->
                            CollectionTagEncoder(nbt, NBTTagIntArray(emptyList()), consumer)

                        LongArraySerializer().descriptor ->
                            CollectionTagEncoder(nbt, NBTTagLongArray(emptyList()), consumer)

                        else -> CollectionTagEncoder(nbt, NBTTagList(), consumer)
                    }

                StructureKind.MAP -> CompoundTagMapEncoder(nbt, consumer)
                else -> CompoundTagEncoder(nbt, consumer)
            }
        return encoder
    }

    override fun endEncode(descriptor: SerialDescriptor) {
        tagConsumer(getCurrent())
    }
}

@ExperimentalSerializationApi
private open class CompoundTagEncoder(nbt: MinecraftTag, tagConsumer: (NBTBase) -> Unit) :
    TagTreeEncoder(nbt, tagConsumer) {
    protected val compound = NBTTagCompound()

    override fun putTag(key: String, tag: NBTBase) {
        compound.setTag(key, tag)
    }

    override fun getCurrent() = compound

    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?,
    ) {
        if (value != null)
            super.encodeNullableSerializableElement(descriptor, index, serializer, value)
    }
}

@Suppress("UNCHECKED_CAST")
@ExperimentalSerializationApi
private class CompoundTagMapEncoder(nbt: MinecraftTag, tagConsumer: (NBTTagList) -> Unit) :
    CompoundTagEncoder(nbt, tagConsumer as (NBTBase) -> Unit) {
    private lateinit var key: String
    private var isKey = true

    override fun putTag(key: String, tag: NBTBase) {
        if (isKey) {
            this.key =
                when (tag) {
                    is NBTTagList,
                    is NBTTagIntArray,
                    is NBTTagLongArray,
                    is NBTTagByteArray,
                    is NBTTagCompound ->
                        throw IllegalStateException("Map key shouldn't be list or compound")

                    else -> (tag as NBTBaseAccessor).string
                }
            isKey = false
        } else {
            compound.setTag(this.key, tag)
            isKey = true
        }
    }
}

@ExperimentalSerializationApi
private class CollectionTagEncoder(
    nbt: MinecraftTag,
    private val tag: NBTBase,
    tagConsumer: (NBTBase) -> Unit
) : TagTreeEncoder(nbt, tagConsumer) {

    override fun elementName(descriptor: SerialDescriptor, index: Int) = index.toString()

    override fun putTag(key: String, tag: NBTBase) {
        when (this.tag) {
            is NBTTagList -> this.tag.appendTag(tag)
            is NBTTagByteArray ->
                (this.tag as NBTTagByteArrayAccessor).data += (tag as NBTTagByte).byte

            is NBTTagLongArray ->
                (this.tag as NBTTagLongArrayAccessor).data += (tag as NBTTagLong).long

            is NBTTagIntArray -> (this.tag as NBTTagIntArrayAccessor).data += (tag as NBTTagInt).int
            else -> error("The NBT ${this.tag} isn't a collection NBT")
        }
    }

    override fun getCurrent() = tag
}
