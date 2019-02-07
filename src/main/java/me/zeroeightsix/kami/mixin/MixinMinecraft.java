package me.zeroeightsix.kami.mixin;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.GuiScreenEvent;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Created by 086 on 17/11/2017.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    WorldClient world;
    @Shadow
    EntityPlayerSP player;
    @Shadow
    GuiScreen currentScreen;
    @Shadow
    GameSettings gameSettings;
    @Shadow
    GuiIngame ingameGUI;
    @Shadow
    boolean skipRenderWorld;
    @Shadow
    SoundHandler soundHandler;

    @Shadow
    public void displayGuiScreen(@Nullable GuiScreen p_displayGuiScreen_1_) {
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    public void displayGuiScreen(GuiScreen guiScreenIn, CallbackInfo info) {
        info.cancel();
        GuiScreenEvent.Closed screenEvent = new GuiScreenEvent.Closed(Wrapper.getMinecraft().currentScreen);
        KamiMod.EVENT_BUS.post(screenEvent);
        if (screenEvent.isCancelled()) {
            return;
        }
        GuiScreenEvent.Displayed screenEvent1 = new GuiScreenEvent.Displayed(guiScreenIn);
        KamiMod.EVENT_BUS.post(screenEvent1);
        guiScreenIn = screenEvent1.getScreen();
        displayGuiScreen(guiScreenIn);
    }

    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayCrashReport(Lnet/minecraft/crash/CrashReport;)V"))
    public void displayCrashReport(Minecraft minecraft, CrashReport crashReport) {
        save();
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    public void shutdown(CallbackInfo info) {
        save();
    }

    private void save() {
        System.out.println("Shutting down: saving KAMI configuration");
        KamiMod.saveConfiguration();
        System.out.println("Configuration saved.");
    }

}
