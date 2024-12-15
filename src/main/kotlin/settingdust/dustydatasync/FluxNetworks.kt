package settingdust.dustydatasync

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.nbt.NBTTagCompound
import sonar.fluxnetworks.api.network.IFluxNetwork
import sonar.fluxnetworks.api.network.NetworkSettings
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
    fun FluxNetworkBase.observeValues() = if (DustyDataSync.isServerScopeInitialized()) {
        merge(
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
    } else Unit

    var loading = false

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
                            Updates.set(SyncedFluxNetwork::data.name, network.toNbt()),
                            UpdateOptions().upsert(true)
                        )
                    }
                )
            }.launchIn(DustyDataSync.scope)

        collection.watch()
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .onEach { document ->
                loading = true
                when (document.operationType) {
                    OperationType.INSERT -> {
                        val network = FluxNetworkServer().also {
                            it.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_GENERAL)
                            it.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_PLAYERS)
                        }

                        DustyDataSync.serverCoroutineScope.launch {
                            FluxNetworkData.get().addNetwork(network)
                        }
                    }

                    OperationType.UPDATE -> {
                        val id = document.documentKey!!.getInt32("_id").value
                        val network = FluxNetworkCache.instance.getNetwork(id)
                        if (document.fullDocument == null) return@onEach
                        network.getSetting(NetworkSettings.NETWORK_PLAYERS).clear()
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_GENERAL)
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_PLAYERS)
                    }

                    OperationType.REPLACE -> {
                        val id = document.documentKey!!.getInt32("_id").value
                        val network = FluxNetworkCache.instance.getNetwork(id)
                        network.getSetting(NetworkSettings.NETWORK_PLAYERS).clear()
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_GENERAL)
                        network.readNetworkNBT(document.fullDocument!!.data, NBTType.NETWORK_PLAYERS)
                    }

                    OperationType.DELETE -> {
                        val id = document.documentKey!!.getInt32("_id").value
                        DustyDataSync.serverCoroutineScope.launch {
                            if (id in FluxNetworkData.get().networks) {
                                FluxNetworkData.get().removeNetwork(FluxNetworkCache.instance.getNetwork(id))
                            }
                        }
                    }

                    OperationType.DROP -> {
                        DustyDataSync.serverCoroutineScope.launch {
                            for (network in FluxNetworkData.get().networks.values) {
                                FluxNetworkData.get().removeNetwork(network)
                            }
                        }
                    }

                    else -> {}
                }
                loading = false
            }
            .launchIn(DustyDataSync.scope)
    }

    fun FluxNetworkData.loadNetworks() = runBlocking {
        val collection = Database.database.getCollection<SyncedFluxNetwork>(SyncedFluxNetwork.COLLECTION)
        loading = true
        collection.find().collect { synced ->
            if (synced.data == null || synced.data.isEmpty) return@collect
            val network = networks.getOrDefault(synced.id!!, FluxNetworkServer())
            network.readNetworkNBT(synced.data, NBTType.NETWORK_GENERAL)
            network.readNetworkNBT(synced.data, NBTType.NETWORK_PLAYERS)
            addNetwork(network)
        }
        loading = false
    }

    fun addNetwork(network: IFluxNetwork) = runBlocking {
        if (loading) return@runBlocking
        val collection = Database.database.getCollection<SyncedFluxNetwork>(SyncedFluxNetwork.COLLECTION)
        collection.updateOne(
            Filters.eq("_id", network.networkID),
            Updates.set(SyncedFluxNetwork::data.name, network.toNbt()),
            UpdateOptions().upsert(true)
        )
    }

    fun removeNetwork(network: IFluxNetwork) = runBlocking {
        if (loading) return@runBlocking
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