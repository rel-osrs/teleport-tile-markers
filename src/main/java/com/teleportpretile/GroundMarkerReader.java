package com.teleportpretile;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the tiles marked with RuneLite's Tile Markers (ground markers) plugin, for any region, straight from the
 * config where that plugin saves them. Works for regions that aren't loaded, which is the point: the destination
 * of a teleport isn't loaded until the player gets there.
 *
 * <p>Read only, always: nothing in this plugin writes to the Tile Markers config. Colour overrides and the blur
 * change only how this plugin draws its preview of the markers.
 */
@Slf4j
@Singleton
class GroundMarkerReader
{
	static final String GROUP = "groundMarker";
	private static final String REGION_PREFIX = "region_";

	/** A marked tile in world coordinates. */
	@Value
	static class Marker
	{
		int x;
		int y;
		int plane;
		/** Null for old markers saved without a colour: the Tile Markers default colour applies. */
		@Nullable
		Color color;
		@Nullable
		String label;
	}

	/** The fields the Tile Markers plugin saves for each tile (its GroundMarkerPoint). */
	private static final class SavedPoint
	{
		int regionId;
		int regionX;
		int regionY;
		int z;
		Color color;
		String label;
	}

	private final ConfigManager configManager;
	private final Gson gson;
	private final Map<Integer, List<Marker>> cache = new HashMap<>();

	@Inject
	GroundMarkerReader(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Forget what was read, after the Tile Markers config changes. */
	synchronized void invalidate()
	{
		cache.clear();
	}

	/** Every marker on {@code plane} within {@code range} tiles (Chebyshev) of tile (x, y). */
	synchronized List<Marker> around(int x, int y, int plane, int range)
	{
		List<Marker> out = new ArrayList<>();
		for (int rx = (x - range) >> 6; rx <= (x + range) >> 6; rx++)
		{
			for (int ry = (y - range) >> 6; ry <= (y + range) >> 6; ry++)
			{
				for (Marker m : region((rx << 8) | ry))
				{
					if (m.plane == plane && Math.abs(m.x - x) <= range && Math.abs(m.y - y) <= range)
					{
						out.add(m);
					}
				}
			}
		}
		return out;
	}

	private List<Marker> region(int regionId)
	{
		return cache.computeIfAbsent(regionId, this::load);
	}

	private List<Marker> load(int regionId)
	{
		String json = configManager.getConfiguration(GROUP, REGION_PREFIX + regionId);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}
		List<SavedPoint> points;
		try
		{
			points = gson.fromJson(json, new TypeToken<List<SavedPoint>>()
			{
			}.getType());
		}
		catch (JsonParseException e)
		{
			log.debug("unreadable tile markers for region {}", regionId, e);
			return Collections.emptyList();
		}
		if (points == null)
		{
			return Collections.emptyList();
		}
		List<Marker> out = new ArrayList<>(points.size());
		for (SavedPoint p : points)
		{
			if (p == null)
			{
				continue;
			}
			int id = p.regionId != 0 ? p.regionId : regionId;
			int worldX = ((id >>> 8) << 6) + p.regionX;
			int worldY = ((id & 0xFF) << 6) + p.regionY;
			out.add(new Marker(worldX, worldY, p.z, p.color, p.label));
		}
		return out;
	}
}
