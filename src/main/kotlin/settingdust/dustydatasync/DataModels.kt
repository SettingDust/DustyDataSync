package settingdust.dustydatasync

import java.util.*
import net.minecraft.nbt.NBTTagCompound
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.json.json

sealed class PlayerNbtTable(name: String = "") : IdTable<UUID>(name) {
    final override val id = uuid("id").entityId()

    val lock = bool("lock").default(false)

    val data = json<NBTTagCompound>("data", json, NBTTagCompoundSerializer)

    final override val primaryKey = PrimaryKey(id)
}

sealed class PlayerNbtEntity(id: EntityID<UUID>, table: PlayerNbtTable) : UUIDEntity(id) {
    companion object : EntityClass<UUID, FTBQuestData>(FTBQuestTable)

    var lock by table.lock

    var data by table.data
}

sealed class PlayerNbtEntityClass<E : PlayerNbtEntity>(table: PlayerNbtTable) :
    EntityClass<UUID, E>(table)
