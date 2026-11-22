package com.teleportpretile.data;

import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import lombok.Value;

/** One teleport from the bundled data: how it is started and where it lands. Immutable. */
@Value
public class Teleport
{
	public enum Kind
	{
		/** Used from anywhere: spells, items. */
		TELEPORT,
		/** A stop of a network (fairy rings, spirit trees, ...), reached from any other stop. */
		NETWORK,
		/** Boarded at one place ({@link #hasFrom()}): levers, portals, boats, charter ships, the house's rides. */
		RIDE
	}

	String id;
	String name;
	String category;
	int x;
	int y;
	int plane;
	/** Roughly how long the teleport takes, in game ticks. */
	int ticks;
	/**
	 * True when the data says the teleport lands on a random tile around the destination (the destination is then
	 * the middle of that area); false lands exactly on the destination.
	 */
	boolean randomLanding;
	/**
	 * How many whole tiles from the destination the landing can be: 0 for an exact landing, -1 for a zone whose size
	 * the data doesn't know yet.
	 */
	int maxOffset;
	MenuSpec menu;
	/** Item ids that can start it (empty when the data names the item only by its name). */
	Set<Integer> itemIds;
	List<Condition> conditions;
	Kind kind;
	/** Where a ride is boarded; {@link #hasFrom()} is false when it can be started anywhere. */
	int fromX;
	int fromY;
	int fromPlane;
	/**
	 * Where in the player's house this lands ({@link HouseLayout#PORTAL} or {@link HouseLayout#GARDEN}), or null for a
	 * fixed destination. The house is an instance: its tiles are only known from the player's last visit.
	 */
	String houseEntry;

	public boolean isHouse()
	{
		return houseEntry != null;
	}

	public boolean hasFrom()
	{
		return fromX > 0;
	}

	/**
	 * True when the data says the landing can be {@code tiles} or more tiles from the destination. A zone of unknown
	 * size isn't, until the data knows its size.
	 */
	public boolean couldBeOffBy(int tiles)
	{
		return randomLanding && maxOffset >= 0 && maxOffset >= tiles;
	}

	public boolean conditionsMet(IntUnaryOperator varbits)
	{
		for (Condition c : conditions)
		{
			if (!c.test(varbits))
			{
				return false;
			}
		}
		return true;
	}
}
