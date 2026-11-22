package com.teleportpretile.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import java.util.function.Supplier;
import org.junit.Test;

public class ClickedNameTest
{
	private static final Supplier<String> NONE = () -> null;
	private static final Supplier<String> NOT_ASKED = () ->
	{
		fail("asked for a name although the target has one");
		return null;
	};

	@Test
	public void keepsATargetThatHasText()
	{
		assertEquals("<col=ff9040>Amulet of glory(4)</col>",
			ClickedName.of("<col=ff9040>Amulet of glory(4)</col>", NOT_ASKED, NOT_ASKED, NOT_ASKED, NOT_ASKED));
	}

	@Test
	public void inventorySubMenuUsesTheItem()
	{
		// "Rub" then "Edgeville" on Amulet of glory(4) in the inventory: target '', item 1712
		assertEquals("Amulet of glory(4)",
			ClickedName.of("", () -> "Amulet of glory(4)", () -> "<col=ff9040>Amulet of glory(4)</col>", NONE, NONE));
	}

	@Test
	public void equipmentSubMenuUsesTheSlotWhenThereIsNoItem()
	{
		assertEquals("<col=ff9040>Max cape</col>",
			ClickedName.of("", NONE, () -> "<col=ff9040>Max cape</col>", NONE, NONE));
	}

	@Test
	public void objectSubMenuUsesTheObject()
	{
		// "Favourites" then a code on a fairy ring
		assertEquals("Fairy ring", ClickedName.of(" ", NONE, () -> "", () -> "Fairy ring", NONE));
	}

	@Test
	public void npcSubMenuUsesTheNpc()
	{
		assertEquals("Bob Barter", ClickedName.of("", NONE, NONE, NONE, () -> "Bob Barter"));
	}

	@Test
	public void nothingKnownLeavesItEmpty()
	{
		assertEquals("", ClickedName.of(null, NONE, NONE, NONE, NONE));
	}
}
