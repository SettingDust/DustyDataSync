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
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.client.model.changestream.OperationType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.NBTTagCompound
import org.bson.types.ObjectId
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
object FTBLibSyncer {
    init {
        Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
            .watch()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                when (document.operationType) {
                    OperationType.INSERT -> {
                        val fullDocument = document.fullDocument!!
                        val player = ForgePlayer(
                            Universe.get(),
                            fullDocument.id!!.toJavaUuid(),
                            fullDocument.name!!
                        )
                        Universe.get().players[player.id] = player
                        player.deserializeNBT(fullDocument.data!!)
                        player.team = Universe.get().getTeam(fullDocument.data.getString("TeamID"))
                        ForgePlayerLoadedEvent(player).post()
                    }

                    OperationType.UPDATE, OperationType.REPLACE -> {
                        val fullDocument = document.fullDocument ?: return@onEach
                        val player = Universe.get().players[fullDocument.id!!.toJavaUuid()]!!
                        player.deserializeNBT(fullDocument.data!!)
                        player.team = Universe.get().getTeam(fullDocument.data.getString("TeamID"))
                    }

                    else -> {}
                }
            }
            .launchIn(DustyDataSync.scope)

        Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
            .watch()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                when (document.operationType) {
                    OperationType.INSERT -> {
                        val fullDocument = document.fullDocument!!
                        val team = ForgeTeam(
                            Universe.get(),
                            fullDocument.id!!,
                            fullDocument.stringId!!,
                            fullDocument.type!!
                        )
                        if (team.uid == 0.toShort()) team.markDirty()
                        Universe.get().addTeam(team)
                        if (team.type.save) {
                            team.deserializeNBT(fullDocument.data!!)
                            ForgeTeamLoadedEvent(team).post()
                        }
                    }

                    OperationType.UPDATE, OperationType.REPLACE -> {
                        val fullDocument = document.fullDocument ?: return@onEach
                        val team = Universe.get().getTeam(fullDocument.id!!)
                        team.type = fullDocument.type
                        if (team.type.save) {
                            team.deserializeNBT(fullDocument.data!!)
                        }
                    }

                    else -> {}
                }
            }
            .launchIn(DustyDataSync.scope)
    }

    fun Universe.loadUniverse() = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBUniverse>(SyncedFTBUniverse.COLLECTION)
        try {
            collection.find().single()
        } catch (_: NoSuchElementException) {
            null
        } catch (e: IllegalStateException) {
            throw e
        }?.data
    }

    @OptIn(ExperimentalUuidApi::class)
    fun Universe.loadPlayers(nbtMap: MutableMap<UUID, NBTTagCompound>) = runBlocking {
        Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION).find()
            .collect {
                val id = it.id!!.toJavaUuid()
                players[id] = ForgePlayer(this@loadPlayers, id, it.name!!)
                nbtMap[id] = it.data!!
            }
    }

    fun Universe.loadTeams(nbtMap: MutableMap<String, NBTTagCompound>) = runBlocking {
        Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION).find()
            .collect {
                val id = it.id!!
                val team = ForgeTeam(this@loadTeams, generateTeamUID(id), it.stringId!!, it.type!!)
                addTeam(team)
                if (id == 0.toShort()) team.markDirty()
                nbtMap[it.stringId] = it.data!!
            }
    }

    fun Universe.saveUniverse(data: NBTTagCompound) = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBUniverse>(SyncedFTBUniverse.COLLECTION)
        collection.updateOne(
            Filters.empty(),
            Updates.set(SyncedFTBUniverse::data.name, data)
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    fun Universe.savePlayers() = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
        collection.bulkWrite(
            players.values.map {
                UpdateOneModel(
                    Filters.eq("_id", it.id.toKotlinUuid()),
                    Updates.combine(
                        Updates.set(SyncedFTBPlayer::name.name, it.name),
                        Updates.set(SyncedFTBPlayer::data.name, it.serializeNBT().also { compound ->
                            compound.setString("TeamID", it.team.id)
                        })
                    ),
                    UpdateOptions().upsert(true)
                )
            }
        )
        for (player in players.values) {
            ForgePlayerSavedEvent(player).post()
            player.needsSaving = false
        }
    }

    fun Universe.saveTeams() = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        collection.bulkWrite(
            teams.filter { team -> team.type.save }.map {
                UpdateOneModel(
                    Filters.eq("_id", it.uid),
                    Updates.combine(
                        Updates.set("_id", it.uid),
                        Updates.set(SyncedFTBTeam::stringId.name, it.id),
                        Updates.set(SyncedFTBTeam::type.name, it.type),
                        Updates.set(SyncedFTBTeam::data.name, it.serializeNBT())
                    )
                )
            }
        )
        for (team in teams) {
            ForgeTeamSavedEvent(team).post()
            team.needsSaving = false
        }
    }
}