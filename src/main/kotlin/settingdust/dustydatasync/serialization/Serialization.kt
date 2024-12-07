package settingdust.dustydatasync.serialization

import com.google.common.collect.Multimaps
import com.google.common.collect.SetMultimap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.descriptors.setSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import kotlin.collections.iterator

object ResourceLocationSerializer : KSerializer<ResourceLocation> {
    override val descriptor = PrimitiveSerialDescriptor("ResourceLocation", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ResourceLocation {
        return ResourceLocation(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: ResourceLocation) {
        encoder.encodeString(value.toString())
    }
}

object BlockPosSerializer : KSerializer<BlockPos> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("BlockPos") {
            element("x", Int.serializer().descriptor)
            element("y", Int.serializer().descriptor)
            element("z", Int.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): BlockPos {
        return decoder.decodeStructure(descriptor) {
            var x = 0
            var y = 0
            var z = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> x = decodeIntElement(descriptor, index)
                    1 -> y = decodeIntElement(descriptor, index)
                    2 -> z = decodeIntElement(descriptor, index)
                    else -> throw IllegalArgumentException("Unexpected index: $index")
                }
            }
            BlockPos(x, y, z)
        }
    }

    override fun serialize(encoder: Encoder, value: BlockPos) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.x)
            encodeIntElement(descriptor, 1, value.y)
            encodeIntElement(descriptor, 2, value.z)
        }
    }
}

@Suppress("UnstableApiUsage")
@OptIn(ExperimentalSerializationApi::class)
class MultimapSerializer<K, V>(
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<Set<V>>,
    private val valueFactory: () -> MutableSet<V> = { ObjectOpenHashSet() }
) : KSerializer<SetMultimap<K, V>> {
    override val descriptor =
        SerialDescriptor(
            "Multimap",
            mapSerialDescriptor(
                keySerializer.descriptor, setSerialDescriptor(valueSerializer.descriptor)
            )
        )

    override fun serialize(encoder: Encoder, value: SetMultimap<K, V>) {
        MapSerializer(keySerializer, valueSerializer).serialize(encoder, Multimaps.asMap(value))
    }

    override fun deserialize(decoder: Decoder): SetMultimap<K, V> {
        val map = Multimaps.newSetMultimap<K, V>(Object2ObjectOpenHashMap(), valueFactory)
        for (entry in MapSerializer(keySerializer, valueSerializer).deserialize(decoder)) {
            map.putAll(entry.key, entry.value)
        }
        return map
    }
}

object ChunkPosAsStringSerializer : KSerializer<ChunkPos> {
    override val descriptor = PrimitiveSerialDescriptor("ChunkPos", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder) = decoder.decodeString().let {
        val strings = it.split(' ')
        ChunkPos(strings[0].toInt(), strings[1].toInt())
    }

    override fun serialize(encoder: Encoder, value: ChunkPos) = encoder.encodeString("${value.x} ${value.z}")
}

object ChunkPosAsLongSerializer : KSerializer<ChunkPos> {
    override val descriptor = PrimitiveSerialDescriptor("ChunkPos", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder) = decoder.decodeLong().let {
        ChunkPos((it and 4294967295L).toInt(), (it ushr 32 and 4294967295L).toInt())
    }

    override fun serialize(encoder: Encoder, value: ChunkPos) = encoder.encodeLong(ChunkPos.asLong(value.x, value.z))
}