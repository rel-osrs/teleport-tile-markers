package com.teleportpretile.data;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cleans menu and widget text so it can be compared with the teleport data. */
public final class Text
{
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	/**
	 * "1: Edgeville" (the game's lists label entries 1-9 and then letters: "A: Edgeville"), and also "1. Edgeville" or
	 * "[1] Edgeville".
	 */
	private static final Pattern NUMBERING = Pattern.compile("^\\[?([0-9]+|[A-Z])[\\].:)]\\s+");
	/** "(4)", "(t4)", "(i)", "(uncharged)" after an item name. */
	private static final Pattern SUFFIX = Pattern.compile("\\s*\\([^)]*\\)\\s*$");
	/** A tier number at the end of an item name: "Desert amulet 3". */
	private static final Pattern TIER = Pattern.compile("\\s+\\d+$");

	private Text()
	{
	}

	/** Removes colour and image tags, swaps non-breaking spaces for spaces and trims. Never null. */
	public static String clean(String s)
	{
		if (s == null)
		{
			return "";
		}
		return TAGS.matcher(s).replaceAll("").replace(' ', ' ').trim();
	}

	/** {@link #clean(String)} and then lower case, for comparing. */
	public static String key(String s)
	{
		return clean(s).toLowerCase(Locale.ROOT);
	}

	/** The key label a list entry starts with ("1: Fishing Guild" gives "1", "A: Kourend" gives "A"), or null. */
	public static String listKey(String s)
	{
		java.util.regex.Matcher m = NUMBERING.matcher(clean(s));
		return m.find() ? m.group(1) : null;
	}

	/** The entry without its leading number: "1: Fishing Guild" gives "Fishing Guild". */
	public static String withoutNumbering(String s)
	{
		return NUMBERING.matcher(clean(s)).replaceFirst("");
	}

	/**
	 * True when the clicked item or spell name {@code actual} is the data's name {@code expected}, maybe with charges
	 * or a variant after it: "Amulet of glory(4)" and "Amulet of glory (t4)" are both "Amulet of glory".
	 */
	public static boolean sameName(String actual, String expected)
	{
		String a = key(actual);
		String e = key(expected);
		if (a.equals(e))
		{
			return true;
		}
		if (!a.startsWith(e))
		{
			return false;
		}
		String rest = a.substring(e.length());
		return rest.startsWith("(") || rest.startsWith(" (");
	}

	/** The name with no charges and no tier number: "Desert amulet 3" and "Desert amulet 2" are both "desert amulet". */
	public static String baseName(String s)
	{
		String k = key(s);
		String prev;
		do
		{
			prev = k;
			k = SUFFIX.matcher(k).replaceFirst("");
			k = TIER.matcher(k).replaceFirst("");
		}
		while (!k.equals(prev));
		return k;
	}

	/**
	 * True when a dialog or list entry is the data's choice text ("Edgeville", "2. Edgeville"), or says it as a whole
	 * phrase ("Travel to Bedabin Camp").
	 */
	public static boolean sameChoice(String entry, String choice)
	{
		String a = key(withoutNumbering(entry));
		String c = key(choice);
		if (a.isEmpty() || c.isEmpty())
		{
			return false;
		}
		if (a.equals(c))
		{
			return true;
		}
		// "Edgeville." or "Edgeville (Wilderness level 1)": the choice then something that isn't a letter or digit
		if (a.startsWith(c) && !Character.isLetterOrDigit(a.charAt(c.length())))
		{
			return true;
		}
		// "Travel to Bedabin Camp": the choice as a whole phrase inside the entry
		for (int i = a.indexOf(c); i >= 0; i = a.indexOf(c, i + 1))
		{
			boolean startOk = i == 0 || !Character.isLetterOrDigit(a.charAt(i - 1));
			int end = i + c.length();
			boolean endOk = end == a.length() || !Character.isLetterOrDigit(a.charAt(end));
			if (startOk && endOk)
			{
				return true;
			}
		}
		return false;
	}
}
