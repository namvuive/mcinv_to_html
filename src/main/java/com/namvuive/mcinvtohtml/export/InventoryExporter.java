package com.namvuive.mcinvtohtml.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.namvuive.mcinvtohtml.MCInvToHtmlClient;
import com.namvuive.mcinvtohtml.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Optional;

/**
 * Converts the currently open container screen into a JSON payload that the
 * HTML viewer template can render. Runs on the client thread.
 */
public final class InventoryExporter {
	private static final Gson GSON = new GsonBuilder().create();
	private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

	private InventoryExporter() {
	}

	public static JsonObject export(AbstractContainerScreen<?> screen) {
		Minecraft mc = Minecraft.getInstance();
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		JsonObject root = new JsonObject();

		root.addProperty("modVersion", "1.0.0");
		root.addProperty("minecraftVersion", mc.getLaunchedVersion());
		root.addProperty("exportedAt", System.currentTimeMillis());
		root.addProperty("imageWidth", accessor.mcinvtohtml$imageWidth());
		root.addProperty("imageHeight", accessor.mcinvtohtml$imageHeight());
		root.addProperty("titleLabelX", accessor.mcinvtohtml$titleLabelX());
		root.addProperty("titleLabelY", accessor.mcinvtohtml$titleLabelY());
		root.addProperty("inventoryLabelX", accessor.mcinvtohtml$inventoryLabelX());
		root.addProperty("inventoryLabelY", accessor.mcinvtohtml$inventoryLabelY());
		root.addProperty("screenClass", screen.getClass().getSimpleName());

		JsonObject title = componentToJson(screen.getTitle());
		root.add("title", title);

		JsonObject inventoryTitle = componentToJson(accessor.mcinvtohtml$playerInventoryTitle());
		root.add("inventoryTitle", inventoryTitle);

		JsonArray slots = new JsonArray();
		int index = 0;
		for (Slot slot : screen.getMenu().slots) {
			JsonObject json = new JsonObject();
			json.addProperty("index", index++);
			json.addProperty("containerSlot", slot.getContainerSlot());
			json.addProperty("x", slot.x);
			json.addProperty("y", slot.y);
			json.addProperty("active", slot.isActive());

			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				json.add("item", itemToJson(stack));
			}
			slots.add(json);
		}
		root.add("slots", slots);
		return root;
	}

	private static JsonObject itemToJson(ItemStack stack) {
		JsonObject json = new JsonObject();
		Item item = stack.getItem();

		json.addProperty("id", item.builtInRegistryHolder().key().identifier().toString());
		json.addProperty("count", stack.getCount());
		json.addProperty("maxStackSize", stack.getMaxStackSize());
		json.addProperty("damage", stack.getDamageValue());
		json.addProperty("maxDamage", stack.getMaxDamage());
		json.addProperty("barWidth", item.isBarVisible(stack) ? item.getBarWidth(stack) : -1);
		json.addProperty("barColor", item.isBarVisible(stack) ? item.getBarColor(stack) : 0);
		json.addProperty("rarity", stack.getRarity().name());
		json.addProperty("foil", stack.hasFoil());

		json.add("name", componentToJson(stack.getHoverName()));
		json.add("tooltip", tooltipToJson(stack));
		json.add("components", componentsToJson(stack));
		json.addProperty("componentsSnbt", componentsToSnbt(stack));

		return json;
	}

	private static JsonArray tooltipToJson(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		List<Component> lines = stack.getTooltipLines(
			mc.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(mc.level),
			mc.player,
			mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL
		);

		JsonArray result = new JsonArray();
		for (Component line : lines) {
			result.add(componentToJson(line));
		}
		return result;
	}

	/** Serializes a component into plain text plus a list of styled spans. */
	public static JsonObject componentToJson(Component component) {
		JsonObject json = new JsonObject();
		json.addProperty("text", component.getString());

		JsonArray spans = new JsonArray();
		component.visit((style, content) -> {
			if (!content.isEmpty()) {
				JsonObject span = new JsonObject();
				span.addProperty("text", content);
				styleToJson(style, span);
				spans.add(span);
			}
			return Optional.empty();
		}, Style.EMPTY);
		json.add("spans", spans);

		try {
			JsonElement serialized = ComponentSerialization.CODEC
				.encodeStart(JsonOps.INSTANCE, component)
				.getOrThrow();
			json.add("serialized", serialized);
			json.addProperty("serializedPretty", GSON_PRETTY.toJson(serialized));
		} catch (Exception e) {
			MCInvToHtmlClient.LOGGER.warn("Failed to serialize component", e);
			json.addProperty("serializedPretty", component.getString());
		}
		return json;
	}

	private static void styleToJson(Style style, JsonObject target) {
		TextColor color = style.getColor();
		if (color != null) {
			target.addProperty("color", String.format("#%06X", color.getValue() & 0xFFFFFF));
		}
		if (style.isBold()) {
			target.addProperty("bold", true);
		}
		if (style.isItalic()) {
			target.addProperty("italic", true);
		}
		if (style.isUnderlined()) {
			target.addProperty("underlined", true);
		}
		if (style.isStrikethrough()) {
			target.addProperty("strikethrough", true);
		}
		if (style.isObfuscated()) {
			target.addProperty("obfuscated", true);
		}
	}

	private static JsonElement componentsToJson(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		DataComponentPatch patch = stack.getComponentsPatch();
		if (patch.isEmpty()) {
			return new JsonObject();
		}
		try {
			return DataComponentPatch.CODEC.encodeStart(
				mc.level == null
					? JsonOps.INSTANCE
					: RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess()),
				patch
			).getOrThrow();
		} catch (Exception e) {
			MCInvToHtmlClient.LOGGER.warn("Failed to serialize components", e);
			return new JsonObject();
		}
	}

	/** Human readable SNBT dump (same style as /data get output). */
	private static String componentsToSnbt(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		try {
			DataComponentPatch patch = stack.getComponentsPatch();
			Tag tag;
			if (patch.isEmpty()) {
				tag = new net.minecraft.nbt.CompoundTag();
			} else {
				tag = DataComponentPatch.CODEC.encodeStart(
					mc.level == null
						? net.minecraft.nbt.NbtOps.INSTANCE
						: RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, mc.level.registryAccess()),
					patch
				).getOrThrow();
			}

			net.minecraft.nbt.CompoundTag wrapper = new net.minecraft.nbt.CompoundTag();
			wrapper.putString("id", stack.getItem().builtInRegistryHolder().key().identifier().toString());
			wrapper.putInt("count", stack.getCount());
			if (tag instanceof net.minecraft.nbt.CompoundTag compound && !compound.isEmpty()) {
				wrapper.put("components", compound);
			}
			return net.minecraft.nbt.NbtUtils.prettyPrint(wrapper, true);
		} catch (Exception e) {
			MCInvToHtmlClient.LOGGER.warn("Failed to build SNBT", e);
			return "{}";
		}
	}

	public static String toJsonString(JsonObject object) {
		return GSON.toJson(object);
	}
}
