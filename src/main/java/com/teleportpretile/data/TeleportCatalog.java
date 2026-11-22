package com.teleportpretile.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** The bundled teleports and the lookups that turn a click into a teleport. Immutable. */
public final class TeleportCatalog
{
	/** Teleports that share a click count as one place when they land this close together (in tiles). */
	static final int SAME_PLACE_TILES = 8;
	/** A ride is only taken for a click this close (in tiles) to where it is boarded. */
	static final int BOARDING_TILES = 20;

	private final List<Teleport> teleports;

	private TeleportCatalog(List<Teleport> teleports)
	{
		this.teleports = Collections.unmodifiableList(teleports);
	}

	public static TeleportCatalog load(InputStream in) throws IOException
	{
		return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	/** Reads teleports.tsv: "#" comment lines, then one tab-separated teleport per line. Bad lines throw, naming the line. */
	public static TeleportCatalog parse(Reader reader) throws IOException
	{
		List<Teleport> list = new ArrayList<>();
		BufferedReader r = new BufferedReader(reader);
		String line;
		int n = 0;
		while ((line = r.readLine()) != null)
		{
			n++;
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			try
			{
				list.add(parseLine(line));
			}
			catch (RuntimeException e)
			{
				throw new IOException("teleports.tsv line " + n + ": " + e.getMessage(), e);
			}
		}
		return new TeleportCatalog(list);
	}

	private static Teleport parseLine(String line)
	{
		String[] c = line.split("\t", -1);
		if (c.length < 11)
		{
			throw new IllegalArgumentException("expected 11 columns, got " + c.length);
		}
		String houseEntry = c[3].startsWith("house:") ? c[3].substring("house:".length()) : null;
		String[] dest = houseEntry != null ? new String[]{"0", "0", "0"} : c[3].split(",");
		Set<Integer> ids = new LinkedHashSet<>();
		for (String id : c[7].split(","))
		{
			if (!id.isEmpty())
			{
				ids.add(Integer.parseInt(id.trim()));
			}
		}
		List<Condition> conditions = new ArrayList<>();
		for (String cond : c[8].split(";"))
		{
			Condition parsed = cond.isEmpty() ? null : Condition.parse(cond);
			if (parsed != null)
			{
				conditions.add(parsed);
			}
		}
		int[] from = {0, 0, 0};
		if (!c[10].trim().isEmpty())
		{
			String[] f = c[10].split(",");
			for (int i = 0; i < 3; i++)
			{
				from[i] = Integer.parseInt(f[i].trim());
			}
		}
		return new Teleport(c[0], c[1], c[2], Integer.parseInt(dest[0].trim()), Integer.parseInt(dest[1].trim()),
			Integer.parseInt(dest[2].trim()), Integer.parseInt(c[4]), !c[5].trim().isEmpty(), zoneOffset(c[5]), MenuSpec.parse(c[6]),
			Collections.unmodifiableSet(ids), Collections.unmodifiableList(conditions),
			Teleport.Kind.valueOf(c[9].trim().toUpperCase()), from[0], from[1], from[2], houseEntry);
	}

	/** The zone column: empty for an exact landing (0), "?" for a zone of unknown size (-1), else whole tiles. */
	private static int zoneOffset(String s)
	{
		String t = s.trim();
		if (t.isEmpty())
		{
			return 0;
		}
		if (t.equals("?"))
		{
			return -1;
		}
		int n = Integer.parseInt(t);
		if (n < 1)
		{
			throw new IllegalArgumentException("zone must be empty, ? or at least 1, got '" + s + "'");
		}
		return n;
	}

	public List<Teleport> all()
	{
		return teleports;
	}

	/** The teleport with this id, or null. */
	public Teleport byId(String id)
	{
		for (Teleport t : teleports)
		{
			if (t.getId().equals(id))
			{
				return t;
			}
		}
		return null;
	}

	/**
	 * Teleports this click starts straight away: a verb or named click, or a worn item's option that is the
	 * choice itself ("Edgeville" on a worn Amulet of glory).
	 */
	public List<Teleport> started(String option, String target, int itemId)
	{
		String opt = Text.key(option);
		return best(target, itemId, t ->
		{
			MenuSpec m = t.getMenu();
			String wanted = m.getStyle() == MenuSpec.Style.CHOICE ? m.getChoice() : m.getOption();
			return Text.key(wanted).equals(opt);
		});
	}

	/**
	 * Teleports whose dialog or list this click opens ("Rub" on an Amulet of glory); one is picked next. A click on
	 * any stop of a network opens the whole network: the game names some stops differently ("Spirit tree" and
	 * "Spirit Tree").
	 */
	public List<Teleport> opened(String option, String target, int itemId)
	{
		String opt = Text.key(option);
		List<Teleport> found = best(target, itemId, t -> t.getMenu().getStyle() == MenuSpec.Style.CHOICE
			&& Text.key(t.getMenu().getOption()).equals(opt));
		Set<String> networks = new LinkedHashSet<>();
		for (Teleport t : found)
		{
			if (t.getKind() == Teleport.Kind.NETWORK)
			{
				networks.add(t.getCategory());
			}
		}
		if (networks.isEmpty())
		{
			return found;
		}
		List<Teleport> out = new ArrayList<>(found);
		for (Teleport t : teleports)
		{
			if (t.getKind() == Teleport.Kind.NETWORK && networks.contains(t.getCategory()) && !out.contains(t))
			{
				out.add(t);
			}
		}
		return out;
	}

	/** Of the teleports a click {@link #opened(String, String, int)}, those this dialog or list entry picks. */
	public static List<Teleport> chosen(List<Teleport> opened, String entry)
	{
		List<Teleport> out = new ArrayList<>();
		for (Teleport t : opened)
		{
			if (Text.sameChoice(entry, t.getMenu().getChoice()))
			{
				out.add(t);
			}
		}
		return out;
	}

	/**
	 * Teleports with the right option whose target matches the click, keeping only the closest kind of match:
	 * the clicked item's id, then the exact name, the name in any letter case, the name with a word in front (the
	 * house's "Ornate Jewellery Box" for the data's "Jewellery Box"), and last the name without its tier number
	 * ("Desert amulet 3" for the data's "Desert amulet 2").
	 */
	private List<Teleport> best(String target, int itemId, Predicate<Teleport> optionMatches)
	{
		List<List<Teleport>> tiers = new ArrayList<>();
		for (int i = 0; i < 5; i++)
		{
			tiers.add(new ArrayList<>());
		}
		String base = Text.baseName(target);
		for (Teleport t : teleports)
		{
			if (!optionMatches.test(t))
			{
				continue;
			}
			String expected = t.getMenu().getTarget();
			if (itemId > 0 && t.getItemIds().contains(itemId))
			{
				tiers.get(0).add(t);
			}
			else if (Text.sameName(target, expected) && Text.clean(target).startsWith(Text.clean(expected)))
			{
				tiers.get(1).add(t);
			}
			else if (Text.sameName(target, expected))
			{
				tiers.get(2).add(t);
			}
			else if (!Text.key(expected).isEmpty() && Text.key(target).endsWith(" " + Text.key(expected)))
			{
				tiers.get(3).add(t);
			}
			else if (!base.isEmpty() && base.equals(Text.baseName(expected)))
			{
				tiers.get(4).add(t);
			}
		}
		for (List<Teleport> tier : tiers)
		{
			if (!tier.isEmpty())
			{
				return tier;
			}
		}
		return Collections.emptyList();
	}

	/**
	 * These candidates with the rides boarded somewhere else left out: a ride counts only when the click is within
	 * {@link #BOARDING_TILES} of where it is boarded, and only the nearest boarding place counts. Teleports and network
	 * stops, which have no boarding place, are kept.
	 */
	public static List<Teleport> near(List<Teleport> candidates, int x, int y, int plane)
	{
		int best = Integer.MAX_VALUE;
		for (Teleport t : candidates)
		{
			if (t.hasFrom() && t.getFromPlane() == plane)
			{
				best = Math.min(best, Math.max(Math.abs(t.getFromX() - x), Math.abs(t.getFromY() - y)));
			}
		}
		List<Teleport> out = new ArrayList<>();
		for (Teleport t : candidates)
		{
			if (!t.hasFrom())
			{
				out.add(t);
			}
			else if (best <= BOARDING_TILES && t.getFromPlane() == plane
				&& Math.max(Math.abs(t.getFromX() - x), Math.abs(t.getFromY() - y)) == best)
			{
				out.add(t);
			}
		}
		return out;
	}

	/** Of the teleports a click opened, those whose choice the game's list puts on this key (the data's fixed keys). */
	public static List<Teleport> chosenByKey(List<Teleport> opened, char key)
	{
		List<Teleport> out = new ArrayList<>();
		for (Teleport t : opened)
		{
			String k = t.getMenu().getKey();
			if (k != null && k.length() == 1 && Character.toUpperCase(k.charAt(0)) == Character.toUpperCase(key))
			{
				out.add(t);
			}
		}
		return out;
	}

	/** The fairy ring stops with this code ("CKS"). */
	public List<Teleport> fairyRing(String code)
	{
		List<Teleport> out = new ArrayList<>();
		for (Teleport t : teleports)
		{
			MenuSpec m = t.getMenu();
			if (t.getKind() == Teleport.Kind.NETWORK && m.getStyle() == MenuSpec.Style.CHOICE
				&& m.getChoice().equalsIgnoreCase(code) && FairyRings.isCode(m.getChoice()))
			{
				out.add(t);
			}
		}
		return out;
	}

	/**
	 * The one teleport of these the player is using: those whose varbit conditions hold, as long as they all land
	 * in about the same place. Null when none fits or they go to different places (the plugin then shows nothing
	 * rather than guess).
	 */
	public static Teleport resolve(List<Teleport> candidates, IntUnaryOperator varbits)
	{
		Teleport first = null;
		for (Teleport t : candidates)
		{
			if (!t.conditionsMet(varbits))
			{
				continue;
			}
			if (first == null)
			{
				first = t;
			}
			else if (t.getPlane() != first.getPlane()
				|| Math.max(Math.abs(t.getX() - first.getX()), Math.abs(t.getY() - first.getY())) > SAME_PLACE_TILES)
			{
				return null;
			}
		}
		return first;
	}
}
