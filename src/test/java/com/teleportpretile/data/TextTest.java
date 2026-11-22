package com.teleportpretile.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TextTest
{
	@Test
	public void cleanRemovesTags()
	{
		assertEquals("Amulet of glory(4)", Text.clean("<col=ff9040>Amulet of glory(4)</col>"));
		assertEquals("Lumbridge Teleport", Text.clean("Lumbridge Teleport "));
	}

	@Test
	public void sameNameAllowsChargesButNotOtherItems()
	{
		assertTrue(Text.sameName("Amulet of glory(4)", "Amulet of glory"));
		assertTrue(Text.sameName("Amulet of glory (t4)", "Amulet of glory"));
		assertTrue(Text.sameName("varrock teleport", "Varrock Teleport"));
		assertFalse(Text.sameName("Amulet of glory", "Amulet of eternal glory"));
		assertFalse(Text.sameName("Ardougne cloak 2", "Ardougne cloak 1"));
	}

	@Test
	public void baseNameDropsTierAndCharges()
	{
		assertEquals("desert amulet", Text.baseName("Desert amulet 3"));
		assertEquals("ring of dueling", Text.baseName("Ring of dueling(8)"));
	}

	@Test
	public void choicesAndNumbering()
	{
		assertTrue(Text.sameChoice("Edgeville", "Edgeville"));
		assertTrue(Text.sameChoice("<col=735a28>1:</col> Fishing Guild", "Fishing Guild"));
		assertTrue(Text.sameChoice("Edgeville.", "Edgeville"));
		assertFalse(Text.sameChoice("Edgevilleish", "Edgeville"));
		assertTrue(Text.sameChoice("Travel to Bedabin Camp.", "Bedabin Camp"));
		assertFalse(Text.sameChoice("Travel to Bedabin Campsite", "Bedabin Camp"));
		assertFalse(Text.sameChoice("Superuzer", "Uzer"));
		assertEquals("3", Text.listKey("<col=735a28>3</col>: Mining Guild"));
		assertEquals("A", Text.listKey("<col=735a28>A</col>: Kourend Castle"));
		assertEquals(null, Text.listKey("Mining Guild"));
		assertEquals(null, Text.listKey("Al Kharid"));
		assertTrue(Text.sameChoice("<col=735a28>B</col>: Kourend Castle", "Kourend Castle"));
	}
}
