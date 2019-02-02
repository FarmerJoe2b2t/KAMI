package me.zeroeightsix.kami;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class KamiTweaker implements ITweaker {

    private List<String> args;

    private void addArg(String name, String value) {
        args.add(name);
        if (value != null) {
            args.add(value);
        }
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.args = new ArrayList<>(args);

        addArg("--version", profile);
        addArg("--assetsDir", assetsDir.getPath());
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        KamiMod.log.info("Initiating mixins!");
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        KamiMod.log.info("Mixins initiated.");
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments() {
        return args.toArray(new String[0]);
    }
}
