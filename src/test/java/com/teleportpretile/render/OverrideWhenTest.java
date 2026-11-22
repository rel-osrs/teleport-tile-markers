package com.teleportpretile.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class OverrideWhenTest
{
	@Test
	public void anyIgnoresTheThreshold()
	{
		assertTrue(OverrideWhen.ANY.matches(0, 26));
		assertTrue(OverrideWhen.ANY.matches(26, 26));
		assertTrue(OverrideWhen.ANY.matches(255, 26));
	}

	@Test
	public void below()
	{
		assertTrue(OverrideWhen.BELOW.matches(0, 26));
		assertTrue(OverrideWhen.BELOW.matches(25, 26));
		assertFalse(OverrideWhen.BELOW.matches(26, 26));
		assertFalse(OverrideWhen.BELOW.matches(255, 26));
	}

	@Test
	public void above()
	{
		assertTrue(OverrideWhen.ABOVE.matches(255, 26));
		assertTrue(OverrideWhen.ABOVE.matches(27, 26));
		assertFalse(OverrideWhen.ABOVE.matches(26, 26));
		assertFalse(OverrideWhen.ABOVE.matches(0, 26));
	}

	@Test
	public void exactly()
	{
		assertTrue(OverrideWhen.EXACTLY.matches(26, 26));
		assertFalse(OverrideWhen.EXACTLY.matches(25, 26));
		assertTrue(OverrideWhen.EXACTLY.matches(255, 255));
		assertTrue(OverrideWhen.EXACTLY.matches(0, 0));
	}
}
