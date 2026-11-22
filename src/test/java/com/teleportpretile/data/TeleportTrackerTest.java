package com.teleportpretile.data;

import static com.teleportpretile.data.TeleportTracker.Result.ARRIVED;
import static com.teleportpretile.data.TeleportTracker.Result.CANCELLED;
import static com.teleportpretile.data.TeleportTracker.Result.WAITING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import org.junit.Test;

public class TeleportTrackerTest
{
	private static final int TELEPORT_ANIM = 714;

	private static TeleportTracker tracker(int ticks, int animationAtClick)
	{
		Teleport t = new Teleport("t", "Test", "spell", 3213, 3424, 0, ticks, false, 0, MenuSpec.parse("v|Cast|Test"),
			Collections.emptySet(), Collections.emptyList(), Teleport.Kind.TELEPORT, 0, 0, 0, null);
		return new TeleportTracker(t, 10, 3000, 3000, 0, animationAtClick);
	}

	private static TeleportTracker ride(int ticks)
	{
		Teleport t = new Teleport("r", "Lever", "ride", 3154, 3924, 0, ticks, false, 0, MenuSpec.parse("v|Pull|Lever"),
			Collections.emptySet(), Collections.emptyList(), Teleport.Kind.RIDE, 2561, 3311, 0, null);
		return new TeleportTracker(t, 10, 2570, 3311, 0, -1);
	}

	@Test
	public void rideMayWalkToTheBoardingSpotFirst()
	{
		TeleportTracker tr = ride(4);
		assertTrue(tr.isStarted());
		// walking (2 tiles a tick at a run) and standing still don't cancel it, and need no animation
		assertEquals(WAITING, tr.tick(11, 2568, 3311, 0, -1, false));
		assertEquals(WAITING, tr.tick(12, 2566, 3311, 0, -1, false));
		assertEquals(WAITING, tr.tick(13, 2564, 3311, 0, -1, false));
		assertEquals(WAITING, tr.tick(16, 2562, 3311, 0, 2140, false));
		assertEquals(ARRIVED, tr.tick(18, 3154, 3924, 0, -1, false));
	}

	@Test
	public void rideGivesUpAfterTheWalkingAllowance()
	{
		TeleportTracker tr = ride(4);
		int last = 10 + 4 + TeleportTracker.WALK_TICKS + TeleportTracker.ARRIVAL_SLACK_TICKS;
		assertEquals(WAITING, tr.tick(last, 2570, 3311, 0, -1, false));
		assertEquals(CANCELLED, tr.tick(last + 1, 2570, 3311, 0, -1, false));
	}

	@Test
	public void normalTeleport()
	{
		TeleportTracker tr = tracker(4, -1);
		assertEquals(WAITING, tr.tick(11, 3000, 3000, 0, TELEPORT_ANIM, false));
		assertTrue(tr.isStarted());
		assertEquals(WAITING, tr.tick(12, 3000, 3000, 0, TELEPORT_ANIM, false));
		assertEquals(ARRIVED, tr.tick(14, 3214, 3423, 0, -1, false));
		assertEquals(3214, tr.getArrivalX());
		assertEquals(3423, tr.getArrivalY());
	}

	@Test
	public void teleportThatNeverStarts()
	{
		TeleportTracker tr = tracker(4, -1);
		for (int tick = 11; tick <= 13; tick++)
		{
			assertEquals(WAITING, tr.tick(tick, 3000, 3000, 0, -1, false));
		}
		assertEquals(CANCELLED, tr.tick(14, 3000, 3000, 0, -1, false));
	}

	@Test
	public void animationAlreadyPlayingIsNotTheTeleport()
	{
		TeleportTracker tr = tracker(4, 624);
		tr.tick(11, 3000, 3000, 0, 624, false);
		assertTrue(!tr.isStarted());
		tr.tick(12, 3000, 3000, 0, 624, true);
		assertTrue(tr.isStarted());
	}

	@Test
	public void runningToAStopBeforeTheCastIsNotWalkingOff()
	{
		TeleportTracker tr = tracker(4, -1);
		assertEquals(WAITING, tr.tick(11, 3002, 3000, 0, -1, false));
		assertEquals(WAITING, tr.tick(12, 3002, 3000, 0, TELEPORT_ANIM, false));
		assertEquals(ARRIVED, tr.tick(15, 3213, 3424, 0, -1, false));
	}

	@Test
	public void walkingOffInterruptsAHomeTeleport()
	{
		TeleportTracker tr = tracker(24, -1);
		tr.tick(11, 3000, 3000, 0, 4847, false);
		assertEquals(CANCELLED, tr.tick(12, 3001, 3000, 0, -1, false));
	}

	@Test
	public void givesUpWhenTheArrivalNeverComes()
	{
		TeleportTracker tr = tracker(4, -1);
		tr.tick(11, 3000, 3000, 0, TELEPORT_ANIM, false);
		assertEquals(WAITING, tr.tick(20, 3000, 3000, 0, TELEPORT_ANIM, false));
		assertEquals(CANCELLED, tr.tick(21, 3000, 3000, 0, TELEPORT_ANIM, false));
	}

	@Test
	public void planeChangeIsAnArrival()
	{
		TeleportTracker tr = tracker(4, -1);
		tr.tick(11, 3000, 3000, 0, TELEPORT_ANIM, false);
		assertEquals(ARRIVED, tr.tick(13, 3000, 3000, 1, -1, false));
	}
}
