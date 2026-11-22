package com.teleportpretile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BlurLayersTest
{
	/** How opaque the piled-up layers are in band k (1 = next to the outline): layers k..n all cover it. */
	private static double covered(double[] a, int k)
	{
		double clear = 1;
		for (int j = k; j <= a.length; j++)
		{
			clear *= 1 - a[j - 1];
		}
		return 1 - clear;
	}

	@Test
	public void strongestNextToTheOutlineAndFadingOut()
	{
		for (double reach : new double[]{2, 9, 18, 40, 200})
		{
			double[] a = TeleportPreTileOverlay.blurLayerAlphas(reach);
			assertTrue(a.length >= 1 && a.length <= 12);
			double prev = 1;
			for (int k = 1; k <= a.length; k++)
			{
				assertTrue(a[k - 1] >= 0 && a[k - 1] <= 1);
				double c = covered(a, k);
				assertTrue("fades with distance at reach " + reach, c <= prev + 1e-9);
				prev = c;
			}
			assertEquals("the peak next to the outline", 0.6, covered(a, 1), 1e-9);
			assertTrue("all but gone at the edge", covered(a, a.length) < 0.25 || a.length == 1);
		}
	}

	@Test
	public void followsABellCurve()
	{
		double[] a = TeleportPreTileOverlay.blurLayerAlphas(18);
		int n = a.length;
		double sigma = 1 / 3.0;
		for (int k = 1; k <= n; k++)
		{
			double d = (k - 1) / (double) n;
			assertEquals(0.6 * Math.exp(-0.5 * d * d / (sigma * sigma)), covered(a, k), 1e-9);
		}
	}
}
