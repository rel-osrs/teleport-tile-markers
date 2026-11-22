package com.teleportpretile.data;

import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.gameval.VarbitID;

/**
 * A requirement the plugin can check from varbits, used to pick between teleports that share a click (the Arceuus
 * Respawn Teleport goes wherever the respawn point is; Teleport to House goes to the house's portal).
 */
@Value
public class Condition
{
	/** Varbit SPELLBOOK: 0 standard, 1 ancient, 2 lunar, 3 arceuus. */
	static final int SPELLBOOK_VARBIT = VarbitID.SPELLBOOK;
	/** Varbit POH_HOUSE_LOCATION. */
	static final int HOUSE_VARBIT = VarbitID.POH_HOUSE_LOCATION;

	private static final Pattern VARBIT = Pattern.compile("^varbit (\\d+) (=|!=|<|<=|>|>=) (-?\\d+)$");
	private static final String[] SPELLBOOKS = {"standard", "ancient", "lunar", "arceuus"};
	/** Varbit 2187 values to house places (the game's enum 252), as Best Path names them. */
	private static final String[] HOUSES = new String[14];

	static
	{
		HOUSES[1] = "rimmington";
		HOUSES[2] = "taverley";
		HOUSES[3] = "pollnivneach";
		HOUSES[4] = "rellekka";
		HOUSES[5] = "brimhaven";
		HOUSES[6] = "yanille";
		HOUSES[8] = "hosidius";
		HOUSES[9] = "prifddinas";
		HOUSES[13] = "aldarin";
	}

	int varbit;
	String op;
	int value;

	/** Parses one condition from the data; null when the plugin can't check it. */
	public static Condition parse(String s)
	{
		String t = s.trim().toLowerCase();
		Matcher m = VARBIT.matcher(t);
		if (m.matches())
		{
			return new Condition(Integer.parseInt(m.group(1)), m.group(2), Integer.parseInt(m.group(3)));
		}
		if (t.startsWith("spellbook "))
		{
			int book = indexOf(SPELLBOOKS, t.substring("spellbook ".length()));
			return book < 0 ? null : new Condition(SPELLBOOK_VARBIT, "=", book);
		}
		if (t.startsWith("house at "))
		{
			int house = indexOf(HOUSES, t.substring("house at ".length()));
			return house < 0 ? null : new Condition(HOUSE_VARBIT, "=", house);
		}
		return null;
	}

	private static int indexOf(String[] names, String name)
	{
		for (int i = 0; i < names.length; i++)
		{
			if (name.equals(names[i]))
			{
				return i;
			}
		}
		return -1;
	}

	public boolean test(IntUnaryOperator varbits)
	{
		int v = varbits.applyAsInt(varbit);
		switch (op)
		{
			case "=":
				return v == value;
			case "!=":
				return v != value;
			case "<":
				return v < value;
			case "<=":
				return v <= value;
			case ">":
				return v > value;
			case ">=":
				return v >= value;
			default:
				return true;
		}
	}
}
