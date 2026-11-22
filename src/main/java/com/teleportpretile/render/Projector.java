package com.teleportpretile.render;

/**
 * Projects a point in the game's local 3D coordinates to the canvas, with the same maths as RuneLite's
 * {@code Perspective.localToCanvas} but with no scene bounds check: the ghost markers sit where the destination's
 * tiles will be once the player has landed, which can be outside the scene that is loaded now.
 */
public final class Projector
{
	private final double camX;
	private final double camY;
	private final double camZ;
	private final double pitchSin;
	private final double pitchCos;
	private final double yawSin;
	private final double yawCos;
	private final int scale;
	private final int viewportWidth;
	private final int viewportHeight;
	private final int viewportX;
	private final int viewportY;

	/**
	 * @param pitch camera pitch in radians
	 * @param yaw   camera yaw in radians
	 */
	public Projector(double camX, double camY, double camZ, double pitch, double yaw, int scale,
		int viewportWidth, int viewportHeight, int viewportX, int viewportY)
	{
		this.camX = camX;
		this.camY = camY;
		this.camZ = camZ;
		this.pitchSin = Math.sin(pitch);
		this.pitchCos = Math.cos(pitch);
		this.yawSin = Math.sin(yaw);
		this.yawCos = Math.cos(yaw);
		this.scale = scale;
		this.viewportWidth = viewportWidth;
		this.viewportHeight = viewportHeight;
		this.viewportX = viewportX;
		this.viewportY = viewportY;
	}

	/** Canvas {x, y} of local point (x, y) at height z, or null when it is behind or too close to the camera. */
	public double[] project(double x, double y, double z)
	{
		double fx = x - camX;
		double fy = y - camY;
		double fz = z - camZ;
		double x1 = fx * yawCos + fy * yawSin;
		double y1 = fy * yawCos - fx * yawSin;
		double y2 = fz * pitchCos - y1 * pitchSin;
		double z1 = y1 * pitchCos + fz * pitchSin;
		if (z1 < 50)
		{
			return null;
		}
		return new double[]{
			viewportWidth / 2.0 + x1 * scale / z1 + viewportX,
			viewportHeight / 2.0 + y2 * scale / z1 + viewportY
		};
	}
}
