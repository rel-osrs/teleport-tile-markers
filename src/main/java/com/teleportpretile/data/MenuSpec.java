package com.teleportpretile.data;

import lombok.Value;

/**
 * What the player clicks to start a teleport, as Best Path's data writes it:
 * <ul>
 * <li>{@code v|Cast|Varrock Teleport}: the menu option and target.</li>
 * <li>{@code n|Edgeville|Amulet of glory}: the same, where the option names the destination.</li>
 * <li>{@code c|Rub|Amulet of glory|Edgeville}: the click opens a dialog, list or sub-menu, then the choice is picked.
 * Worn items often show the choice straight away as a menu option ("Edgeville Amulet of glory").</li>
 * </ul>
 */
@Value
public class MenuSpec
{
	public enum Style
	{
		VERB, NAMED, CHOICE
	}

	Style style;
	String option;
	String target;
	/** {@link Style#CHOICE} only, else null. */
	String choice;
	/**
	 * {@link Style#CHOICE} only: the keyboard key that picks the choice where the game's list fixes it ("5" in the
	 * house jewellery box, "A" for a spirit tree), else null.
	 */
	String key;

	public static MenuSpec parse(String s)
	{
		String[] p = s.split("\\|", -1);
		if (p.length < 3)
		{
			throw new IllegalArgumentException("menu text needs at least 3 parts: " + s);
		}
		switch (p[0])
		{
			case "v":
				return new MenuSpec(Style.VERB, p[1], p[2], null, null);
			case "n":
				return new MenuSpec(Style.NAMED, p[1], p[2], null, null);
			case "c":
				// "c|||BLQ": a stop known only by its choice (reached by the fairy ring dials, not a click)
				if (p.length < 4 || p[3].isEmpty())
				{
					throw new IllegalArgumentException("choice menu text has no choice: " + s);
				}
				String key = p.length > 4 && !p[4].isEmpty() ? p[4] : null;
				return new MenuSpec(Style.CHOICE, p[1], p[2], p[3], key);
			default:
				throw new IllegalArgumentException("unknown menu text style: " + s);
		}
	}
}
