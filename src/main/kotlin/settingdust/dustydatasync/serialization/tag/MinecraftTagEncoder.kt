package settingdust.dustydatasync.serialization.tag

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.nbt.NBTBase

@ExperimentalSerializationApi
interface MinecraftTagEncoder : Encoder, CompositeEncoder {
    val nbt: MinecraftTag

    fun encodeTag(tag: NBTBase)
}
