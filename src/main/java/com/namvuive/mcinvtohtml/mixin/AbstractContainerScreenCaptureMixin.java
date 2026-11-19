package com.namvuive.mcinvtohtml.mixin;

import com.namvuive.mcinvtohtml.export.ExportManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenCaptureMixin {
	/** Hide the hover tooltip while we are capturing the panel. */
	@Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
	private void mcinvtohtml$hideTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (ExportManager.isCapturePending()) {
			ci.cancel();
		}
	}

	/** Hide the item held on the cursor while capturing. */
	@Inject(method = "renderCarriedItem", at = @At("HEAD"), cancellable = true)
	private void mcinvtohtml$hideCarried(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (ExportManager.isCapturePending()) {
			ci.cancel();
		}
	}

	/** Hide slot hover highlight while capturing. */
	@Inject(method = "renderSlotHighlightBack", at = @At("HEAD"), cancellable = true)
	private void mcinvtohtml$hideHighlightBack(GuiGraphics graphics, CallbackInfo ci) {
		if (ExportManager.isCapturePending()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSlotHighlightFront", at = @At("HEAD"), cancellable = true)
	private void mcinvtohtml$hideHighlightFront(GuiGraphics graphics, CallbackInfo ci) {
		if (ExportManager.isCapturePending()) {
			ci.cancel();
		}
	}
}
