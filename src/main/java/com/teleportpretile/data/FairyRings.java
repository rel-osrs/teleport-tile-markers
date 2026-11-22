package com.teleportpretile.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fairy ring codes. The dials are set in the fairy ring interface and the code is sent with its Confirm button, so
 * the code is read from the dial varbits at that click, the way RuneLite's Fairy Ring plugin reads it.
 */
public final class FairyRings
{
	/** The letter each dial value shows (varbits FAIRYRING_1, FAIRYRING_2, FAIRYRING_3), as in RuneLite's plugin. */
	private static final String[] LEFT = {"A", "D", "C", "B"};
	private static final String[] MIDDLE = {"I", "L", "K", "J"};
	private static final String[] RIGHT = {"P", "S", "R", "Q"};
	private static final Pattern CODE = Pattern.compile("^[ABCD][IJKL][PQRS]$");
	/** "Last-destination (CKS)" on a fairy ring. */
	private static final Pattern LAST = Pattern.compile("\\(([A-Da-d][I-Li-l][P-Sp-s])\\)");

	/** The ring Zanaris is reached by ("Zanaris" on a fairy ring). */
	public static final String ZANARIS = "BKS";

	private FairyRings()
	{
	}

	/** The code the dials show, or null for a dial value out of range. */
	public static String code(int left, int middle, int right)
	{
		if (left < 0 || left > 3 || middle < 0 || middle > 3 || right < 0 || right > 3)
		{
			return null;
		}
		return LEFT[left] + MIDDLE[middle] + RIGHT[right];
	}

	public static boolean isCode(String s)
	{
		return s != null && CODE.matcher(s.toUpperCase()).matches();
	}

	/**
	 * The code a click on a fairy ring goes to without the dials: "Last-destination (CKS)" or "Zanaris"; null for
	 * any other click.
	 */
	public static String codeOfOption(String option, String target)
	{
		if (!Text.key(target).equals("fairy ring"))
		{
			return null;
		}
		String opt = Text.clean(option);
		if (opt.equalsIgnoreCase("Zanaris"))
		{
			return ZANARIS;
		}
		Matcher m = LAST.matcher(opt);
		return opt.toLowerCase().startsWith("last-destination") && m.find() ? m.group(1).toUpperCase() : null;
	}
}
