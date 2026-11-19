package com.namvuive.mcinvtohtml.export;

import com.google.gson.JsonObject;
import com.namvuive.mcinvtohtml.MCInvToHtmlClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class HtmlWriter {
	private static final String TEMPLATE_PATH = "/assets/mcinvtohtml/templates/viewer.html";
	private static String cachedTemplate;

	private HtmlWriter() {
	}

	public static String render(JsonObject data) throws Exception {
		String template = loadTemplate();
		String payload = InventoryExporter.toJsonString(data);
		// Prevent breaking out of the <script> block.
		String safe = payload.replace("</", "<\\/");
		return template.replace("__MCINV_DATA__", safe);
	}

	private static synchronized String loadTemplate() throws Exception {
		if (cachedTemplate != null) {
			return cachedTemplate;
		}
		try (InputStream in = HtmlWriter.class.getResourceAsStream(TEMPLATE_PATH)) {
			if (in == null) {
				throw new IllegalStateException("Viewer template not found in jar: " + TEMPLATE_PATH);
			}
			StringBuilder sb = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					sb.append(line).append('\n');
				}
			}
			cachedTemplate = sb.toString();
			return cachedTemplate;
		} catch (Exception e) {
			MCInvToHtmlClient.LOGGER.error("Failed to load HTML template", e);
			throw e;
		}
	}
}
