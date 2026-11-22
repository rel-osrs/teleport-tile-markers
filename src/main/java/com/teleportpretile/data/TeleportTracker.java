package com.teleportpretile.data;

import lombok.Getter;

/**
 * Follows one teleport or ride from the click to the arrival, tick by tick. The plugin feeds it the player's tile and
 * animation each game tick; it says when the player has arrived, or when the trip didn't happen after all.
 *
 * <p>The player has arrived when they move more than {@link #JUMP_TILES} in one tick or change plane: walking and
 * running move at most 2 tiles a tick.
 * <ul>
 * <li>A teleport ({@link Teleport.Kind#TELEPORT}) must start (an animation or graphic) within {@link #START_TICKS};
 * walking off afterwards cancels it.</li>
 * <li>A ride or network trip may first walk to where it is boarded, so movement doesn't cancel it; it gives up after
 * its travel time plus {@link #WALK_TICKS}.</li>
 * </ul>
 */
public final class TeleportTracker
{
	/** Ticks after the click for a teleport's animation to start before giving up (failed or refused). */
	static final int START_TICKS = 3;
	/** Extra ticks past the data's travel time before giving up on the arrival. */
	static final int ARRIVAL_SLACK_TICKS = 6;
	/** Ticks a ride may spend walking to where it is boarded. */
	static final int WALK_TICKS = 30;
	/** A move of more tiles than this in one tick is the landing; walking and running move at most 2. */
	static final int JUMP_TILES = 3;

	public enum Result
	{
		/** Still on the way. */
		WAITING,
		/** The player has landed: see {@link #getArrivalX()}. */
		ARRIVED,
		/** The trip didn't happen, was interrupted or took far too long. */
		CANCELLED
	}

	@Getter
	private final Teleport teleport;
	private final int startTick;
	private final int baselineAnimation;
	private int lastX;
	private int lastY;
	private int lastPlane;
	@Getter
	private boolean started;
	@Getter
	private int arrivalX;
	@Getter
	private int arrivalY;
	@Getter
	private int arrivalPlane;

	/**
	 * @param animation the player's animation at the click, so an animation already playing (skilling, say) isn't
	 *                  taken for a teleport starting
	 */
	public TeleportTracker(Teleport teleport, int tick, int x, int y, int plane, int animation)
	{
		this.teleport = teleport;
		this.startTick = tick;
		this.baselineAnimation = animation;
		this.lastX = x;
		this.lastY = y;
		this.lastPlane = plane;
		// a ride or network trip has nothing to wait for: it is shown from the click
		this.started = teleport.getKind() != Teleport.Kind.TELEPORT;
	}

	/**
	 * One game tick.
	 *
	 * @param animation the player's animation (-1 for none)
	 * @param spotAnimChanged true when a graphic started on the player since the click
	 */
	public Result tick(int tick, int x, int y, int plane, int animation, boolean spotAnimChanged)
	{
		int moved = Math.max(Math.abs(x - lastX), Math.abs(y - lastY));
		boolean planeChanged = plane != lastPlane;
		lastX = x;
		lastY = y;
		lastPlane = plane;
		if (planeChanged || moved > JUMP_TILES)
		{
			arrivalX = x;
			arrivalY = y;
			arrivalPlane = plane;
			return Result.ARRIVED;
		}
		int elapsed = tick - startTick;
		if (teleport.getKind() != Teleport.Kind.TELEPORT)
		{
			return elapsed > Math.max(teleport.getTicks(), 1) + WALK_TICKS + ARRIVAL_SLACK_TICKS
				? Result.CANCELLED : Result.WAITING;
		}
		if (!started)
		{
			if ((animation != -1 && animation != baselineAnimation) || spotAnimChanged)
			{
				started = true;
			}
			else if (elapsed > START_TICKS)
			{
				return Result.CANCELLED;
			}
			// still running to a stop after the click
			return Result.WAITING;
		}
		if (moved > 0)
		{
			// walked off mid-cast (a home teleport can be interrupted)
			return Result.CANCELLED;
		}
		if (elapsed > Math.max(teleport.getTicks(), 1) + ARRIVAL_SLACK_TICKS)
		{
			return Result.CANCELLED;
		}
		return Result.WAITING;
	}
}
