package com.namvuive.mcinvtohtml.export;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.namvuive.mcinvtohtml.MCInvToHtmlClient;
import com.namvuive.mcinvtohtml.mixin.AbstractContainerScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Orchestrates an export: snapshot the container data now, wait for the next
 * rendered frame (with hover tooltip / carried item suppressed), capture the
 * panel region from the framebuffer, and write the self-contained HTML file.
 */
public final class ExportManager {
	private static CaptureRequest pending;

	private ExportManager() {
	}

	private record CaptureRequest(
		JsonObject inventory,
		int leftPos,
		int topPos,
		int imageWidth,
		int imageHeight,
		String baseName,
		long time
	) {
	}

	/** True while a capture is queued; used by mixins to suppress hover/carried item. */
	public static boolean isCapturePending() {
		return pending != null;
	}

	public static void request(AbstractContainerScreen<?> screen) {
		Minecraft mc = Minecraft.getInstance();
		if (pending != null) {
			chat(mc, Component.literal("An export is already in progress...").withStyle(ChatFormatting.YELLOW));
			return;
		}

		JsonObject inventory;
		try {
			inventory = InventoryExporter.export(screen);
		} catch (Throwable t) {
			MCInvToHtmlClient.LOGGER.error("Failed to gather inventory data", t);
			chat(mc, Component.literal("Export failed: " + t.getMessage()).withStyle(ChatFormatting.RED));
			return;
		}

		String plainTitle = screen.getTitle().getString();
		String baseName = sanitize(plainTitle);
		if (baseName.isEmpty()) {
			baseName = sanitize(screen.getClass().getSimpleName());
		}

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		pending = new CaptureRequest(
			inventory,
			accessor.mcinvtohtml$leftPos(),
			accessor.mcinvtohtml$topPos(),
			accessor.mcinvtohtml$imageWidth(),
			accessor.mcinvtohtml$imageHeight(),
			baseName,
			System.currentTimeMillis()
		);
	}

	/** Called at the tail of GameRenderer#render on the render thread. */
	public static void onFrameRendered() {
		CaptureRequest request = pending;
		if (request == null) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.getMainRenderTarget();
		if (target == null) {
			pending = null;
			return;
		}

		int fbWidth = target.width;
		int fbHeight = target.height;
		int guiWidth = mc.getWindow().getGuiScaledWidth();
		int guiHeight = mc.getWindow().getGuiScaledHeight();
		if (fbWidth <= 0 || fbHeight <= 0 || guiWidth <= 0 || guiHeight <= 0) {
			pending = null;
			return;
		}

		// The screen for this frame has already been rendered (this runs at the tail
		// of GameRenderer#render), so hover/carried suppression has taken effect.
		// Clear the flag now so subsequent frames render normally again.
		try {
			Screenshot.takeScreenshot(target, image -> {
				try {
					handleCapture(request, image, fbWidth, fbHeight, guiWidth, guiHeight);
				} catch (Throwable t) {
					MCInvToHtmlClient.LOGGER.error("Failed to process capture", t);
				} finally {
					image.close();
				}
			});
		} finally {
			pending = null;
		}
	}

	private static void handleCapture(
		CaptureRequest request,
		NativeImage full,
		int fbWidth,
		int fbHeight,
		int guiWidth,
		int guiHeight
	) {
		double scaleX = (double) fbWidth / guiWidth;
		double scaleY = (double) fbHeight / guiHeight;

		int x0 = (int) Math.round(request.leftPos() * scaleX);
		int y0 = (int) Math.round(request.topPos() * scaleY);
		int w = (int) Math.round(request.imageWidth() * scaleX);
		int h = (int) Math.round(request.imageHeight() * scaleY);

		x0 = Math.max(0, Math.min(x0, full.getWidth() - 1));
		y0 = Math.max(0, Math.min(y0, full.getHeight() - 1));
		w = Math.max(1, Math.min(w, full.getWidth() - x0));
		h = Math.max(1, Math.min(h, full.getHeight() - y0));

		NativeImage cropped = new NativeImage(w, h, false);
		try {
			full.copyRect(cropped, x0, y0, 0, 0, w, h, false, false);

			Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("mcinvtohtml");
			Files.createDirectories(dir);

			Path temp = dir.resolve(".capture_" + request.time() + ".png");
			cropped.writeToFile(temp);
			byte[] png = Files.readAllBytes(temp);
			Files.deleteIfExists(temp);

			JsonObject data = request.inventory();
			JsonObject image = new JsonObject();
			image.addProperty("png", Base64.getEncoder().encodeToString(png));
			image.addProperty("width", w);
			image.addProperty("height", h);
			image.addProperty("sourceScaleX", scaleX);
			image.addProperty("sourceScaleY", scaleY);
			data.add("image", image);

			String stamp = java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
			String fileName = request.baseName() + "_" + stamp + ".html";
			Path output = dir.resolve(fileName);
			int suffix = 1;
			while (Files.exists(output)) {
				output = dir.resolve(request.baseName() + "_" + stamp + "_" + suffix++ + ".html");
			}

			String html = HtmlWriter.render(data);
			Files.writeString(output, html);

			notifySuccess(output);
		} catch (Throwable t) {
			MCInvToHtmlClient.LOGGER.error("Failed to write export", t);
			chat(Minecraft.getInstance(), Component.literal("Export failed: " + t.getMessage()).withStyle(ChatFormatting.RED));
		} finally {
			cropped.close();
		}
	}

	private static void notifySuccess(Path output) {
		Minecraft mc = Minecraft.getInstance();
		Path relative = mc.gameDirectory.toPath().relativize(output);
		Component link = Component.literal(relative.toString().replace('\\', '/'))
			.withStyle(ChatFormatting.UNDERLINE)
			.withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(output.toAbsolutePath().toString())));
		chat(mc, Component.literal("Exported to ").withStyle(ChatFormatting.GREEN)
			.append(link.copy().withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" (click to open)").withStyle(ChatFormatting.DARK_GRAY)));
	}

	private static void chat(Minecraft mc, Component message) {
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		} else {
			MCInvToHtmlClient.LOGGER.info(message.getString());
		}
	}

	private static String sanitize(String raw) {
		if (raw == null) {
			return "";
		}
		String cleaned = raw.trim().toLowerCase(java.util.Locale.ROOT)
			.replaceAll("[^a-z0-9._-]+", "_")
			.replaceAll("_+", "_")
			.replaceAll("^_|_$", "");
		if (cleaned.length() > 48) {
			cleaned = cleaned.substring(0, 48);
		}
		return cleaned;
	}

	static void runAsync(Runnable runnable) {
		Util.ioPool().execute(runnable);
	}
}
