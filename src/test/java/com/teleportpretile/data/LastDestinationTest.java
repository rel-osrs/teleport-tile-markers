package com.teleportpretile.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class LastDestinationTest
{
	@Test
	public void lastDestinationClicks()
	{
		assertEquals(LastDestination.QUETZAL_WHISTLE,
			LastDestination.clicked("Last-destination", "<col=ff9040>Perfected quetzal whistle</col>"));
		assertEquals(LastDestination.RING_OF_THE_ELEMENTS,
			LastDestination.clicked("Last Destination", "Ring of the elements"));
		assertEquals(LastDestination.PHARAOHS_SCEPTRE, LastDestination.clicked("Last-Teleport", "Pharaoh's sceptre"));
		assertNull(LastDestination.clicked("Signal", "Basic quetzal whistle"));
		assertNull(LastDestination.clicked("Last-destination", "Fairy mushroom"));
	}

	@Test
	public void everyBundledTeleportOfTheseItemsIsFound() throws IOException
	{
		TeleportCatalog catalog;
		try (InputStream in = LastDestinationTest.class.getResourceAsStream("/com/teleportpretile/teleports.tsv"))
		{
			catalog = TeleportCatalog.load(in);
		}
		int quetzal = 0;
		int ring = 0;
		int sceptre = 0;
		for (Teleport t : catalog.all())
		{
			LastDestination d = LastDestination.of(t);
			if (d == LastDestination.QUETZAL_WHISTLE)
			{
				quetzal++;
			}
			else if (d == LastDestination.RING_OF_THE_ELEMENTS)
			{
				ring++;
			}
			else if (d == LastDestination.PHARAOHS_SCEPTRE)
			{
				sceptre++;
			}
		}
		assertEquals(4, ring);
		assertEquals(4, sceptre);
		assertEquals(true, quetzal >= 26);
		assertEquals("QUETZAL_WHISTLE.12", LastDestination.QUETZAL_WHISTLE.key(12));
	}
}
