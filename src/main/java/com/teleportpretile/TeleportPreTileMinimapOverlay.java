package com.teleportpretile;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The same preview on the minimap: each destination marker is drawn as many tiles from the player as it will be from them once they land, like the Tile Markers plugin draws its own tiles on
 * the minimap. A random landing is drawn fainter instead of blurred, since a minimap tile is only a few pixels.
 */
class TeleportPreTileMinimapOverlay extends Overlay
{
	private static final int HALF_TILE = Perspective.LOCAL_HALF_TILE_SIZE;
	/** How much fainter a random landing's tiles are drawn, as the 3D preview's fill is. */
	private static final double RANDOM_FAINT = 0.6;

	private final Client client;
	private final TeleportPreTileConfig config;
	private final TeleportPreTileOverlay overlay;

	@Inject
	TeleportPreTileMinimapOverlay(Client client, TeleportPreTileConfig config, TeleportPreTileOverlay overlay)
	{
		this.client = client;
		this.config = config;
		this.overlay = overlay;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showOnMinimap())
		{
			return null;
		}
		TeleportPreTilePlugin.Ghost ghost = overlay.shownGhost();
		if (ghost == null)
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		LocalPoint here = overlay.anchor();
		double opacity = config.opacity() / 100.0;
		if (config.blurRandom() && ghost.getTracker().getTeleport().isRandomLanding())
		{
			opacity *= RANDOM_FAINT;
		}

		Color defaultColor = overlay.style().defaultColor;
		for (GroundMarkerReader.Marker m : ghost.getMarkers())
		{
			// overrides only change how these preview markers are drawn; the Tile Markers data is only ever read
			Color color = overlay.outline(m.getColor() != null ? m.getColor() : defaultColor);
			fillTile(g, wv, here, m.getX() - ghost.getCenterX(), m.getY() - ghost.getCenterY(),
				TeleportPreTileOverlay.withAlpha(color, color.getAlpha() * opacity));
		}
		return null;
	}

	/** Fills the tile {@code dx}, {@code dy} tiles from the player on the minimap, if it is on the minimap. */
	private void fillTile(Graphics2D g, WorldView wv, LocalPoint here, int dx, int dy, Color color)
	{
		if (color.getAlpha() <= 0)
		{
			return;
		}
		int cx = here.getX() + dx * Perspective.LOCAL_TILE_SIZE;
		int cy = here.getY() + dy * Perspective.LOCAL_TILE_SIZE;
		Point sw = Perspective.localToMinimap(client, new LocalPoint(cx - HALF_TILE, cy - HALF_TILE, wv));
		Point nw = Perspective.localToMinimap(client, new LocalPoint(cx - HALF_TILE, cy + HALF_TILE, wv));
		Point ne = Perspective.localToMinimap(client, new LocalPoint(cx + HALF_TILE, cy + HALF_TILE, wv));
		Point se = Perspective.localToMinimap(client, new LocalPoint(cx + HALF_TILE, cy - HALF_TILE, wv));
		if (sw == null || nw == null || ne == null || se == null)
		{
			return; // off the minimap
		}
		Polygon poly = new Polygon();
		poly.addPoint(sw.getX(), sw.getY());
		poly.addPoint(nw.getX(), nw.getY());
		poly.addPoint(ne.getX(), ne.getY());
		poly.addPoint(se.getX(), se.getY());
		g.setColor(color);
		g.fill(poly);
	}
}
