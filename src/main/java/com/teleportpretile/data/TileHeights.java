package com.teleportpretile.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The ground's height at the marked tiles around one landing spot, learned the first time the player lands there,
 * so later trips can draw each marker at its real height instead of flat at the player's feet.
 *
 * <p>Heights are kept relative to the landing tile's middle (in the game's height units, 128 to a tile, where more
 * negative is higher) for each tile's four corners, in the order (-x,-y), (+x,-y), (+x,+y), (-x,+y). Tiles level
 * with the landing spot aren't stored, so flat places cost almost nothing to save.
 */
public final class TileHeights
{
	/** The ground height at a scene corner, or null where it isn't loaded. */
	public interface Ground
	{
		/**
		 * @param x world tile x
		 * @param y world tile y
		 * @param corner 0-3, in the class's corner order
		 */
		Integer corner(int x, int y, int corner);
	}

	private static final int[] FLAT = {0, 0, 0, 0};

	private final Map<Long, int[]> tiles;

	private TileHeights(Map<Long, int[]> tiles)
	{
		this.tiles = tiles;
	}

	/**
	 * Reads the heights of these marked tiles (x, y pairs) around the landing tile (cx, cy) from the loaded ground.
	 * Null when the landing tile itself isn't loaded. Tiles that aren't loaded are left out (drawn flat).
	 */
	public static TileHeights learn(int cx, int cy, int[][] marked, Ground ground)
	{
		int sum = 0;
		for (int c = 0; c < 4; c++)
		{
			Integer h = ground.corner(cx, cy, c);
			if (h == null)
			{
				return null;
			}
			sum += h;
		}
		// the middle of a tile is the mean of its corners (the game blends a tile's corners)
		int middle = Math.round(sum / 4f);
		Map<Long, int[]> out = new HashMap<>();
		for (int[] xy : marked)
		{
			int[] rel = new int[4];
			boolean level = true;
			boolean loaded = true;
			for (int c = 0; c < 4 && loaded; c++)
			{
				Integer h = ground.corner(xy[0], xy[1], c);
				if (h == null)
				{
					loaded = false;
					break;
				}
				rel[c] = h - middle;
				level &= rel[c] == 0;
			}
			if (loaded && !level)
			{
				out.put(key(xy[0] - cx, xy[1] - cy), rel);
			}
		}
		return new TileHeights(out);
	}

	/** The corners' heights of the tile (dx, dy) tiles from the landing tile, relative to it. Never null. */
	public int[] at(int dx, int dy)
	{
		int[] h = tiles.get(key(dx, dy));
		return h != null ? h : FLAT;
	}

	/**
	 * True when this knows a height that {@code before} didn't (a tile it didn't have, or a different height), so the
	 * trip taught something new. {@code before} null: nothing was known.
	 */
	public boolean addsTo(TileHeights before)
	{
		for (Map.Entry<Long, int[]> e : tiles.entrySet())
		{
			int[] old = before == null ? null : before.tiles.get(e.getKey());
			if (old == null || !Arrays.equals(old, e.getValue()))
			{
				return true;
			}
		}
		return false;
	}

	public int size()
	{
		return tiles.size();
	}

	/** Saved form: "dx,dy:h0,h1,h2,h3" per sloped or raised tile, joined by ";"; empty when all are level. */
	public String encode()
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Long, int[]> e : tiles.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			int[] h = e.getValue();
			sb.append(dx(e.getKey())).append(',').append(dy(e.getKey())).append(':')
				.append(h[0]).append(',').append(h[1]).append(',').append(h[2]).append(',').append(h[3]);
		}
		return sb.toString();
	}

	/** Reads {@link #encode()}'s form; null for text it can't read (then nothing was learned). */
	public static TileHeights decode(String s)
	{
		if (s == null)
		{
			return null;
		}
		if (s.isEmpty())
		{
			return new TileHeights(Collections.emptyMap());
		}
		Map<Long, int[]> out = new HashMap<>();
		try
		{
			for (String part : s.split(";"))
			{
				String[] kv = part.split(":");
				String[] d = kv[0].split(",");
				String[] h = kv[1].split(",");
				if (d.length != 2 || h.length != 4)
				{
					return null;
				}
				out.put(key(Integer.parseInt(d[0].trim()), Integer.parseInt(d[1].trim())), new int[]{
					Integer.parseInt(h[0].trim()), Integer.parseInt(h[1].trim()), Integer.parseInt(h[2].trim()),
					Integer.parseInt(h[3].trim())});
			}
		}
		catch (RuntimeException e)
		{
			return null;
		}
		return new TileHeights(out);
	}

	private static long key(int dx, int dy)
	{
		return ((long) dx << 32) | (dy & 0xffffffffL);
	}

	private static int dx(long key)
	{
		return (int) (key >> 32);
	}

	private static int dy(long key)
	{
		return (int) key;
	}
}
