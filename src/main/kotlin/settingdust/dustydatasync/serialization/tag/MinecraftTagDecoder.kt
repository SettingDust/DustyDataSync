package settingdust.dustydatasync.serialization.tag

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import net.minecraft.nbt.NBTBase

@ExperimentalSerializationApi
interface MinecraftTagDecoder : Decoder, CompositeDecoder {
    val nbt: MinecraftTag

    fun decodeTag(): NBTBase
}
