package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.mixin.MixinNetworkManager;
import me.zeroeightsix.kami.module.Module;

/**
 * @author 086
 * @see MixinNetworkManager
 */
@Module.Info(name = "NoPacketKick", category = Module.Category.MISC, description = "Prevent large packets from kicking you")
public class NoPacketKick {
    private static NoPacketKick INSTANCE;

    public NoPacketKick() {
        INSTANCE = this;
    }

    public static boolean isEnabled() {
        return INSTANCE.isEnabled();
    }

}
