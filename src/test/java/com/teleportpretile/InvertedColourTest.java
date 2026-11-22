package com.teleportpretile;

import static org.junit.Assert.assertEquals;
import java.awt.Color;
import org.junit.Test;

public class InvertedColourTest
{
	@Test
	public void invertsRedGreenBlueAndKeepsAlpha()
	{
		assertEquals(new Color(0, 0, 255, 255), TeleportPreTileOverlay.inverted(Color.YELLOW));
		assertEquals(new Color(255, 255, 255, 80), TeleportPreTileOverlay.inverted(new Color(0, 0, 0, 80)));
		assertEquals(new Color(155, 55, 255, 200), TeleportPreTileOverlay.inverted(new Color(100, 200, 0, 200)));
	}
}
