package com.teleportpretile.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DialogKeysTest
{
	@Test
	public void chatDialogKeyPicksTheChildWithThatIndex()
	{
		// chatbox_multi_init: child 0 is the title, 1-5 the options, then two decoration graphics with no text
		String[] dialog = {"Where would you like to teleport to?", "Edgeville", "Karamja", "Draynor Village",
			"Al Kharid", "Nowhere.", null, null};
		assertEquals("Edgeville", DialogKeys.chatOption(dialog, '1'));
		assertEquals("Al Kharid", DialogKeys.chatOption(dialog, '4'));
		assertNull(DialogKeys.chatOption(dialog, '6'));
		assertNull(DialogKeys.chatOption(dialog, 'a'));
		assertNull(DialogKeys.chatOption(new String[]{"Title", "Yes", "No"}, '3'));
	}

	@Test
	public void listKeyPicksTheEntryLabelledWithIt()
	{
		List<String> list = Arrays.asList("1: Fishing Guild", "2: Mining Guild", "9: Woodcutting Guild",
			"A: Kourend Castle");
		assertEquals("2: Mining Guild", DialogKeys.listEntry(list, '2'));
		assertEquals("A: Kourend Castle", DialogKeys.listEntry(list, 'a'));
		assertNull(DialogKeys.listEntry(list, '5'));
	}
}
