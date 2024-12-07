package settingdust.dustydatasync

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.Updates
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.client.model.changestream.OperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.NBTTagCompound
import sonar.fluxnetworks.api.network.IFluxNetwork
import sonar.fluxnetworks.api.utils.ICustomValue
import sonar.fluxnetworks.api.utils.NBTType
import sonar.fluxnetworks.common.connection.FluxNetworkBase
import sonar.fluxnetworks.common.connection.FluxNetworkCache
import sonar.fluxnetworks.common.connection.FluxNetworkServer
import sonar.fluxnetworks.common.data.FluxNetworkData
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class SyncedFluxNetwork(
    @SerialName("_id") val id: Int? = null,
    val data: @Contextual NBTTagCompound? = null
) {
    companion object {
        const val COLLECTION = "flux_networks"
    }
}

data class ObservableCustomValue<T>(val wrapped: ICustomValue<T>) : ICustomValue<T> by wrapped {
    var updates = MutableSharedFlow<Pair<T, T>>()

    override fun setValue(value: T) {
        if (wrapped.value != value && value != null) {
            val old = wrapped.value
            wrapped.value = value
            runBlocking { updates.emit(old to value) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
object FluxNetworksSyncer {
    val updates = MutableSharedFlow<IFluxNetwork>()

    @OptIn(FlowPreview::class)
    fun FluxNetworkBase.observeValues() = merge(
        (network_name as ObservableCustomValue<*>).updates,
        (network_owner as ObservableCustomValue<*>).updates,
        (network_security as ObservableCustomValue<*>).updates,
        (network_password as ObservableCustomValue<*>).updates,
        (network_color as ObservableCustomValue<*>).updates,
        (network_energy as ObservableCustomValue<*>).updates,
        (network_wireless as ObservableCustomValue<*>).updates,
        (network_stats as ObservableCustomValue<*>).updates,
        (network_players as ObservableCustomValue<*>).updates
    )
        .flowOn(Dispatchers.IO)
        .debounce(50.milliseconds)
        .onEach { updates.emit(this) }
        .launchIn(DustyDataSync.serverCoroutineScope)

    var syncing = false

    fun <T> FluxNetworkBase.emitUpdate(value: ObservableCustomValue<T>) = runBlocking {
        value.updates.emit(value.value to value.value)
    }

    init {
        val collection = Database.database.getCollection<SyncedFluxNetwork>(SyncedFluxNetwork.COLLECTION)
        updates
            .bufferTimeout(Int.MAX_VALUE, 100.milliseconds)
            .onEach { networks ->
                collection.bulkWrite(
                    networks.map { network ->
                        UpdateOneModel(
                            Filters.eq("_id", network.networkID),
                            Updates.set(SyncedFluxNetwork::data.name, network)
                        )
                    }
                )
            }.launchIn(DustyDataSync.scope)

        collection.watch()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                syncing = true
                when (document.operationType) {
                    OperationType.INSERT -> {
                        FluxNetworkData.get().addNetwork(FluxNetworkServer().also {
                            it.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_GENERAL)
                            it.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_PLAYERS)
                        })
                    }

                    OperationType.UPDATE -> {
                        val id = document.documentKey!!.getInt32("_id").value
                        val network = FluxNetworkCache.instance.getNetwork(id)
                        if (document.fullDocument == null) return@onEach
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_GENERAL)
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_PLAYERS)
                    }

                    OperationType.REPLACE -> {
                        val id = document.documentKey!!.getInt32("_id").value
                        val network = FluxNetworkCache.instance.getNetwork(id)
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_GENERAL)
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_PLAYERS)
                    }

                    OperationType.DELETE -> {
                        val id = document.documentKey!!.getInt32("_id").value
                        FluxNetworkData.get().removeNetwork(FluxNetworkCache.instance.getNetwork(id))
                    }

                    OperationType.DROP -> {
                        for (network in FluxNetworkData.get().networks.values) {
                            FluxNetworkData.get().removeNetwork(network)
                        }
                    }

                    else -> {}
                }
                syncing = false
            }
            .launchIn(DustyDataSync.scope)
    }

    fun addNetwork(network: IFluxNetwork) = runBlocking {
        if (syncing) return@runBlocking
        val collection = Database.database.getCollection<SyncedFluxNetwork>(SyncedFluxNetwork.COLLECTION)
        collection.replaceOne(
            Filters.eq("_id", network.networkID), SyncedFluxNetwork(network.networkID, network.toNbt()),
            ReplaceOptions().upsert(true)
        )
    }

    fun removeNetwork(network: IFluxNetwork) = runBlocking {
        if (syncing) return@runBlocking
        val collection = Database.database.getCollection<SyncedFluxNetwork>(SyncedFluxNetwork.COLLECTION)
        collection.deleteOne(Filters.eq("_id", network.networkID))
    }
}


fun IFluxNetwork.toNbt() =
    NBTTagCompound().also {
        try {
            FluxNetworkData.writePlayers(this, it)
            writeNetworkNBT(it, NBTType.NETWORK_GENERAL)
        } catch (_: Throwable) {
        }
    }