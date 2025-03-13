package settingdust.dustydatasync

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.darkhax.gamestages.data.IStageData
import net.minecraft.nbt.NBTTagCompound
import org.apache.logging.log4j.LogManager
import java.util.UUID
import java.util.function.Supplier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class SyncedGameStage(
    @SerialName("_id") val id: @Contextual Uuid? = null,
    val data: @Contextual NBTTagCompound? = null
) {
    companion object {
        const val COLLECTION = "game_stages"
    }
}

class ObservableStageData(val wrapped: IStageData, val onChange: IStageData.() -> Unit) : IStageData by wrapped {
    override fun addStage(p0: String) {
        wrapped.addStage(p0)
        onChange()
    }

    override fun removeStage(p0: String) {
        wrapped.removeStage(p0)
        onChange()
    }

    override fun clear() {
        wrapped.clear()
        onChange()
    }
}

@OptIn(ExperimentalUuidApi::class)
object GameStagesSyncer {
    @JvmStatic
    private val logger = LogManager.getLogger()

    @OptIn(ExperimentalUuidApi::class)
    val updates = MutableSharedFlow<Pair<Uuid, NBTTagCompound>>()

    init {
        DustyDataSync.scope.launch {
            updates.collect { (id, nbt) ->
                val collection = Database.database.getCollection<SyncedGameStage>(SyncedGameStage.COLLECTION)
                collection.updateOne(
                    Filters.eq("_id", id),
                    Updates.set(SyncedGameStage::data.name, nbt),
                    UpdateOptions().upsert(true)
                )
            }
        }
    }

    fun IStageData.observe(uuid: UUID) = ObservableStageData(this) {
        DustyDataSync.scope.launch { updates.emit(uuid.toKotlinUuid() to writeToNBT()) }
    }

    @OptIn(ExperimentalUuidApi::class, FlowPreview::class)
    fun getPlayerData(id: String, original: Supplier<NBTTagCompound?>) = runBlocking {
        val stageCollection = Database.database.getCollection<SyncedGameStage>(SyncedGameStage.COLLECTION)
        logger.debug("Getting player data for {}", id)
        val uuid = Uuid.parse(id)
        val stages = try {
            stageCollection.find(Filters.eq("_id", uuid))
                .projection(Projections.include(SyncedGameStage::data.name)).single().data
        } catch (_: NoSuchElementException) {
            null
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to find data for player $id", e)
        }
        logger.debug("Player {} stages: {}", uuid, stages)

        return@runBlocking if (stages == null || stages.isEmpty) original.get() else stages
    }

    @OptIn(ExperimentalUuidApi::class)
    fun savePlayerData(id: String, data: NBTTagCompound) = DustyDataSync.scope.launch {
        val stageCollection = Database.database.getCollection<SyncedGameStage>(SyncedGameStage.COLLECTION)
        val uuid = Uuid.parse(id)
        stageCollection.updateOne(
            Filters.eq("_id", uuid),
            Updates.set(SyncedGameStage::data.name, data),
            UpdateOptions().upsert(true)
        )
    }
}
