package com.teleportpretile.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.junit.BeforeClass;
import org.junit.Test;

public class NetworkAndRideTest
{
	private static final IntUnaryOperator NO_VARBITS = id -> 0;
	private static TeleportCatalog catalog;

	@BeforeClass
	public static void load() throws IOException
	{
		try (InputStream in = NetworkAndRideTest.class.getResourceAsStream("/com/teleportpretile/teleports.tsv"))
		{
			catalog = TeleportCatalog.load(in);
		}
	}

	private static Teleport one(List<Teleport> candidates)
	{
		Teleport t = TeleportCatalog.resolve(candidates, NO_VARBITS);
		assertNotNull("a teleport for " + candidates, t);
		return t;
	}

	@Test
	public void fairyRingCodeFromTheDials()
	{
		// RuneLite's Fairy Ring plugin: left A D C B, middle I L K J, right P S R Q
		assertEquals("AIQ", FairyRings.code(0, 0, 3));
		assertEquals("CKS", FairyRings.code(2, 2, 1));
		assertEquals("DKR", FairyRings.code(1, 2, 2));
		assertNull(FairyRings.code(4, 0, 0));
		Teleport edgeville = one(catalog.fairyRing("DKR"));
		assertEquals(Teleport.Kind.NETWORK, edgeville.getKind());
		assertEquals(3129, edgeville.getX());
		assertTrue(catalog.fairyRing("AAA").isEmpty());
	}

	@Test
	public void fairyRingOptionsWithoutTheDials()
	{
		assertEquals("CKS", FairyRings.codeOfOption("Last-destination (CKS)", "<col=ffff>Fairy ring</col>"));
		assertEquals("BKS", FairyRings.codeOfOption("Zanaris", "Fairy ring"));
		assertNull(FairyRings.codeOfOption("Configure", "Fairy ring"));
		// a code saved in the ring's "Favourites" sub-menu, with the ring's name found or the target left empty
		assertEquals("AIP", FairyRings.codeOfOption("AIP", "Fairy ring"));
		assertEquals("CKS", FairyRings.codeOfOption("<col=ffffff>CKS</col>", ""));
		assertNull(FairyRings.codeOfOption("Favourite 1", "Fairy Ring"));
		assertNull(FairyRings.codeOfOption("Favourite 1", ""));
		assertNull(FairyRings.codeOfOption("AIP", "Bank booth"));
		assertNull(FairyRings.codeOfOption("Last-destination (CKS)", "Spirit tree"));
	}

	@Test
	public void spiritTreeChoiceByTextOrFixedKey()
	{
		List<Teleport> opened = catalog.opened("Travel", "Spirit tree", -1);
		assertTrue(opened.size() >= 10);
		assertEquals(3185, one(TeleportCatalog.chosen(opened, "4: Grand Exchange")).getX());
		assertEquals(3185, one(TeleportCatalog.chosenByKey(opened, '4')).getX());
	}

	@Test
	public void houseJewelleryBoxByKey()
	{
		List<Teleport> opened = catalog.opened("Teleport Menu", "Jewellery Box", -1);
		Teleport burthorpe = one(TeleportCatalog.chosenByKey(opened, '5'));
		assertEquals(2899, burthorpe.getX());
		assertFalse(burthorpe.hasFrom());
		assertEquals(3163, one(TeleportCatalog.chosenByKey(opened, 'l')).getX());
		// the game names the box by its tier: "Ornate Jewellery Box"
		assertEquals(opened.size(), catalog.opened("Teleport Menu", "Ornate Jewellery Box", -1).size());
	}

	@Test
	public void portalNexusLandsLikeTheSpell()
	{
		List<Teleport> opened = catalog.opened("Teleport Menu", "<col=ffff>Portal Nexus", -1);
		Teleport lumbridge = one(TeleportCatalog.chosen(opened, "Lumbridge"));
		assertTrue(lumbridge.isRandomLanding());
		assertEquals(3222, lumbridge.getX());
	}

	@Test
	public void ridesCountOnlyNearWhereTheyAreBoarded()
	{
		List<Teleport> levers = catalog.started("Pull", "Lever", -1);
		assertTrue(levers.size() > 3);
		// the Ardougne lever, from a few tiles away: to the Wilderness
		Teleport ardougne = one(TeleportCatalog.near(levers, 2565, 3312, 0));
		assertEquals(Teleport.Kind.RIDE, ardougne.getKind());
		assertEquals(3154, ardougne.getX());
		// nowhere near a lever: nothing
		assertTrue(TeleportCatalog.near(levers, 3200, 3200, 0).isEmpty());
	}

	@Test
	public void twoWayRidesGoBothWays()
	{
		List<Teleport> carts = catalog.started("Board", "Travel cart", -1);
		assertEquals(2776, one(TeleportCatalog.near(carts, 2834, 2951, 0)).getX());
		// back the other way: the data has two landing tiles a couple of tiles apart, either is right
		Teleport back = one(TeleportCatalog.near(carts, 2776, 3214, 0));
		assertTrue(back.getX() >= 2832 && back.getX() <= 2834);
	}

	@Test
	public void charterShipFromThePortYouAreAt()
	{
		List<Teleport> opened = TeleportCatalog.near(catalog.opened("Board", "Gangplank", -1), 1455, 2968, 0);
		assertFalse(opened.isEmpty());
		for (Teleport t : opened)
		{
			assertTrue(t.getName(), t.getName().startsWith("Charter ship: Aldarin to "));
		}
		assertEquals(2792, one(TeleportCatalog.chosen(opened, "Catherby")).getX());
	}

	@Test
	public void clicksReadFromTheCache()
	{
		// Veos at Port Sarim names each destination as an option (cache npc 8630)
		assertEquals(1824, one(TeleportCatalog.near(catalog.started("Port Piscarilius", "Veos", -1), 3055, 3245, 0)).getX());
		// a hot air balloon at Entrana: "Use" on the basket, then the destination
		List<Teleport> balloon = TeleportCatalog.near(catalog.opened("Use", "Basket", -1), 2807, 3355, 0);
		// both balloons unlocked (ZEP_MULTI varbits)
		IntUnaryOperator unlocked = id -> id == 2867 ? 2 : id == 2872 ? 1 : 0;
		Teleport varrock = TeleportCatalog.resolve(TeleportCatalog.chosen(balloon, "Varrock"), unlocked);
		assertNotNull(varrock);
		assertEquals(3299, varrock.getX());
		assertNull(TeleportCatalog.resolve(TeleportCatalog.chosen(balloon, "Varrock"), NO_VARBITS));
		// a magic carpet: the destination said in a sentence
		List<Teleport> carpet = TeleportCatalog.near(catalog.opened("Travel", "Rug Merchant", -1), 3311, 3109, 0);
		assertEquals(3467, one(TeleportCatalog.chosen(carpet, "Travel to Uzer")).getX());
		// a Dorgeshuun guide's named option is the stop
		assertEquals(3313, one(catalog.started("Mines", "Kazgar", -1)).getX());
		// magic mushtrees: any tree opens all four
		assertEquals(4, catalog.opened("Use", "Magic Mushtree", -1).size());
	}

	@Test
	public void nothingLandsInsideTheHouse()
	{
		for (Teleport t : catalog.all())
		{
			assertFalse(t.getName(), t.getX() == 1857 && t.getY() == 7065);
		}
	}
}
