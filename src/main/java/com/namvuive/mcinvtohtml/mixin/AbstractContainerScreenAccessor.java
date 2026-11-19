package com.namvuive.mcinvtohtml.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("imageWidth")
	int mcinvtohtml$imageWidth();

	@Accessor("imageHeight")
	int mcinvtohtml$imageHeight();

	@Accessor("titleLabelX")
	int mcinvtohtml$titleLabelX();

	@Accessor("titleLabelY")
	int mcinvtohtml$titleLabelY();

	@Accessor("inventoryLabelX")
	int mcinvtohtml$inventoryLabelX();

	@Accessor("inventoryLabelY")
	int mcinvtohtml$inventoryLabelY();

	@Accessor("leftPos")
	int mcinvtohtml$leftPos();

	@Accessor("topPos")
	int mcinvtohtml$topPos();

	@Accessor("playerInventoryTitle")
	net.minecraft.network.chat.Component mcinvtohtml$playerInventoryTitle();
}
