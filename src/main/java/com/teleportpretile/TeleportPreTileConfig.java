package com.teleportpretile;

import com.teleportpretile.render.OverrideWhen;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * The plugin's settings. Descriptions are the panel's tooltips, which don't wrap on their own; RuneLite shows them as
 * HTML, so they carry a line break every 50 or so characters.
 */
@ConfigGroup(TeleportPreTileConfig.GROUP)
public interface TeleportPreTileConfig extends Config
{
	String GROUP = "teleportpretile";
	/** Config key prefix for the learned meaning of "last destination" varbit values (see LastDestination). */
	String LAST_DESTINATION_PREFIX = "lastDestination.";
	/** The Forget learned data checkbox: ticking it asks, then forgets, then unticks itself. */
	String FORGET_LEARNED = "forgetLearned";

	@ConfigItem(
		keyName = "showOnClick",
		name = "Show on click",
		description = "Show the destination's tile markers as soon as you<br>"
			+ "click the teleport. When off, they show once the<br>teleport animation starts.",
		position = 1
	)
	default boolean showOnClick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawDistance",
		name = "Draw distance",
		description = "Show tile markers up to this many tiles from<br>where you will land.",
		position = 2
	)
	@Range(min = 1, max = 100)
	default int drawDistance()
	{
		return 32;
	}

	@ConfigItem(
		keyName = "snapToTrueTile",
		name = "Snap to true tile",
		description = "Place the markers from the tile you are really on<br>"
			+ "(your true tile), so they jump a tile at a time<br>"
			+ "while you move. When off, they follow your<br>character smoothly.",
		position = 3
	)
	default boolean snapToTrueTile()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOnMinimap",
		name = "Show on minimap",
		description = "Also draw the destination's markers on the<br>"
			+ "minimap, where they will be once you land.",
		position = 4
	)
	default boolean showOnMinimap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "opacity",
		name = "Opacity",
		description = "How strongly the destination's tile markers are<br>"
			+ "drawn, compared with normal tile markers.",
		position = 5
	)
	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	default int opacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "lineThickness",
		name = "Line thickness",
		description = "How thick the destination's marker outlines are<br>"
			+ "drawn, in pixels. 0 uses the Tile Markers<br>plugin's border width.",
		position = 6
	)
	@Range(min = 0, max = 10)
	@Units(Units.PIXELS)
	default int lineThickness()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "blurRandom",
		name = "Blur random landings",
		description = "For teleports that land on a random tile near the<br>"
			+ "destination, draw the markers with a soft blur<br>"
			+ "around a sharp outline. They are placed as if<br>"
			+ "you land in the middle of the area. Other<br>"
			+ "teleports land exactly on their tile.",
		position = 7
	)
	default boolean blurRandom()
	{
		return true;
	}

	@ConfigItem(
		keyName = "blurDistance",
		name = "Blur distance",
		description = "How far the blur of random landings fades out<br>"
			+ "from each marker's outline, as a share of a<br>"
			+ "tile's width. The outline itself stays sharp.",
		position = 8
	)
	@Range(min = 5, max = 100)
	@Units(Units.PERCENT)
	default int blurDistance()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "hideUncertain",
		name = "Hide uncertain landings",
		description = "Don't show the markers for teleports that can<br>"
			+ "land the number of tiles below or more from<br>"
			+ "where they are drawn. Zones whose size isn't<br>known yet are still shown.",
		position = 9
	)
	default boolean hideUncertain()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideOffBy",
		name = "Hide when off by",
		description = "With Hide uncertain landings on: hide the markers<br>"
			+ "when the landing could be this many tiles or<br>more from where they are drawn.",
		position = 10
	)
	@Range(min = 1, max = 20)
	@Units(" tiles")
	default int hideOffBy()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "flashWhenLearned",
		name = "Flash when learned",
		description = "When a trip teaches the plugin something new<br>"
			+ "(your house's layout, an instance, the ground's<br>"
			+ "height, or where an item's last destination<br>"
			+ "goes), flash the destination's markers 3 times<br>"
			+ "when you land: in the override colour, or each<br>"
			+ "marker's own colour inverted.",
		position = 11
	)
	default boolean flashWhenLearned()
	{
		return true;
	}

	@ConfigItem(
		keyName = "learnHeights",
		name = "Learn ground height",
		description = "The first time you land somewhere, remember how<br>"
			+ "high the ground is under the marked tiles there.<br>"
			+ "Later trips draw each marker at its real height,<br>"
			+ "so hills and steps line up. Off: always flat.",
		position = 12
	)
	default boolean learnHeights()
	{
		return true;
	}

	@ConfigItem(
		keyName = FORGET_LEARNED,
		name = "Forget learned data",
		description = "Forget everything the plugin has learned on this<br>"
			+ "account: your house's layout, instances, ground<br>"
			+ "heights and items' last destinations. Asks first;<br>"
			+ "it can't be undone, and the next trip to each<br>"
			+ "place shows nothing (or flat markers) until it's<br>learned again.",
		position = 13,
		warning = "Forget everything Teleport Tile Markers has learned (your house's layout, instances, ground heights"
			+ " and items' last destinations)?\nThis can't be undone."
	)
	default boolean forgetLearned()
	{
		return false;
	}

	@ConfigSection(
		name = "Colours",
		description = "Draw the destination's markers in your own colours<br>"
			+ "instead of the Tile Markers ones. This only<br>"
			+ "changes how this plugin draws them; your tile<br>markers are never changed.",
		position = 14
	)
	String COLOURS = "colours";

	@ConfigItem(
		keyName = "overrideColour",
		name = "Override marker colour",
		description = "Draw every destination marker's outline and<br>label in the colour below.",
		position = 15,
		section = COLOURS
	)
	default boolean overrideColour()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "markerColour",
		name = "Marker colour",
		description = "The outline and label colour used when Override<br>marker colour is on.",
		position = 16,
		section = COLOURS
	)
	default Color markerColour()
	{
		return new Color(0, 200, 255, 255);
	}

	@ConfigItem(
		keyName = "overrideFill",
		name = "Override fill colour",
		description = "Fill every destination marker with the colour<br>below.",
		position = 17,
		section = COLOURS
	)
	default boolean overrideFill()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "fillColour",
		name = "Fill colour",
		description = "The fill colour used when Override fill colour is<br>on.",
		position = 18,
		section = COLOURS
	)
	default Color fillColour()
	{
		return new Color(0, 200, 255, 10);
	}

	@ConfigItem(
		keyName = "overrideText",
		name = "Override text colour",
		description = "Draw every destination marker's label in the<br>colour below.",
		position = 19,
		section = COLOURS
	)
	default boolean overrideText()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "textColour",
		name = "Text colour",
		description = "The label colour used when Override text colour<br>is on.",
		position = 20,
		section = COLOURS
	)
	default Color textColour()
	{
		return Color.WHITE;
	}

	@ConfigSection(
		name = "Override threshold",
		description = "Only apply the colour overrides to some markers,<br>"
			+ "chosen by their own outline opacity.",
		position = 21
	)
	String THRESHOLD = "threshold";

	@ConfigItem(
		keyName = "overrideThreshold",
		name = "Override threshold",
		description = "Only apply the colour overrides to markers whose<br>"
			+ "own outline opacity is above, below or exactly<br>"
			+ "the threshold. With Override when set to Any,<br>the overrides apply to every marker.",
		position = 22,
		section = THRESHOLD
	)
	default boolean overrideThreshold()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overrideWhen",
		name = "Override when",
		description = "With Override threshold on: override markers<br>"
			+ "whose outline opacity is above, below or exactly<br>"
			+ "the threshold. Any overrides every marker and<br>ignores the threshold.",
		position = 23,
		section = THRESHOLD
	)
	default OverrideWhen overrideWhen()
	{
		return OverrideWhen.ANY;
	}

	@ConfigItem(
		keyName = "threshold",
		name = "Threshold",
		description = "The outline opacity (alpha, 0-255 as in the<br>"
			+ "colour pickers) that \"Override when\" compares<br>with.",
		position = 24,
		section = THRESHOLD
	)
	@Range(min = 0, max = 255)
	default int threshold()
	{
		// about 10%
		return 26;
	}
}
