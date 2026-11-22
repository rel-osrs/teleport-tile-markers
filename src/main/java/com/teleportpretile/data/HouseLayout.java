package com.teleportpretile.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's house as last seen: which room template sits in each 8x8 chunk of the instance, and the tile the
 * player arrived on. The house is an instance, so its tiles don't exist until the player is there; the Tile Markers
 * plugin saves a marker placed in it at the room template's tile, and draws it wherever that template is placed.
 * With the layout from the last visit the same can be done before arriving.
 *
 * <p>Scene coordinates throughout (tiles from the corner of the loaded scene); the chunk data is the client's
 * instance template chunks, decoded and rotated exactly as RuneLite's {@code WorldPoint.toLocalInstance} does.
 */
public final class HouseLayout
{
	/** Where the house portal, a teleport inside, a house tablet or a cape's "Home" puts the player. */
	public static final String PORTAL = "portal";
	/** Where the garden's spirit tree or fairy ring puts the player. */
	public static final String GARDEN = "garden";

	private static final int CHUNK = 8;

	/** The instance's template chunks, [plane][chunk x][chunk y], as the client gives them. */
	int[][][] chunks;
	/** Arrival tiles by entry ({@link #PORTAL}, {@link #GARDEN}): {scene x, scene y, plane}. */
	Map<String, int[]> entries = new HashMap<>();

	public HouseLayout()
	{
	}

	public HouseLayout(int[][][] chunks)
	{
		this.chunks = chunks;
	}

	/** Records where the player arrived for this entry. */
	public void arrived(String entry, int sceneX, int sceneY, int plane)
	{
		entries.put(entry, new int[]{sceneX, sceneY, plane});
	}

	/** The arrival tile {scene x, scene y, plane} for this entry, or null when never seen. */
	public int[] entry(String entry)
	{
		return entries == null ? null : entries.get(entry);
	}

	public boolean sameChunks(int[][][] other)
	{
		return java.util.Arrays.deepEquals(chunks, other);
	}

	/** One chunk of the house on a plane: where it is in the scene and which template it shows, how turned. */
	public static final class Placement
	{
		public final int sceneChunkX;
		public final int sceneChunkY;
		/** The template chunk's south-west world tile and plane. */
		public final int templateX;
		public final int templateY;
		public final int templatePlane;
		public final int rotation;

		Placement(int sceneChunkX, int sceneChunkY, int templateX, int templateY, int templatePlane, int rotation)
		{
			this.sceneChunkX = sceneChunkX;
			this.sceneChunkY = sceneChunkY;
			this.templateX = templateX;
			this.templateY = templateY;
			this.templatePlane = templatePlane;
			this.rotation = rotation;
		}

		/** True when the template world tile is in this chunk's template. */
		public boolean holds(int x, int y, int plane)
		{
			return plane == templatePlane && x >= templateX && x < templateX + CHUNK && y >= templateY && y < templateY + CHUNK;
		}

		/** The scene tile {x, y} where this chunk shows the template world tile (x, y). */
		public int[] toScene(int x, int y)
		{
			int px = sceneChunkX * CHUNK + (x & (CHUNK - 1));
			int py = sceneChunkY * CHUNK + (y & (CHUNK - 1));
			return rotate(px, py, rotation);
		}
	}

	/** The house's chunks on this plane that show a room template (empty chunks left out). */
	public List<Placement> placements(int plane)
	{
		List<Placement> out = new ArrayList<>();
		if (chunks == null || plane < 0 || plane >= chunks.length)
		{
			return out;
		}
		for (int x = 0; x < chunks[plane].length; x++)
		{
			for (int y = 0; y < chunks[plane][x].length; y++)
			{
				int data = chunks[plane][x][y];
				if (data <= 0)
				{
					continue;
				}
				int rotation = data >> 1 & 0x3;
				int templateY = (data >> 3 & 0x7FF) * CHUNK;
				int templateX = (data >> 14 & 0x3FF) * CHUNK;
				int templatePlane = data >> 24 & 0x3;
				out.add(new Placement(x, y, templateX, templateY, templatePlane, rotation));
			}
		}
		return out;
	}

	/** RuneLite's chunk rotation ({@code WorldPoint.rotate}): turns a tile within its 8x8 chunk. */
	static int[] rotate(int x, int y, int rotation)
	{
		int chunkX = x & ~(CHUNK - 1);
		int chunkY = y & ~(CHUNK - 1);
		int cx = x & (CHUNK - 1);
		int cy = y & (CHUNK - 1);
		switch (rotation)
		{
			case 1:
				return new int[]{chunkX + cy, chunkY + (CHUNK - 1 - cx)};
			case 2:
				return new int[]{chunkX + (CHUNK - 1 - cx), chunkY + (CHUNK - 1 - cy)};
			case 3:
				return new int[]{chunkX + (CHUNK - 1 - cy), chunkY + cx};
			default:
				return new int[]{x, y};
		}
	}

	/** Packs template chunk data the way the client does (for tests). */
	static int pack(int templateChunkX, int templateChunkY, int templatePlane, int rotation)
	{
		return templatePlane << 24 | templateChunkX << 14 | templateChunkY << 3 | rotation << 1;
	}
}
