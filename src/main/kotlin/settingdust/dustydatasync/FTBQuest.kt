package settingdust.dustydatasync

import com.feed_the_beast.ftblib.lib.data.ForgeTeam
import com.feed_the_beast.ftblib.lib.data.Universe
import com.feed_the_beast.ftbquests.quest.QuestObjectBase
import com.feed_the_beast.ftbquests.quest.ServerQuestFile
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
import org.apache.logging.log4j.LogManager
import settingdust.dustydatasync.mixin.late.ftblib.ServerQuestDataAccessor
import java.util.*
import java.util.function.Supplier

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
    private val logger = LogManager.getLogger()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var questLoading = false
    var questUnloading = false

    init {
        Database.database.getCollection<SyncedFTBQuest>(SyncedFTBQuest.COLLECTION).watch<SyncedFTBQuest>()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                if (questUnloading) return@onEach
                questLoading = true
                when (document.operationType) {
                    OperationType.INSERT, OperationType.UPDATE, OperationType.REPLACE -> {
                        val fullDocument = document.fullDocument!!
                        val data = fullDocument.data ?: return@onEach
                        runBlocking(DustyDataSync.serverCoroutineDispatcher) {
                            val team = Universe.get().getTeam(fullDocument.id!!) ?: return@runBlocking
                            val questData = ServerQuestData.get(team)
                            questData.taskData.entries
                                .filter { (key, value) -> !data.hasKey(QuestObjectBase.getCodeString(key)) }
                                .forEach { (_, value) -> questData.createTaskData(value.task) }
                            for (key in data.keySet) {
                                val id = ServerQuestFile.INSTANCE.getID(key)
                                if (id in questData.taskData) continue
                                val task = ServerQuestFile.INSTANCE.getTask(id)
                                if (task == null) {
                                    logger.warn("Failed to find task with id {}", id)
                                    continue
                                }
                                questData.createTaskData(task)
                            }
                            questData.progressCache = null
                            questData.areDependenciesCompleteCache = null
                            (questData as ServerQuestDataAccessor).callReadData(data)
                            logger.debug("Synced update quest data for team {}. Data: {}", team.uid, data)
                        }
                    }

                    else -> {}
                }
                questLoading = false
            }.launchIn(scope)
    }

    fun ForgeTeam.load(original: Supplier<NBTTagCompound?>): NBTTagCompound? {
        questLoading = true
        val collection = Database.database.getCollection<SyncedFTBQuest>(SyncedFTBQuest.COLLECTION)
        val data = try {
            runBlocking { collection.find(Filters.eq("_id", uid)).single() }.data
        } catch (_: NoSuchElementException) {
            null
        } catch (e: Exception) {
            questLoading = false
            throw e
        }.also {
            logger.debug("Loaded quest data for team {}. Data: {}", uid, it)
            questLoading = false
        }
        return if (data == null || data.isEmpty) original.get() else data
    }

    fun ForgeTeam.save(nbt: NBTTagCompound) = runBlocking {
        if (questLoading || questUnloading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBQuest>(SyncedFTBQuest.COLLECTION)
        collection.updateOne(
            Filters.eq("_id", uid),
            Updates.set(SyncedFTBQuest::data.name, nbt),
            UpdateOptions().upsert(true)
        )
        logger.debug("Saved quest data for team {}. Data: {}", uid, nbt)
    }

    fun remove(team: ForgeTeam) = runBlocking {
        if (questLoading || questUnloading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBQuest>(SyncedFTBQuest.COLLECTION)
        collection.deleteOne(Filters.eq("_id", team.uid))
        logger.debug("Removed quest data for team {}", team.uid)
    }
}