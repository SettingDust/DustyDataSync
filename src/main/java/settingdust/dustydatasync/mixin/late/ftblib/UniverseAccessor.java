package settingdust.dustydatasync.mixin.late.ftblib;

import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.Universe;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Universe.class, remap = false)
public interface UniverseAccessor {
    @Accessor
    Short2ObjectOpenHashMap<ForgeTeam> getTeamMap();
}
