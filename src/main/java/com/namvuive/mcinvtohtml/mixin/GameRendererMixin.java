package com.namvuive.mcinvtohtml.mixin;

import com.namvuive.mcinvtohtml.export.ExportManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void mcinvtohtml$afterFrame(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
		ExportManager.onFrameRendered();
	}
}
