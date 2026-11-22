package com.teleportpretile.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.BeforeClass;
import org.junit.Test;

public class TeleportCatalogTest
{
	private static TeleportCatalog catalog;

	@BeforeClass
	public static void load() throws IOException
	{
		try (InputStream in = TeleportCatalogTest.class.getResourceAsStream("/com/teleportpretile/teleports.tsv"))
		{
			assertNotNull("teleports.tsv on the classpath", in);
			catalog = TeleportCatalog.load(in);
		}
	}

	private static IntUnaryOperator varbits(int... pairs)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put(pairs[i], pairs[i + 1]);
		}
		return id -> m.getOrDefault(id, 0);
	}

	private static Teleport one(List<Teleport> candidates, IntUnaryOperator varbits)
	{
		Teleport t = TeleportCatalog.resolve(candidates, varbits);
		assertNotNull("a teleport for " + candidates, t);
		return t;
	}

	@Test
	public void bundledDataLoads()
	{
		assertTrue(catalog.all().size() > 300);
	}

	@Test
	public void inventorySubMenuWithTheItemsName()
	{
		// "Rub" then "Edgeville" in the inventory comes with no target, so the plugin passes the item's name instead
		assertTrue(catalog.started("Edgeville", "", 1712).isEmpty());
		String[] glories = {"Amulet of glory(1)", "Amulet of glory(4)", "Amulet of glory(6)", "Amulet of glory(t4)",
			"Amulet of eternal glory"};
		for (String name : glories)
		{
			Teleport t = one(catalog.started("Edgeville", name, -1), varbits());
			assertEquals(name, 3087, t.getX());
			assertEquals(name, 3496, t.getY());
		}
		Teleport karamja = one(catalog.started("Karamja", "Amulet of glory(4)", 1712), varbits());
		assertEquals(2918, karamja.getX());
	}

	@Test
	public void wornSubMenuWithTheItemsName()
	{
		// "Teleports" then a place on a worn Max cape: the item id (or, failing that, the slot's name) names the cape
		Teleport byId = one(catalog.started("Warrior's Guild", "Max cape", 13280), varbits());
		assertEquals(2865, byId.getX());
		Teleport byName = one(catalog.started("Fishing Guild", "<col=ff9040>Max cape</col>", -1), varbits());
		assertEquals(2604, byName.getX());
	}

	@Test
	public void everyChoiceIsRecognisedFromASubMenuOnceNamed()
	{
		// any sub-menu entry is its choice clicked on the item or object: each one in the data has to be found that way
		for (Teleport t : catalog.all())
		{
			MenuSpec m = t.getMenu();
			if (m.getStyle() != MenuSpec.Style.CHOICE || m.getTarget().isEmpty())
			{
				continue;
			}
			assertTrue(t.getName(), catalog.started(m.getChoice(), m.getTarget(), -1).contains(t));
		}
	}

	@Test
	public void spellCastFromTheSpellbook()
	{
		Teleport t = one(catalog.started("Cast", "<col=00ff00>Varrock Teleport</col>", -1), varbits());
		assertEquals(3213, t.getX());
		assertEquals(3424, t.getY());
		// the wiki draws its line with the template's default 3-tile square
		assertTrue(t.isRandomLanding());
		assertEquals(3, t.getMaxOffset());
	}

	@Test
	public void spellbookDecidesBetweenSameNamedSpells()
	{
		List<Teleport> ape = catalog.started("Cast", "Ape Atoll Teleport", -1);
		assertEquals(2798, one(ape, varbits(Condition.SPELLBOOK_VARBIT, 0)).getX());
		assertEquals(2771, one(ape, varbits(Condition.SPELLBOOK_VARBIT, 3)).getX());
	}

	@Test
	public void respawnTeleportFollowsTheRespawnPoint()
	{
		List<Teleport> respawn = catalog.started("Cast", "Respawn Teleport", -1);
		Teleport camelot = one(respawn, varbits(Condition.SPELLBOOK_VARBIT, 3, 3910, 1));
		// the Respawn Teleport page's own Camelot line
		assertEquals(2758, camelot.getX());
		// Lumbridge or Prifddinas depends on something varbits don't say: show nothing rather than guess
		assertNull(TeleportCatalog.resolve(respawn, varbits(Condition.SPELLBOOK_VARBIT, 3)));
	}

	@Test
	public void houseTeleportGoesToTheHousePortal()
	{
		List<Teleport> house = catalog.started("Outside", "Teleport to House", -1);
		Teleport yanille = one(house, varbits(Condition.HOUSE_VARBIT, 6));
		assertEquals(2544, yanille.getX());
	}

	@Test
	public void tablets()
	{
		Teleport rellekka = one(catalog.started("Break", "<col=ff9040>Rellekka teleport</col>", -1), varbits());
		assertEquals("tablet", rellekka.getCategory());
		// tablets with two destinations name each one as a menu option
		Teleport varrock = one(catalog.started("Varrock", "<col=ff9040>Varrock teleport</col>", 8007), varbits());
		assertEquals("tablet", varrock.getCategory());
		assertEquals(3213, varrock.getX());
	}

	@Test
	public void rubbingJewelleryOpensADialogAndTheChoicePicksTheDestination()
	{
		assertTrue(catalog.started("Rub", "<col=ff9040>Amulet of glory(4)</col>", 1712).isEmpty());
		List<Teleport> opened = catalog.opened("Rub", "<col=ff9040>Amulet of glory(4)</col>", 1712);
		assertTrue(opened.size() >= 4);
		Teleport edgeville = one(TeleportCatalog.chosen(opened, "Edgeville"), varbits());
		assertEquals(3087, edgeville.getX());
		Teleport karamja = one(TeleportCatalog.chosen(opened, "2. Karamja"), varbits());
		assertEquals(2918, karamja.getX());
		assertTrue(TeleportCatalog.chosen(opened, "Nowhere").isEmpty());
	}

	@Test
	public void wornJewelleryOptionIsTheChoice()
	{
		Teleport t = one(catalog.started("Edgeville", "<col=ff9040>Amulet of glory(t3)</col>", 10356), varbits());
		assertEquals(3087, t.getX());
	}

	@Test
	public void eternalGloryIsNotPlainGlory()
	{
		for (Teleport t : catalog.opened("Rub", "Amulet of eternal glory", 19707))
		{
			assertTrue(t.getName(), t.getName().startsWith("Amulet of eternal glory"));
		}
	}

	@Test
	public void higherTierItemUsesTheTierTeleport()
	{
		// the data names the tier 2-3 Nardah teleport after "Desert amulet 2"
		Teleport t = one(catalog.started("Teleport", "Desert amulet 3", 13135), varbits());
		assertEquals(3420, t.getX());
		Teleport byName = one(catalog.started("Teleport", "Desert amulet 3", -1), varbits());
		assertEquals(3420, byName.getX());
	}

	@Test
	public void spellOptionsKeepTheirMeaningWhateverTheLeftClickIs()
	{
		// magic_spellbook: op1 "Cast", op2 "Grand Exchange", op3 "Cast". varrock_ge_teleport only blanks op1, which makes
		// "Grand Exchange" the left click; "Cast" still means Varrock square
		IntUnaryOperator geByDefault = varbits(Condition.SPELLBOOK_VARBIT, 0, 4585, 1, 4480, 1);
		assertEquals(3213, one(catalog.started("Cast", "Varrock Teleport", -1), geByDefault).getX());
		// the Varrock Teleport page's own Grand Exchange line
		assertEquals(3165, one(catalog.started("Grand Exchange", "Varrock Teleport", -1), geByDefault).getX());
		// Seers' Village: the middle of the Camelot Teleport page's outline
		assertEquals(2727, one(catalog.started("Seers'", "Camelot Teleport", -1),
			varbits(Condition.SPELLBOOK_VARBIT, 0, 4560, 1, 4477, 1)).getX());
	}

	@Test
	public void tabletBreakGoesWhereItsToggleSays()
	{
		List<Teleport> varrock = catalog.started("Break", "Varrock teleport", 8007);
		assertEquals(3213, one(varrock, varbits(4585, 0)).getX());
		assertEquals(3165, one(varrock, varbits(4585, 1, 4480, 1)).getX());

		List<Teleport> camelot = catalog.started("Break", "Camelot teleport", 8010);
		assertEquals(2757, one(camelot, varbits(4560, 0)).getX());
		assertEquals(2727, one(camelot, varbits(4560, 1, 4477, 1)).getX());

		List<Teleport> watchtower = catalog.started("Break", "Watchtower teleport", 8012);
		assertEquals(2584, one(watchtower, varbits(4548, 1, 4460, 1)).getX());
	}

	@Test
	public void houseTabletBreakFollowsTheHouseSetting()
	{
		List<Teleport> house = catalog.started("Break", "Teleport to house", 8013);
		assertEquals(2544, one(house, varbits(Condition.HOUSE_VARBIT, 6, 4744, 1)).getX());
		// set to inside: the house, drawn from its layout as last seen
		assertTrue(one(house, varbits(Condition.HOUSE_VARBIT, 6, 4744, 0)).isHouse());
	}

	@Test
	public void subMenuDestinationsAreStartedDirectly()
	{
		// isubop: "Rub" on the ring opens a sub-menu whose entries name the altar
		assertEquals(3314, one(catalog.started("Fire Altar", "Ring of the elements", 26818), varbits()).getX());
		assertEquals(3341, one(catalog.started("Jaleustrophos", "Pharaoh's sceptre", 26948), varbits()).getX());
	}

	@Test
	public void onlyTeleportsWithAWikiLandingZoneAreRandom()
	{
		// the Wiki's Ardougne Teleport page draws a square: "lands within 3 tiles of this point"
		Teleport ardougne = one(catalog.started("Cast", "Ardougne Teleport", -1), varbits());
		assertTrue(ardougne.isRandomLanding());
		assertEquals(2662, ardougne.getX());
		assertEquals(3305, ardougne.getY());
		// a zone belongs to the landing spot: the Camelot spell's page lists plain points, but the tablet's page draws
		// the zone both land in
		assertTrue(one(catalog.started("Break", "Camelot teleport", 8010), varbits()).isRandomLanding());
		assertTrue(one(catalog.started("Cast", "Camelot Teleport", -1), varbits()).isRandomLanding());
		// the same for the house portals: Teleport to House lands in the zone the Rimmington tablet's page draws
		assertTrue(one(catalog.started("Outside", "Teleport to House", -1),
			varbits(Condition.SPELLBOOK_VARBIT, 0, Condition.HOUSE_VARBIT, 1)).isRandomLanding());
		// no zone on the page: exactly the destination tile
		assertFalse(one(catalog.started("Edgeville", "Amulet of glory(4)", 1712), varbits()).isRandomLanding());
	}

	@Test
	public void howFarOffALandingCanBe()
	{
		// "lands within 3 tiles of this point"
		Teleport ardougne = one(catalog.started("Cast", "Ardougne Teleport", -1), varbits());
		assertEquals(3, ardougne.getMaxOffset());
		assertTrue(ardougne.couldBeOffBy(3));
		assertFalse(ardougne.couldBeOffBy(4));
		// "lands within 2.5 tiles": 2 whole tiles off at most
		Teleport minigame = one(catalog.started("Barbarian Assault", "Minigame Teleport", -1), varbits(3264, 11));
		assertEquals(2, minigame.getMaxOffset());
		// an outlined area: as far off as its farthest point
		Teleport catherby = one(catalog.started("Cast", "Catherby Teleport", -1),
			varbits(Condition.SPELLBOOK_VARBIT, 2));
		assertTrue(catherby.isRandomLanding());
		assertEquals(3, catherby.getMaxOffset());
		assertTrue(catherby.couldBeOffBy(3));
		assertFalse(catherby.couldBeOffBy(4));
		// an exact landing is never off
		Teleport glory = one(catalog.started("Edgeville", "Amulet of glory(4)", 1712), varbits());
		assertEquals(0, glory.getMaxOffset());
		assertFalse(glory.couldBeOffBy(1));
	}

	@Test
	public void clickThatIsNoTeleport()
	{
		assertTrue(catalog.started("Walk here", "", -1).isEmpty());
		assertTrue(catalog.opened("Wield", "Amulet of glory(4)", 1712).isEmpty());
	}
}
