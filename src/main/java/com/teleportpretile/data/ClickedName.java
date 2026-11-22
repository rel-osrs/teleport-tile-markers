package com.teleportpretile.data;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * The name of what a click was on. Entries in a right-click sub-menu ("Rub" then "Edgeville" on a glory in the
 * inventory, "Teleports" then a place on a worn Max cape, "Favourites" then a code on a fairy ring) come with an
 * empty target, so the name is found from the clicked item, interface slot, object or NPC instead.
 */
public final class ClickedName
{
	private ClickedName()
	{
	}

	/**
	 * The menu target when it has text, else the first of the others that has: the item's name, the interface slot's
	 * name, the object's name, the NPC's name. Each is only asked for when needed; any may give null.
	 */
	public static String of(String target, Supplier<String> item, Supplier<String> widget, Supplier<String> object,
		Supplier<String> npc)
	{
		if (!Text.clean(target).isEmpty())
		{
			return target;
		}
		for (Supplier<String> s : Arrays.asList(item, widget, object, npc))
		{
			String name = s.get();
			if (!Text.clean(name).isEmpty())
			{
				return name;
			}
		}
		return target == null ? "" : target;
	}
}
