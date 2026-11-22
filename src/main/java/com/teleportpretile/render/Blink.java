package com.teleportpretile.render;

/**
 * Timing of the blink that shows the player the plugin just learned something on this trip (the house's layout, what
 * a "last destination" means): {@link #TIMES} flashes, each {@link #ON_MILLIS} shown then {@link #OFF_MILLIS} hidden.
 */
public final class Blink
{
	public static final int TIMES = 3;
	public static final long ON_MILLIS = 700;
	public static final long OFF_MILLIS = 500;

	private final long startMillis;

	public Blink(long startMillis)
	{
		this.startMillis = startMillis;
	}

	/** True while a flash is showing at this time. */
	public boolean visible(long nowMillis)
	{
		long t = nowMillis - startMillis;
		if (t < 0 || isOver(nowMillis))
		{
			return false;
		}
		return t % (ON_MILLIS + OFF_MILLIS) < ON_MILLIS;
	}

	public boolean isOver(long nowMillis)
	{
		return nowMillis - startMillis >= TIMES * (ON_MILLIS + OFF_MILLIS);
	}
}
