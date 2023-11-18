package settingdust.dustydatasync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.nbt.JsonToNBT
import net.minecraft.nbt.NBTTagCompound

object NBTTagCompoundSerializer : KSerializer<NBTTagCompound> {
    override val descriptor = PrimitiveSerialDescriptor("NBTTagCompoundSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): NBTTagCompound = JsonToNBT.getTagFromJson(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: NBTTagCompound) {
        encoder.encodeString(value.toString())
    }
}
