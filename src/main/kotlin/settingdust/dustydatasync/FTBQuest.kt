package settingdust.dustydatasync

import com.feed_the_beast.ftblib.lib.data.ForgeTeam
import com.feed_the_beast.ftblib.lib.data.Universe
import com.feed_the_beast.ftbquests.util.ServerQuestData
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.client.model.changestream.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.NBTTagCompound
import settingdust.dustydatasync.mixin.late.ftblib.ServerQuestDataAccessor

@Serializable
data class SyncedFTBQuest(
    @SerialName("_id") val id: Short? = null,
    val data: @Contextual NBTTagCompound? = null
) {
    companion object {
        const val COLLECTION = "ftb_lib_quests"
    }
}

object FTBQuestSyncer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Database.database.getCollection<SyncedFTBQuest>(SyncedFTBQuest.COLLECTION).watch<SyncedFTBQuest>()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                when (document.operationType) {
                    OperationType.INSERT, OperationType.UPDATE, OperationType.REPLACE -> {
                        val fullDocument = document.fullDocument!!
                        val data = fullDocument.data
                        if (data != null) {
                            val team = Universe.get().getTeam(fullDocument.id!!)
                            val questData = ServerQuestData.get(team)
                            questData.taskData.clear()
                            questData.progressCache = null
                            questData.areDependenciesCompleteCache = null

                            (questData as ServerQuestDataAccessor).callReadData(data)
                        }
                    }

                    else -> {}
                }
            }.launchIn(scope)
    }

    fun ForgeTeam.load() = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBQuest>(SyncedFTBQuest.COLLECTION)
        return@runBlocking try {
            collection.find(Filters.eq("_id", uid)).single().data
        } catch (e: NoSuchElementException) {
            null
        } catch (e: Exception) {
            throw e
        }
    }

    fun ForgeTeam.save(nbt: NBTTagCompound) = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBQuest>(SyncedFTBQuest.COLLECTION)
        collection.updateOne(
            Filters.eq("_id", uid),
            Updates.set(SyncedFTBQuest::data.name, nbt),
            UpdateOptions().upsert(true)
        )
    }
}