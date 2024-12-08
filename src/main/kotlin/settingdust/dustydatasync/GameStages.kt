package settingdust.dustydatasync

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.NBTTagCompound
import org.apache.logging.log4j.LogManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

object GameStagesSyncer {
    @JvmStatic
    private val logger = LogManager.getLogger()

    @OptIn(ExperimentalUuidApi::class, FlowPreview::class)
    fun getPlayerData(id: String) = runBlocking {
        val stageCollection = Database.database.getCollection<SyncedGameStage>(SyncedGameStage.COLLECTION)
        logger.debug("Getting player data for {}", id)
        val uuid = Uuid.parse(id)
        val stages = try {
            stageCollection.find(Filters.eq("_id", uuid))
                .projection(Projections.include(SyncedGameStage::data.name)).single().data
        } catch (_: NoSuchElementException) {
            NBTTagCompound()
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to find data for player $id", e)
        }!!
        logger.debug("Player {} stages: {}", uuid, stages)

        return@runBlocking stages
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
