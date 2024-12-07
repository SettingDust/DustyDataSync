package settingdust.dustydatasync.serialization.tag.internal

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.LongArraySerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.internal.NamedValueDecoder
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
import settingdust.dustydatasync.mixin.early.serialization.NBTTagLongArrayAccessor
import settingdust.dustydatasync.serialization.tag.MinecraftTag
import settingdust.dustydatasync.serialization.tag.MinecraftTagDecoder

@OptIn(ExperimentalSerializationApi::class)
internal fun <T> MinecraftTag.readTag(tag: NBTBase, deserializer: DeserializationStrategy<T>): T {
    val decoder =
        when (tag) {
            is NBTTagByteArray -> NBTTagByteArrayDecoder(this, tag)
            is NBTTagIntArray -> NBTTagIntArrayDecoder(this, tag)
            is NBTTagLongArray -> NBTTagLongArrayDecoder(this, tag)
            is NBTTagList -> NBTTagListDecoder(this, tag)
            is NBTTagCompound -> NBTTagCompoundDecoder(this, tag)
            else -> RootTagDecoder(this, tag)
        }
    return decoder.decodeSerializableValue(deserializer)
}

@OptIn(InternalSerializationApi::class)
@ExperimentalSerializationApi
private sealed class TagTreeDecoder(
    final override val nbt: MinecraftTag,
    open val value: NBTBase,
) : NamedValueDecoder(), MinecraftTagDecoder {
    override val serializersModule: SerializersModule
        get() = nbt.serializersModule

    protected val configuration = nbt.configuration

    protected abstract fun currentTag(tag: String): NBTBase

    private fun currentObject() = currentTagOrNull?.let { currentTag(it) } ?: value

    override fun decodeTag() = currentObject()

    override fun composeName(parentName: String, childName: String): String = childName

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val currentObject = currentObject()
        return when (descriptor.kind) {
            StructureKind.LIST ->
                when (descriptor) {
                    ByteArraySerializer().descriptor ->
                        NBTTagByteArrayDecoder(nbt, currentObject as NBTTagByteArray)
                    IntArraySerializer().descriptor ->
                        NBTTagIntArrayDecoder(nbt, currentObject as NBTTagIntArray)
                    LongArraySerializer().descriptor ->
                        NBTTagLongArrayDecoder(nbt, currentObject as NBTTagLongArray)
                    else -> NBTTagListDecoder(nbt, currentObject as NBTTagList)
                }
            StructureKind.MAP -> NBTTagCompoundMapDecoder(nbt, currentObject as NBTTagCompound)
            else ->
                if (currentObject is NBTTagCompound) {
                    NBTTagCompoundDecoder(nbt, currentObject)
                } else {
                    RootTagDecoder(nbt, currentObject)
                }
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // Nothing
    }

    override fun decodeTaggedEnum(tag: String, enumDescriptor: SerialDescriptor) =
        enumDescriptor.getElementIndex(decodeString())

    // There isn't null in NBT
    override fun decodeTaggedNotNullMark(tag: String) = true

    override fun decodeTaggedBoolean(tag: String) =
        when (val byte = (currentTag(tag) as NBTTagByte).byte) {
            0.toByte() -> false
            1.toByte() -> true
            else -> throw IllegalArgumentException("Byte $byte isn't valid boolean value")
        }

    override fun decodeTaggedByte(tag: String) = (currentTag(tag) as NBTTagByte).byte

    override fun decodeTaggedShort(tag: String) = (currentTag(tag) as NBTTagShort).short

    override fun decodeTaggedInt(tag: String) = (currentTag(tag) as NBTTagInt).int

    override fun decodeTaggedLong(tag: String) = (currentTag(tag) as NBTTagLong).long

    override fun decodeTaggedFloat(tag: String) = (currentTag(tag) as NBTTagFloat).float

    override fun decodeTaggedDouble(tag: String) = (currentTag(tag) as NBTTagDouble).double

    override fun decodeTaggedChar(tag: String) = (currentTag(tag) as NBTTagInt).int.toChar()

    override fun decodeTaggedString(tag: String) = (currentTag(tag) as NBTTagString).string
}

