package me.zeroeightsix.kami.module.modules.combat;

import me.zeroeightsix.kami.module.Module;


import net.minecraft.item.ItemExpBottle;

@Module.Info(name= "FastXP", category = Module.Category.COMBAT, description = "Throws XP Bottles Faster")
public class FastXP extends Module {


    public void onUpdate() {
        if (mc.player.inventory.getCurrentItem().getItem() instanceof ItemExpBottle) {
            mc.rightClickDelayTimer = 0;
        }

    }


}