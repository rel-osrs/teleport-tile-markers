package com.teleportpretile.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HouseLayoutTest
{
	/** RuneLite's WorldPoint.toLocalInstance with base 0 (scene tiles), copied as the reference. */
	private static List<int[]> runeLite(int[][][] chunks, int x, int y, int plane)
	{
		List<int[]> out = new ArrayList<>();
		for (int z = 0; z < chunks.length; z++)
		{
			for (int cx = 0; cx < chunks[z].length; ++cx)
			{
				for (int cy = 0; cy < chunks[z][cx].length; ++cy)
				{
					int chunkData = chunks[z][cx][cy];
					int rotation = chunkData >> 1 & 0x3;
					int templateChunkY = (chunkData >> 3 & 0x7FF) * 8;
					int templateChunkX = (chunkData >> 14 & 0x3FF) * 8;
					int p = chunkData >> 24 & 0x3;
					if (x >= templateChunkX && x < templateChunkX + 8 && y >= templateChunkY && y < templateChunkY + 8 && p == plane)
					{
						int px = cx * 8 + (x & 7);
						int py = cy * 8 + (y & 7);
						int bx = px & ~7;
						int by = py & ~7;
						int ix = px & 7;
						int iy = py & 7;
						switch (rotation)
						{
							case 1:
								out.add(new int[]{bx + iy, by + (7 - ix), z});
								break;
							case 2:
								out.add(new int[]{bx + (7 - ix), by + (7 - iy), z});
								break;
							case 3:
								out.add(new int[]{bx + (7 - iy), by + ix, z});
								break;
							default:
								out.add(new int[]{px, py, z});
						}
					}
				}
			}
		}
		return out;
	}

	private static int[][][] house()
	{
		int[][][] chunks = new int[4][13][13];
		for (int[][] plane : chunks)
		{
			for (int[] row : plane)
			{
				java.util.Arrays.fill(row, -1);
			}
		}
		// the same room template (chunk 237, 712 on plane 0) placed four times, turned each way
		chunks[0][6][6] = HouseLayout.pack(237, 712, 0, 0);
		chunks[0][7][6] = HouseLayout.pack(237, 712, 0, 1);
		chunks[0][6][7] = HouseLayout.pack(237, 712, 0, 2);
		chunks[0][7][7] = HouseLayout.pack(237, 712, 0, 3);
		// another template upstairs
		chunks[1][6][6] = HouseLayout.pack(240, 712, 1, 0);
		return chunks;
	}

	@Test
	public void placesTemplateTilesExactlyAsRuneLite()
	{
		int[][][] chunks = house();
		HouseLayout layout = new HouseLayout(chunks);
		for (int ix = 0; ix < 8; ix++)
		{
			for (int iy = 0; iy < 8; iy++)
			{
				int x = 237 * 8 + ix;
				int y = 712 * 8 + iy;
				List<int[]> expected = runeLite(chunks, x, y, 0);
				List<int[]> mine = new ArrayList<>();
				for (HouseLayout.Placement p : layout.placements(0))
				{
					if (p.holds(x, y, 0))
					{
						int[] s = p.toScene(x, y);
						mine.add(new int[]{s[0], s[1], 0});
					}
				}
				assertEquals(4, expected.size());
				assertEquals(expected.size(), mine.size());
				for (int i = 0; i < expected.size(); i++)
				{
					assertArrayEquals(expected.get(i), mine.get(i));
				}
			}
		}
	}

	@Test
	public void placementsByPlaneSkipEmptyChunks()
	{
		HouseLayout layout = new HouseLayout(house());
		assertEquals(4, layout.placements(0).size());
		assertEquals(1, layout.placements(1).size());
		assertTrue(layout.placements(2).isEmpty());
		assertTrue(layout.placements(9).isEmpty());
	}

	@Test
	public void rememberedEntries()
	{
		HouseLayout layout = new HouseLayout(house());
		assertNull(layout.entry(HouseLayout.PORTAL));
		layout.arrived(HouseLayout.PORTAL, 52, 50, 0);
		assertArrayEquals(new int[]{52, 50, 0}, layout.entry(HouseLayout.PORTAL));
		assertNull(layout.entry(HouseLayout.GARDEN));
		assertTrue(layout.sameChunks(house()));
		int[][][] rebuilt = house();
		rebuilt[0][8][6] = HouseLayout.pack(237, 713, 0, 0);
		assertFalse(layout.sameChunks(rebuilt));
	}

	@Test
	public void tripsIntoTheHouse() throws IOException
	{
		TeleportCatalog catalog;
		try (InputStream in = HouseLayoutTest.class.getResourceAsStream("/com/teleportpretile/teleports.tsv"))
		{
			catalog = TeleportCatalog.load(in);
		}
		Teleport cast = TeleportCatalog.resolve(catalog.started("Cast", "Teleport to House", -1),
			id -> id == Condition.SPELLBOOK_VARBIT ? 0 : 0);
		assertNotNull(cast);
		assertTrue(cast.isHouse());
		assertEquals(HouseLayout.PORTAL, cast.getHouseEntry());
		// with the house set to teleport outside (varbit 4744 = 1), Cast goes to the house's town (Yanille here)
		Teleport outside = TeleportCatalog.resolve(catalog.started("Cast", "Teleport to House", -1),
			id -> id == 4744 ? 1 : id == Condition.HOUSE_VARBIT ? 6 : 0);
		assertNotNull(outside);
		assertFalse(outside.isHouse());
		assertEquals(2544, outside.getX());
		// the Construction cape's "Tele to POH" goes inside, worn or not
		Teleport cape = TeleportCatalog.resolve(catalog.started("Tele to POH", "Construct. cape(t)", -1), id -> 0);
		assertNotNull(cape);
		assertTrue(cape.isHouse());
		// the house tablet's Break goes inside when the house is set to teleport inside (varbit 4744 = 0)
		Teleport tablet = TeleportCatalog.resolve(catalog.started("Break", "Teleport to house", 8013), id -> 0);
		assertNotNull(tablet);
		assertTrue(tablet.isHouse());
		// the portal in Yanille: "Home" goes straight in
		Teleport portal = TeleportCatalog.resolve(TeleportCatalog.near(catalog.started("Home", "Portal", -1), 2544, 3096, 0),
			id -> id == Condition.HOUSE_VARBIT ? 6 : 0);
		assertNotNull(portal);
		assertTrue(portal.isHouse());
		// the garden's spirit tree lands in the garden
		Teleport garden = TeleportCatalog.resolve(TeleportCatalog.chosenByKey(catalog.opened("Travel", "Spirit tree", -1), 'C'),
			id -> 0);
		assertNotNull(garden);
		assertEquals(HouseLayout.GARDEN, garden.getHouseEntry());
	}
}
