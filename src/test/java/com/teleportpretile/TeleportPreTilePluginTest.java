package com.teleportpretile;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts RuneLite with this plugin loaded, for trying it in the game: {@code ./gradlew shadowJar}, then run the jar. */
public class TeleportPreTilePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TeleportPreTilePlugin.class);
		RuneLite.main(args);
	}
}
