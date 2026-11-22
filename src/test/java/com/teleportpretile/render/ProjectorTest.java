package com.teleportpretile.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ProjectorTest
{
	/** RuneLite's integer CPU projection (Perspective.localToCanvasCpu) written out, to compare with. */
	private static int[] runeLiteCpu(int camX, int camY, int camZ, int pitch, int yaw, int scale, int w, int h,
		int x, int y, int z)
	{
		int[] sine = new int[0x4000];
		int[] cosine = new int[0x4000];
		for (int i = 0; i < 0x4000; i++)
		{
			sine[i] = (int) (65536.0 * Math.sin(i * 3.834951969714103E-4D));
			cosine[i] = (int) (65536.0 * Math.cos(i * 3.834951969714103E-4D));
		}
		x -= camX;
		y -= camY;
		z -= camZ;
		int x1 = x * cosine[yaw] + y * sine[yaw] >> 16;
		int y1 = y * cosine[yaw] - x * sine[yaw] >> 16;
		int y2 = z * cosine[pitch] - y1 * sine[pitch] >> 16;
		int z1 = y1 * cosine[pitch] + z * sine[pitch] >> 16;
		return new int[]{w / 2 + x1 * scale / z1, h / 2 + y2 * scale / z1};
	}

	@Test
	public void matchesRuneLiteProjection()
	{
		double jau = 2 * Math.PI / 16384;
		int camX = 6000, camY = 5200, camZ = -1500, pitch = 1024, yaw = 3000, scale = 512;
		Projector p = new Projector(camX, camY, camZ, pitch * jau, yaw * jau, scale, 765, 503, 0, 0);
		int[][] points = {{6400, 6900, -200}, {5800, 7100, -250}, {7000, 7600, 0}, {6100, 6300, -100}};
		for (int[] pt : points)
		{
			double[] mine = p.project(pt[0], pt[1], pt[2]);
			int[] theirs = runeLiteCpu(camX, camY, camZ, pitch, yaw, scale, 765, 503, pt[0], pt[1], pt[2]);
			// RuneLite's version rounds at every step, which adds up to a few pixels far from the middle
			assertEquals(theirs[0], mine[0], Math.max(2, Math.abs(theirs[0]) * 0.002));
			assertEquals(theirs[1], mine[1], Math.max(2, Math.abs(theirs[1]) * 0.002));
		}
	}

	@Test
	public void pointBehindTheCameraIsNotDrawn()
	{
		Projector p = new Projector(0, 0, -1000, 0.5, 0, 512, 800, 600, 0, 0);
		assertNull(p.project(0, -2000, 0));
	}

	@Test
	public void viewportOffsetIsAdded()
	{
		Projector a = new Projector(0, 0, -1000, 0.5, 0, 512, 800, 600, 0, 0);
		Projector b = new Projector(0, 0, -1000, 0.5, 0, 512, 800, 600, 4, 7);
		double[] pa = a.project(100, 1500, 0);
		double[] pb = b.project(100, 1500, 0);
		assertArrayEquals(new double[]{pa[0] + 4, pa[1] + 7}, pb, 1e-9);
	}

	@Test
	public void sameOffsetFromTheCameraLandsOnTheSamePixel()
	{
		// what the ghost markers rely on: shift the camera and the point together and the canvas point stays put
		Projector here = new Projector(6000, 6000, -1500, 0.6, 1.1, 600, 800, 600, 0, 0);
		Projector there = new Projector(6000 + 128 * 50, 6000 - 128 * 30, -1500, 0.6, 1.1, 600, 800, 600, 0, 0);
		double[] a = here.project(6300, 6900, -100);
		double[] b = there.project(6300 + 128 * 50, 6900 - 128 * 30, -100);
		assertArrayEquals(a, b, 1e-6);
		assertTrue(a[0] > 0);
	}
}
