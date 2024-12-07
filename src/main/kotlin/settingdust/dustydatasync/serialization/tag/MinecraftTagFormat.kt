package settingdust.dustydatasync.serialization.tag

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.serializer
import net.minecraft.nbt.NBTBase
import settingdust.dustydatasync.serialization.TagsModule
import settingdust.dustydatasync.serialization.tag.internal.readTag
import settingdust.dustydatasync.serialization.tag.internal.writeTag

/** Nbt minecraft tag types format */
@ExperimentalSerializationApi
sealed class MinecraftTag(
    val configuration: MinecraftTagConfiguration,
    serializersModule: SerializersModule
) : SerialFormat {
    companion object Default : MinecraftTag(MinecraftTagConfiguration(), EmptySerializersModule())

    internal class Impl(config: MinecraftTagConfiguration, serializersModule: SerializersModule) :
        MinecraftTag(config, serializersModule)

    override val serializersModule = serializersModule + TagsModule(configuration.classDiscriminator)

    fun <T> encodeToTag(serializer: SerializationStrategy<T>, value: T): NBTBase =
        writeTag(value, serializer)

    fun <T> decodeFromTag(deserializer: DeserializationStrategy<T>, element: NBTBase): T =
        readTag(element, deserializer)
}

data class MinecraftTagConfiguration(
    val encodeDefaults: Boolean = false,
    val ignoreUnknownKeys: Boolean = false,
    val classDiscriminator: String = "type"
)

@ExperimentalSerializationApi
inline fun MinecraftTag(
    from: MinecraftTag = MinecraftTag,
    build: MinecraftTagBuilder.() -> Unit
): MinecraftTag = MinecraftTagBuilder(from).apply(build).build()

@ExperimentalSerializationApi
class MinecraftTagBuilder(from: MinecraftTag) {
    var encodeDefaults = from.configuration.encodeDefaults
    var ignoreUnknownKeys = from.configuration.ignoreUnknownKeys

    var serializersModule = from.serializersModule

    fun build(): MinecraftTag =
        MinecraftTag.Impl(
            MinecraftTagConfiguration(encodeDefaults, ignoreUnknownKeys), serializersModule
        )
}

@ExperimentalSerializationApi
inline fun <reified T> MinecraftTag.encodeToTag(value: T): NBTBase =
    encodeToTag(serializersModule.serializer(), value)

@ExperimentalSerializationApi
inline fun <reified T> MinecraftTag.decodeFromTag(tag: NBTBase): T =
    decodeFromTag(serializersModule.serializer(), tag)
