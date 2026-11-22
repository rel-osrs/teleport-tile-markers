package com.teleportpretile.data;

import java.util.List;

/**
 * Which entry a key picks in the game's two kinds of teleport choice, as the game's scripts build them:
 * <ul>
 * <li>The chat dialog ("Select an Option", script chatbox_multi_init): child 0 of chatmenu:options is the title and
 * children 1 to 5 are the options; key "n" picks child n.</li>
 * <li>The list ("menu" interface, scripts menu_createentry and menu_createentry_indexed): each entry is a text child
 * of menu:lj_layer1 labelled with its key, "1: Fishing Guild"; keys run 1-9 and then letters.</li>
 * </ul>
 */
public final class DialogKeys
{
	private DialogKeys()
	{
	}

	/**
	 * The chat dialog option {@code key} picks, from the dialog's children by child index (null where a child has no
	 * text), or null.
	 */
	public static String chatOption(String[] byIndex, char key)
	{
		if (byIndex == null || key < '1' || key > '5')
		{
			return null;
		}
		int n = key - '0';
		return n < byIndex.length ? byIndex[n] : null;
	}

	/** The list entry labelled with {@code key}, or null. */
	public static String listEntry(List<String> entries, char key)
	{
		if (entries == null)
		{
			return null;
		}
		String k = String.valueOf(Character.toUpperCase(key));
		for (String entry : entries)
		{
			if (k.equals(Text.listKey(entry)))
			{
				return entry;
			}
		}
		return null;
	}
}
