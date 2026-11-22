package com.teleportpretile.data;

import net.runelite.api.gameval.VarbitID;

/**
 * Items with a "last destination" option. The game keeps the last place in a varbit, but the varbit is set by the
 * server and nothing in the client says which value means which place. So the plugin learns it from play: each time
 * the player lands from one of the item's ordinary teleports, the varbit's value then is that teleport's destination.
 * The quetzal's value also changes when the player rides the quetzal network; a value learned once keeps working.
 */
public enum LastDestination
{
	/** quetzal_last_destination; "Last-destination" on every quetzal whistle. */
	QUETZAL_WHISTLE(VarbitID.QUETZAL_LAST_DESTINATION, "Last-destination", "quetzal whistle"),
	/** ring_of_elements_last_destination; "Last Destination" in the inventory and when worn. */
	RING_OF_THE_ELEMENTS(VarbitID.RING_OF_ELEMENTS_LAST_DESTINATION, "Last Destination", "ring of the elements"),
	/** pharaohs_sceptre_last_teleport; "Last-Teleport". */
	PHARAOHS_SCEPTRE(VarbitID.PHARAOHS_SCEPTRE_LAST_TELEPORT, "Last-Teleport", "pharaoh's sceptre");

	private final int varbit;
	private final String option;
	/** Part of the item's name, in lower case; the item's teleports name it in their menu target. */
	private final String item;

	LastDestination(int varbit, String option, String item)
	{
		this.varbit = varbit;
		this.option = option;
		this.item = item;
	}

	public int getVarbit()
	{
		return varbit;
	}

	/** The item whose "last destination" option this click is, or null. */
	public static LastDestination clicked(String option, String target)
	{
		String opt = Text.key(option);
		String tgt = Text.key(target);
		for (LastDestination d : values())
		{
			if (d.option.equalsIgnoreCase(opt) && tgt.contains(d.item))
			{
				return d;
			}
		}
		return null;
	}

	/** The item this teleport belongs to, or null when it isn't one of these items' teleports. */
	public static LastDestination of(Teleport t)
	{
		String tgt = Text.key(t.getMenu().getTarget());
		for (LastDestination d : values())
		{
			if (tgt.contains(d.item))
			{
				return d;
			}
		}
		return null;
	}

	/** The config key holding the teleport id for one varbit value. */
	public String key(int value)
	{
		return name() + "." + value;
	}
}
