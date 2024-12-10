package settingdust.dustydatasync

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoadedEvent
import com.feed_the_beast.ftblib.events.player.ForgePlayerSavedEvent
import com.feed_the_beast.ftblib.events.team.ForgeTeamLoadedEvent
import com.feed_the_beast.ftblib.events.team.ForgeTeamSavedEvent
import com.feed_the_beast.ftblib.lib.data.ForgePlayer
import com.feed_the_beast.ftblib.lib.data.ForgeTeam
import com.feed_the_beast.ftblib.lib.data.TeamType
import com.feed_the_beast.ftblib.lib.data.Universe
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.client.model.changestream.OperationType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager
import org.bson.types.ObjectId
import settingdust.dustydatasync.mixin.late.ftblib.ForgeTeamAccessor
import settingdust.dustydatasync.mixin.late.ftblib.UniverseAccessor
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Serializable
data class SyncedFTBUniverse(
    @SerialName("_id") val id: @Contextual ObjectId = ObjectId.get(),
    val data: @Contextual NBTTagCompound? = null
) {
    companion object {
        const val COLLECTION = "ftb_universe"
    }
}

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class SyncedFTBPlayer(
    @SerialName("_id") val id: @Contextual Uuid? = null,
    val name: String? = null,
    val data: @Contextual NBTTagCompound? = null
) {
    companion object {
        const val COLLECTION = "ftb_players"
    }
}

@Serializable
data class SyncedFTBTeam(
    @SerialName("_id") val id: Short? = null,
    val stringId: String? = null,
    val type: TeamType? = null,
    val data: @Contextual NBTTagCompound? = null
) {
    companion object {
        const val COLLECTION = "ftb_lib_teams"
    }
}

@OptIn(ExperimentalUuidApi::class)
@Mod.EventBusSubscriber
object FTBLibSyncer {
    private val logger = LogManager.getLogger()
    var playerLoading = false
    var teamLoading = false

    init {
        Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
            .watch()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                playerLoading = true
                when (document.operationType) {
                    OperationType.INSERT -> {
                        val fullDocument = document.fullDocument!!
                        val player = ForgePlayer(
                            Universe.get(),
                            fullDocument.id!!.toJavaUuid(),
                            fullDocument.name!!
                        )
                        player.deserializeNBT(fullDocument.data!!)
                        player.team = Universe.get().getTeam(fullDocument.data.getString("TeamID"))
                        ForgePlayerLoadedEvent(player).post()
                        runBlocking(DustyDataSync.serverCoroutineDispatcher) {
                            Universe.get().players[player.id] = player
                        }
                        logger.debug(
                            "Synced adding player {} with id {}. Data: {}",
                            fullDocument.name,
                            fullDocument.id,
                            fullDocument.data
                        )
                    }

                    OperationType.UPDATE, OperationType.REPLACE -> {
                        val fullDocument = document.fullDocument ?: return@onEach
                        runBlocking(DustyDataSync.serverCoroutineDispatcher) {
                            val player = Universe.get().players[fullDocument.id!!.toJavaUuid()]!!
                            player.deserializeNBT(fullDocument.data!!)
                            player.team = Universe.get().getTeam(fullDocument.data.getString("TeamID"))
                        }
                        logger.debug(
                            "Synced updating player {} with id {}. Data: {}",
                            fullDocument.name,
                            fullDocument.id,
                            fullDocument.data
                        )
                    }

                    else -> {}
                }
                playerLoading = false
            }
            .launchIn(DustyDataSync.scope)

