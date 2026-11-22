package com.teleportpretile.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BlinkTest
{
	@Test
	public void blinksThreeTimesThenStops()
	{
		Blink b = new Blink(1000);
		int flashes = 0;
		boolean was = false;
		for (long t = 1000; t < 4000; t += 10)
		{
			boolean now = b.visible(t);
			if (now && !was)
			{
				flashes++;
			}
			was = now;
		}
		assertEquals(Blink.TIMES, flashes);
		assertTrue(b.visible(1000));
		assertFalse(b.visible(1000 + Blink.ON_MILLIS));
		assertTrue(b.visible(1000 + Blink.ON_MILLIS + Blink.OFF_MILLIS));
		assertFalse(b.isOver(1000 + 3 * (Blink.ON_MILLIS + Blink.OFF_MILLIS) - 1));
		assertTrue(b.isOver(1000 + 3 * (Blink.ON_MILLIS + Blink.OFF_MILLIS)));
		assertFalse(b.visible(999));
	}
}
