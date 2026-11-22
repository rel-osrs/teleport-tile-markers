package com.teleportpretile.render;

/** When a colour override applies to a marker, by how opaque the marker's own outline is. */
public enum OverrideWhen
{
	/** Every marker, whatever its opacity: the threshold number is ignored. */
	ANY("Any"),
	ABOVE("Above"),
	BELOW("Below"),
	EXACTLY("Exactly");

	private final String name;

	OverrideWhen(String name)
	{
		this.name = name;
	}

	/**
	 * True when an outline's alpha is above, below or exactly the threshold (always, for {@link #ANY}); both are
	 * 0-255, as in the colour pickers.
	 */
	public boolean matches(int alpha, int threshold)
	{
		switch (this)
		{
			case ANY:
				return true;
			case ABOVE:
				return alpha > threshold;
			case BELOW:
				return alpha < threshold;
			case EXACTLY:
			default:
				return alpha == threshold;
		}
	}

	@Override
	public String toString()
	{
		return name;
	}
}
