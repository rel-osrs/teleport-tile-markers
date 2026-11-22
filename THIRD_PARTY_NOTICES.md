# Third-party notices

Teleport Tile Markers' code is under the BSD 2-Clause licence (see `LICENSE`).

## Teleport data

`src/main/resources/com/teleportpretile/teleports.tsv` was generated at development time from the teleport rows
of the [Best Path](https://github.com/rel-osrs/best-path) plugin's `transports.tsv`: each teleport's name,
destination tile, duration, menu text and requirements. Which teleports land on a random tile, and the middle
of each landing zone, come from the [Best Path Data Tool](https://github.com/rel-osrs/best-path-tool), which read
them from the OSRS Wiki at development time (MediaWiki API, one request at a time, responses cached). The plugin
never contacts the Wiki.

Where Best Path has no menu text for a ride or network stop, the click (the object or NPC's name and option) was
read from the [osrs-dumps](https://github.com/Joshua-F/osrs-dumps) dump of the game cache. Which option sets the
Varrock, Camelot and Watchtower defaults was read from the game's client scripts
([runelite/cs2-scripts](https://github.com/runelite/cs2-scripts)).

Parts of the data are derived from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/), whose
content is licensed under
[Creative Commons Attribution-NonCommercial-ShareAlike 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).
Those parts are not covered by this project's BSD 2-Clause licence; they are shared under that same licence, with
attribution to the Wiki's contributors. See Best Path's `THIRD_PARTY_NOTICES.md` for details. Game content (names,
places) is the property of Jagex Ltd.