@ExperimentalSerializationApi
private class RootTagDecoder(
    nbt: MinecraftTag,
    private val tag: NBTBase,
) : TagTreeDecoder(nbt, tag) {
    override fun currentTag(tag: String) = this.tag

    override fun decodeElementIndex(descriptor: SerialDescriptor) = 0
}

@ExperimentalSerializationApi
private class NBTTagCompoundDecoder(
    nbt: MinecraftTag,
    private val compound: NBTTagCompound,
) : TagTreeDecoder(nbt, compound) {
    private var index = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (index < descriptor.elementsCount) {
            val name = descriptor.getTag(index++)
            if (compound.hasKey(name)) return index - 1
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun currentTag(tag: String) = compound.getTag(tag)!!

    override fun decodeCollectionSize(descriptor: SerialDescriptor) = compound.size

    override fun endStructure(descriptor: SerialDescriptor) {
        if (configuration.ignoreUnknownKeys || descriptor.kind is PolymorphicKind) return

        val names =
            (0 until descriptor.elementsCount).mapTo(HashSet()) { descriptor.getElementName(it) }
        compound.keySet
            .filter { !names.contains(it) }
            .joinToString(", ")
            .let {
                if (it.isNotBlank()) {
                    throw IllegalArgumentException("$it aren't exist in decoder but compound tag")
                }
            }
    }
}

@ExperimentalSerializationApi
private class NBTTagCompoundMapDecoder(
    nbt: MinecraftTag,
    private val compound: NBTTagCompound,
) : TagTreeDecoder(nbt, compound) {
    private val keys = compound.keySet.toList()
    private val size = keys.size * 2
    private var position = -1

    override fun elementName(descriptor: SerialDescriptor, index: Int) = keys[index / 2]!!

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (position < size - 1) {
            position++
            return position
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun currentTag(tag: String) =
        if (position % 2 == 0) NBTTagString(tag) else compound.getTag(tag)!!

    override fun endStructure(descriptor: SerialDescriptor) {
        // do nothing, maps do not have strict keys, so strict mode check is omitted
    }
}

@ExperimentalSerializationApi
private open class NBTTagListDecoder(
    nbt: MinecraftTag,
    private val list: NBTTagList,
) : MinecraftTagDecoder, TagTreeDecoder(nbt, list) {
    private val size = list.tagCount()
    private var index = -1

    override fun currentTag(tag: String) = list[tag.toInt()]!!

    override fun elementName(descriptor: SerialDescriptor, index: Int): String = index.toString()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (index < size - 1) {
            index++
            return index
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor) = size
}

@ExperimentalSerializationApi
private class NBTTagByteArrayDecoder(
    nbt: MinecraftTag,
    private val array: NBTTagByteArray,
) : MinecraftTagDecoder, TagTreeDecoder(nbt, array) {
    private val size = array.byteArray.size
    private var index = -1

    override fun currentTag(tag: String) = array

    override fun elementName(descriptor: SerialDescriptor, index: Int): String = index.toString()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (index < size - 1) {
            index++
            return index
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor) = size
}

@ExperimentalSerializationApi
private class NBTTagIntArrayDecoder(
    nbt: MinecraftTag,
    private val array: NBTTagIntArray,
) : MinecraftTagDecoder, TagTreeDecoder(nbt, array) {
    private val size = array.intArray.size
    private var index = -1

    override fun currentTag(tag: String) = array

    override fun elementName(descriptor: SerialDescriptor, index: Int): String = index.toString()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (index < size - 1) {
            index++
            return index
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor) = size
}

@ExperimentalSerializationApi
private class NBTTagLongArrayDecoder(
    nbt: MinecraftTag,
    private val array: NBTTagLongArray,
) : MinecraftTagDecoder, TagTreeDecoder(nbt, array) {
    private val size = (array as NBTTagLongArrayAccessor).data.size
    private var index = -1

    override fun currentTag(tag: String) = array

    override fun elementName(descriptor: SerialDescriptor, index: Int): String = index.toString()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (index < size - 1) {
            index++
            return index
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor) = size
}
