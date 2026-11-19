package com.namvuive.mcinvtohtml.mixin;

import com.namvuive.mcinvtohtml.export.ExportManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Inject(method = "init", at = @At("TAIL"))
	private void mcinvtohtml$addExportButton(CallbackInfo ci) {
		AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
		if (self instanceof CreativeModeInventoryScreen) {
			return;
		}

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) self;
		int width = 48;
		int height = 14;
		int x = accessor.mcinvtohtml$leftPos() + accessor.mcinvtohtml$imageWidth() - width + 2;
		// Keep it just above the panel but never inside it (would end up in the capture).
		int y = Math.max(2, accessor.mcinvtohtml$topPos() - height - 2);

		Button button = Button.builder(Component.literal("Export"), b -> ExportManager.request(self))
			.bounds(x, y, width, height)
			.build();
		((ScreenAccessor) self).mcinvtohtml$addRenderableWidget(button);
	}
}
