package com.teleportpretile.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class TileHeightsTest
{
	/** Ground rising 8 units (higher = more negative) per tile east of x = 99, flat elsewhere; corners split the step. */
	private static Integer slope(int x, int y, int corner)
	{
		int east = corner == 1 || corner == 2 ? 1 : 0;
		return -Math.max(0, x + east - 99) * 8 - 400;
	}

	@Test
	public void relativeToTheLandingTileAndLevelTilesLeftOut()
	{
		int[][] marked = {{98, 50}, {101, 50}, {103, 52}};
		TileHeights h = TileHeights.learn(99, 50, marked, TileHeightsTest::slope);
		// the landing tile (99) runs from -400 (west) to -408 (east): its middle is -404
		assertEquals(3, h.size());
		assertArrayEquals(new int[]{4, 4, 4, 4}, h.at(-1, 0));
		assertArrayEquals(new int[]{-12, -20, -20, -12}, h.at(2, 0));
		assertArrayEquals(new int[]{-28, -36, -36, -28}, h.at(4, 2));
		assertArrayEquals("a tile nothing was learned for is drawn level", new int[]{0, 0, 0, 0}, h.at(7, 7));
	}

	@Test
	public void levelGroundStoresNothing()
	{
		TileHeights h = TileHeights.learn(3087, 3496, new int[][]{{3085, 3497}, {3090, 3490}}, (x, y, c) -> -200);
		assertEquals(0, h.size());
		assertEquals("", h.encode());
		assertEquals(0, TileHeights.decode("").size());
	}

	@Test
	public void unloadedTiles()
	{
		assertNull("landing tile not loaded", TileHeights.learn(1, 1, new int[][]{{2, 2}}, (x, y, c) -> null));
		TileHeights h = TileHeights.learn(1, 1, new int[][]{{2, 2}, {9, 9}},
			(x, y, c) -> x == 9 ? null : (x == 2 ? -50 : -10));
		assertEquals(1, h.size());
		assertArrayEquals(new int[]{-40, -40, -40, -40}, h.at(1, 1));
	}

	@Test
	public void savesAndReadsBack()
	{
		int[][] marked = {{98, 50}, {101, 50}, {103, 52}};
		TileHeights h = TileHeights.learn(99, 50, marked, TileHeightsTest::slope);
		TileHeights back = TileHeights.decode(h.encode());
		assertEquals(3, back.size());
		assertArrayEquals(h.at(4, 2), back.at(4, 2));
		assertArrayEquals(h.at(-1, 0), back.at(-1, 0));
		assertNull(TileHeights.decode("1,2:3"));
		assertNull(TileHeights.decode("garbage"));
		assertNull(TileHeights.decode(null));
	}
}