        DustyDataSync.scope.launch {
            Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
                .watch()
                .fullDocument(FullDocument.UPDATE_LOOKUP)
                .collect { document ->
                    teamLoading = true
                    when (document.operationType) {
                        OperationType.INSERT -> {
                            val fullDocument = document.fullDocument!!
                            val team = ForgeTeam(
                                Universe.get(),
                                fullDocument.id!!,
                                fullDocument.stringId!!,
                                fullDocument.type!!
                            )
                            runBlocking(DustyDataSync.serverCoroutineDispatcher) {
                                Universe.get().addTeam(team)
                                if (team.type.save) {
                                    team.deserializeNBT(fullDocument.data!!)
                                    ForgeTeamLoadedEvent(team).post()
                                }
                            }
                            if (team.uid == 0.toShort()) team.markDirty()
                            logger.debug(
                                "Synced adding team {} with id {}. Data: {}",
                                fullDocument.stringId,
                                fullDocument.id,
                                fullDocument.data
                            )
                        }

                        OperationType.UPDATE, OperationType.REPLACE -> {
                            val fullDocument = document.fullDocument ?: return@collect
                            val team = runBlocking(DustyDataSync.serverCoroutineDispatcher) {
                                val team = Universe.get().getTeam(fullDocument.id!!)
                                (team as ForgeTeamAccessor).setType(fullDocument.type)
                                if (team.type.save) {
                                    team.deserializeNBT(fullDocument.data!!)
                                }
                                team
                            }
                            if (team.uid == 0.toShort()) team.markDirty()
                            logger.debug(
                                "Synced updating team {} with id {}. Data: {}",
                                fullDocument.stringId,
                                fullDocument.id,
                                fullDocument.data
                            )
                        }

                        OperationType.DELETE -> {
                            val id = document.documentKey!!["_id"]!!.asInt32().value.toShort()
                            val team = runBlocking(DustyDataSync.serverCoroutineDispatcher) {
                                val team = Universe.get().getTeam(id)
                                Universe.get().removeTeam(team)
                                team
                            }
                            logger.debug("Synced removing team {} with id {}", team.id, id)
                        }

                        else -> {}
                    }
                    teamLoading = false
                }
        }
    }

    fun Universe.loadUniverse() = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBUniverse>(SyncedFTBUniverse.COLLECTION)
        try {
            collection.find().single()
        } catch (_: NoSuchElementException) {
            null
        } catch (e: IllegalStateException) {
            throw e
        }?.data.also { logger.debug("Loaded universe data: {}", it) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun Universe.loadPlayers(nbtMap: MutableMap<UUID, NBTTagCompound>) = runBlocking {
        playerLoading = true
        Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION).find().collect {
            val id = it.id!!.toJavaUuid()
            if (id !in players) {
                players[id] = ForgePlayer(this@loadPlayers, id, it.name!!)
            }
            if (!it.data!!.isEmpty) {
                nbtMap[id] = it.data
                logger.debug("Bulk loaded player {} with id {}. Data: {}", it.name, it.id, it.data)
            }
        }
        playerLoading = false
    }

    fun Universe.loadTeams(nbtMap: MutableMap<String, NBTTagCompound>) = runBlocking {
        teamLoading = true
        Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION).find()
            .collect {
                val id = it.id!!
                if (id !in (this@loadTeams as UniverseAccessor).teamMap) {
                    addTeam(ForgeTeam(this@loadTeams, id, it.stringId!!, it.type!!))
                }
                if (!it.data!!.isEmpty) {
                    nbtMap[it.stringId!!] = it.data
                    logger.debug("Bulk loaded team {} with id {}. Data: {}", it.stringId, it.id, it.data)
                }
            }
        teamLoading = false
    }

    fun Universe.saveUniverse(data: NBTTagCompound) = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBUniverse>(SyncedFTBUniverse.COLLECTION)
        collection.updateOne(
            Filters.empty(),
            Updates.set(SyncedFTBUniverse::data.name, data),
            UpdateOptions().upsert(true)
        )
        logger.debug("Saved universe data: {}", data)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun Universe.savePlayers() = runBlocking {
        if (players.isEmpty()) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
        players.values.filter { it.needsSaving }.takeIf { it.isNotEmpty() }?.let {
            collection.bulkWrite(
                it.map { player ->
                    val nbt = player.serializeNBT().also { compound ->
                        compound.setString("TeamID", player.team.id)
                    }
                    UpdateOneModel<SyncedFTBPlayer>(
                        Filters.eq("_id", player.id.toKotlinUuid()),
                        Updates.combine(
                            Updates.set(SyncedFTBPlayer::name.name, player.name),
                            Updates.set(SyncedFTBPlayer::data.name, nbt)
                        ),
                        UpdateOptions().upsert(true)
                    ).also {
                        logger.debug("Bulk save player {} with id {}. Data: {}", player.name, player.id, nbt)
                    }
                }
            )

            for (player in it) {
                ForgePlayerSavedEvent(player).post()
                player.needsSaving = false
            }
        }
    }

    fun ForgePlayer.savePlayer() = runBlocking {
        if (playerLoading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
        val data = serializeNBT().also { compound -> compound.setString("TeamID", team.id) }
        collection.updateOne(
            Filters.eq("_id", id.toKotlinUuid()),
            Updates.combine(
                Updates.set(SyncedFTBPlayer::name.name, name),
                Updates.set(SyncedFTBPlayer::data.name, data)
            ),
            UpdateOptions().upsert(true)
        )
        ForgePlayerSavedEvent(this@savePlayer).post()
        logger.debug("Saved player {} with id {}. Data: {}", name, id, data)
    }

    fun Universe.saveTeams() = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        teams.filter { team -> team.type.save }.takeIf { it.isNotEmpty() }?.let { teams ->
            collection.bulkWrite(
                teams.map { team ->
                    val data = team.serializeNBT()
                    UpdateOneModel<SyncedFTBTeam>(
                        Filters.eq("_id", team.uid),
                        Updates.combine(
                            Updates.set(SyncedFTBTeam::stringId.name, team.id),
                            Updates.set(SyncedFTBTeam::type.name, team.type),
                            Updates.set(SyncedFTBTeam::data.name, data)
                        ),
                        UpdateOptions().upsert(true)
                    ).also {
                        logger.debug("Bulk save team {} with id {}. Data: {}", team.id, team.uid, data)
                    }
                }
            )
        }

        for (team in teams) {
            ForgeTeamSavedEvent(team).post()
            team.needsSaving = false
        }
    }

    fun ForgeTeam.saveTeam() = runBlocking {
        if (teamLoading || !isValid || !type.save || uid !in (universe as UniverseAccessor).teamMap) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        val data = serializeNBT()
        collection.updateOne(
            Filters.eq("_id", uid),
            Updates.combine(
                Updates.set(SyncedFTBTeam::stringId.name, id),
                Updates.set(SyncedFTBTeam::type.name, type),
                Updates.set(SyncedFTBTeam::data.name, data)
            ),
            UpdateOptions().upsert(true)
        )
        ForgeTeamSavedEvent(this@saveTeam).post()
        logger.debug("Saved team {} with id {}. Data: {}", id, uid, data)
    }

    fun ForgeTeam.addTeam() = runBlocking {
        if (teamLoading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        val data = serializeNBT()
        collection.replaceOne(
            Filters.eq("_id", uid),
            SyncedFTBTeam(uid, id, type, data),
            ReplaceOptions().upsert(true)
        )
        logger.debug("Added team {} with id {}. Data: {}", id, uid, data)
    }

    fun ForgeTeam.removeTeam() = runBlocking {
        if (teamLoading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        collection.deleteOne(Filters.eq("_id", uid))
        logger.debug("Removed team {} with id {}", id, uid)
        if (DustyDataSync.hasFTBQuests) {
            FTBQuestSyncer.remove(this@removeTeam)
        }
    }
}