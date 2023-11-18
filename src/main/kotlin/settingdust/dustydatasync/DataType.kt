package settingdust.dustydatasync

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent
import com.feed_the_beast.ftblib.lib.data.Universe
import com.feed_the_beast.ftbquests.util.ServerQuestData
import net.darkhax.gamestages.data.GameStageSaveHandler
import net.minecraft.nbt.NBTTagCompound
import settingdust.dustydatasync.mixin.ftbquests.ServerQuestDataAccessor
import java.util.*

sealed interface DataType {
    fun restoreNBT(nbt: NBTTagCompound, player: UUID)
    fun storeNBT(nbt: NBTTagCompound, player: UUID)

    data object FTBQuest : DataType {
        override fun restoreNBT(nbt: NBTTagCompound, player: UUID) {
            val forgePlayer = Universe.get().getPlayer(player)
            val team = forgePlayer?.team ?: return
            val data = ServerQuestData.get(team)
            (data as ServerQuestDataAccessor).`dustydatasync$readData`(nbt)
            data.markDirty()
            ForgePlayerLoggedInEvent(forgePlayer).post()
        }

        override fun storeNBT(nbt: NBTTagCompound, player: UUID) {
            (ServerQuestData.get(
                Universe.get().getPlayer(player)!!.team
            ) as ServerQuestDataAccessor).`dustydatasync$writeData`(nbt)
        }
    }

    data object GameStages : DataType {
        override fun restoreNBT(nbt: NBTTagCompound, player: UUID) {
            val stageData = GameStageSaveHandler.getPlayerData(player)
            stageData.readFromNBT(nbt)
        }

        override fun storeNBT(nbt: NBTTagCompound, player: UUID) {
            val stageData = GameStageSaveHandler.getPlayerData(player)
            nbt.keySet.clear()
            nbt.merge(stageData.writeToNBT())
        }
    }
}
