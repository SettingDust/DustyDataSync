package settingdust.dustydatasync.mixin.late.ftblib;

import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.TeamType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ForgeTeam.class, remap = false)
public interface ForgeTeamAccessor {
    @Accessor
    @Mutable
    void setType(TeamType type);
}
