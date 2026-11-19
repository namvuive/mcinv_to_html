package com.namvuive.mcinvtohtml;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCInvToHtmlClient implements ClientModInitializer {
	public static final String MOD_ID = "mcinvtohtml";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("MCInv to HTML initialized");
	}
}
