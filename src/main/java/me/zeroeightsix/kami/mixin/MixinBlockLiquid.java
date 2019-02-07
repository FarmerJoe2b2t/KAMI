package me.zeroeightsix.kami.mixin;

import me.zeroeightsix.kami.module.ModuleManager;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Created by 086 on 16/12/2017.
 */
@Mixin(FlowingFluid.class)
public class MixinFlowingFluid {

    // TODO: Find new method
    @Inject(method = "modifyAcceleration", at = @At("HEAD"), cancellable = true)
    public void modifyAcceleration(World worldIn, BlockPos pos, Entity entityIn, Vec3d motion, CallbackInfoReturnable returnable) {
        if (ModuleManager.isModuleEnabled("Velocity")) {
            returnable.setReturnValue(motion);
            returnable.cancel();
        }
    }

}
