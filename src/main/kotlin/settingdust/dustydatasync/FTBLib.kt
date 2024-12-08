package settingdust.dustydatasync

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoadedEvent
import com.feed_the_beast.ftblib.events.player.ForgePlayerSavedEvent
import com.feed_the_beast.ftblib.events.team.ForgeTeamDeletedEvent
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.bson.types.ObjectId
import settingdust.dustydatasync.FTBLibSyncer.loadTeams
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
    private var loading = false

    init {
        Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
            .watch()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                loading = true
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
                loading = false
            }
            .launchIn(DustyDataSync.scope)

        Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
            .watch()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                loading = true
                when (document.operationType) {
                    OperationType.INSERT -> {
                        val fullDocument = document.fullDocument!!
                        val team = ForgeTeam(
                            Universe.get(),
                            fullDocument.id!!,
                            fullDocument.stringId!!,
                            fullDocument.type!!
                        )
                        Universe.get().addTeam(team)
                        if (team.type.save) {
                            team.deserializeNBT(fullDocument.data!!)
                            ForgeTeamLoadedEvent(team).post()
                        }
                        if (team.uid == 0.toShort()) team.markDirty()
                    }

                    OperationType.UPDATE, OperationType.REPLACE -> {
                        val fullDocument = document.fullDocument ?: return@onEach
                        val team = Universe.get().getTeam(fullDocument.id!!)
                        (team as ForgeTeamAccessor).setType(fullDocument.type)
                        if (team.type.save) {
                            team.deserializeNBT(fullDocument.data!!)
                        }
                        if (team.uid == 0.toShort()) team.markDirty()
                    }

                    OperationType.DELETE -> {
                        val id = document.documentKey!!["_id"]!!.asInt32().value.toShort()
                        val team = Universe.get().getTeam(id)
                        Universe.get().removeTeam(team)
                    }

                    else -> {}
                }
                loading = false
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
        loading = true
        Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION).find().collect {
            val id = it.id!!.toJavaUuid()
            if (id !in players) {
                players[id] = ForgePlayer(this@loadPlayers, id, it.name!!)
            }
            nbtMap[id] = it.data!!
        }
        loading = false
    }

    fun Universe.loadTeams(nbtMap: MutableMap<String, NBTTagCompound>) = runBlocking {
        loading = true
        Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION).find()
            .collect {
                val id = it.id!!
                if (id !in (this@loadTeams as UniverseAccessor).teamMap) {
                    addTeam(ForgeTeam(this@loadTeams, id, it.stringId!!, it.type!!))
                }
                nbtMap[it.stringId!!] = it.data!!
            }
        loading = false
    }

    fun Universe.saveUniverse(data: NBTTagCompound) = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBUniverse>(SyncedFTBUniverse.COLLECTION)
        collection.updateOne(
            Filters.empty(),
            Updates.set(SyncedFTBUniverse::data.name, data),
            UpdateOptions().upsert(true)
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    fun Universe.savePlayers() = runBlocking {
        if (players.isEmpty()) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
        players.values.filter { it.needsSaving }.takeIf { it.isNotEmpty() }?.let {
            collection.bulkWrite(
                it.map {
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

            for (player in it) {
                ForgePlayerSavedEvent(player).post()
                player.needsSaving = false
            }
        }
    }

    fun ForgePlayer.savePlayer() = runBlocking {
        if (loading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBPlayer>(SyncedFTBPlayer.COLLECTION)
        collection.updateOne(
            Filters.eq("_id", id.toKotlinUuid()),
            Updates.combine(
                Updates.set(SyncedFTBPlayer::name.name, name),
                Updates.set(SyncedFTBPlayer::data.name, serializeNBT().also { compound ->
                    compound.setString("TeamID", team.id)
                })
            )
        )
        ForgePlayerSavedEvent(this@savePlayer).post()
    }

    fun Universe.saveTeams() = runBlocking {
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        teams.filter { team -> team.type.save }.takeIf { it.isNotEmpty() }?.let { teams ->
            collection.bulkWrite(
                teams.map {
                    UpdateOneModel(
                        Filters.eq("_id", it.uid),
                        Updates.combine(
                            Updates.set(SyncedFTBTeam::stringId.name, it.id),
                            Updates.set(SyncedFTBTeam::type.name, it.type),
                            Updates.set(SyncedFTBTeam::data.name, it.serializeNBT())
                        ),
                        UpdateOptions().upsert(true)
                    )
                }
            )
        }

        for (team in teams) {
            ForgeTeamSavedEvent(team).post()
            team.needsSaving = false
        }
    }

    fun ForgeTeam.saveTeam() = runBlocking {
        if (loading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        collection.updateOne(
            Filters.eq("_id", uid),
            Updates.combine(
                Updates.set(SyncedFTBTeam::stringId.name, id),
                Updates.set(SyncedFTBTeam::type.name, type),
                Updates.set(SyncedFTBTeam::data.name, serializeNBT())
            ),
            UpdateOptions().upsert(true)
        )
        ForgeTeamSavedEvent(this@saveTeam).post()
    }

    fun ForgeTeam.addTeam() = runBlocking {
        if (loading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        collection.replaceOne(
            Filters.eq("_id", uid),
            SyncedFTBTeam(uid, id, type, serializeNBT()),
            ReplaceOptions().upsert(true)
        )
    }

    fun ForgeTeam.removeTeam() = runBlocking {
        if (loading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFTBTeam>(SyncedFTBTeam.COLLECTION)
        collection.deleteOne(Filters.eq("_id", uid))
    }
}