package com.teleportpretile;

import com.teleportpretile.data.Teleport;
import com.teleportpretile.render.Projector;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the destination's tile markers while the player teleports. The camera follows the player, so a tile N tiles
 * east of the landing spot will be drawn N tiles east of where the player stands once they land; each marker is
 * therefore drawn that far from the player now. The ground is taken as flat at the player's height.
 */
class TeleportPreTileOverlay extends Overlay
{
	private static final int TILE = Perspective.LOCAL_TILE_SIZE;
	private static final int HALF_TILE = Perspective.LOCAL_HALF_TILE_SIZE;
	/** JAU14 to radians, for the camera angles outside GPU mode. */
	private static final double JAU14 = 2 * Math.PI / 16384;
	/** How opaque the blur is right next to the outline, compared with the outline itself. */
	private static final double BLUR_PEAK = 0.6;
	/** Most layers one marker's blur is drawn with. */
	private static final int BLUR_LAYERS = 12;

	private final Client client;
	private final TeleportPreTilePlugin plugin;
	private final TeleportPreTileConfig config;
	private final ConfigManager configManager;

	@Inject
	TeleportPreTileOverlay(Client client, TeleportPreTilePlugin plugin, TeleportPreTileConfig config,
		ConfigManager configManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		renderLearned(g);
		return renderGhost(g);
	}

	/**
	 * After a trip on which the plugin saved something new, flash the destination's markers on their real tiles: drawn
	 * by this plugin on top, the player's tile markers are never changed.
	 */
	private void renderLearned(Graphics2D g)
	{
		TeleportPreTilePlugin.Learned learned = plugin.getLearned();
		long now = System.currentTimeMillis();
		if (learned == null || !config.flashWhenLearned() || !learned.getBlink().visible(now))
		{
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		Style style = style();
		Stroke stroke = new BasicStroke(style.width + 1);
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (GroundMarkerReader.Marker m : learned.getMarkers())
		{
			if (m.getPlane() != wv.getPlane())
			{
				continue;
			}
			LocalPoint lp = learned.isSceneTiles() ? LocalPoint.fromScene(m.getX(), m.getY(), wv)
				: LocalPoint.fromWorld(wv, m.getX(), m.getY());
			Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
			if (poly == null)
			{
				continue;
			}
			Color own = m.getColor() != null ? m.getColor() : style.defaultColor;
			Color color = outline(own);
			g.setColor(fill(own, style.fillAlpha));
			g.fill(poly);
			g.setStroke(stroke);
			g.setColor(withAlpha(color, Math.max(color.getAlpha(), 160)));
			g.draw(poly);
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
	}

	/** The Tile Markers plugin's look: default colour, border width (or this plugin's Line thickness) and fill opacity. */
	static final class Style
	{
		Color defaultColor;
		float width;
		int fillAlpha;
	}

	Style style()
	{
		Style s = new Style();
		Color defaultColor = configManager.getConfiguration(GroundMarkerReader.GROUP, "markerColor", Color.class);
		s.defaultColor = defaultColor == null ? Color.YELLOW : defaultColor;
		Double width = configManager.getConfiguration(GroundMarkerReader.GROUP, "borderWidth", Double.class);
		s.width = config.lineThickness() > 0 ? config.lineThickness() : width == null ? 2f : width.floatValue();
		Integer fillOpacity = configManager.getConfiguration(GroundMarkerReader.GROUP, "fillOpacity", Integer.class);
		s.fillAlpha = fillOpacity == null ? 50 : fillOpacity;
		return s;
	}

	/** Whether the colour overrides apply to a marker with this outline colour (see Override threshold). */
	private boolean overridden(Color own)
	{
		return !config.overrideThreshold() || config.overrideWhen().matches(own.getAlpha(), config.threshold());
	}

	/** The outline colour drawn for a marker of this colour; overrides change only this plugin's drawing. */
	Color outline(Color own)
	{
		return overridden(own) && config.overrideColour() ? config.markerColour() : own;
	}

	/** The label colour drawn for a marker of this colour, whose outline is drawn in {@code outline}. */
	Color label(Color own, Color outline)
	{
		return overridden(own) && config.overrideText() ? config.textColour() : withAlpha(outline, 255);
	}

	/** The fill colour drawn for a marker of this colour. */
	private Color fill(Color own, int fillAlpha)
	{
		return overridden(own) && config.overrideFill() ? config.fillColour() : new Color(0, 0, 0, fillAlpha);
	}

	/**
	 * The teleport preview to draw now, or null: nothing to show, not shown until the teleport starts (Show on click
	 * off), hidden as an uncertain landing, or the player isn't on the main map. Used by the minimap overlay too.
	 */
	TeleportPreTilePlugin.Ghost shownGhost()
	{
		TeleportPreTilePlugin.Ghost ghost = plugin.getGhost();
		if (ghost == null || ghost.getMarkers().isEmpty()
			|| (!config.showOnClick() && !ghost.getTracker().isStarted()))
		{
			return null;
		}
		Teleport teleport = ghost.getTracker().getTeleport();
		if (config.hideUncertain() && teleport.couldBeOffBy(config.hideOffBy()))
		{
			return null;
		}
		Player player = client.getLocalPlayer();
		if (player == null || player.getWorldView() == null || !player.getWorldView().isTopLevel()
			|| player.getLocalLocation() == null)
		{
			return null;
		}
		return ghost;
	}

	/**
	 * Where the preview is placed from: the player's true tile (the tile the server has them on) with Snap to true
	 * tile on, otherwise their smoothly moving position. Call only when {@link #shownGhost()} isn't null.
	 */
	LocalPoint anchor()
	{
		Player player = client.getLocalPlayer();
		LocalPoint smooth = player.getLocalLocation();
		if (!config.snapToTrueTile())
		{
			return smooth;
		}
		LocalPoint trueTile = LocalPoint.fromWorld(client.getTopLevelWorldView(), player.getWorldLocation());
		return trueTile != null ? trueTile : smooth;
	}

	private Dimension renderGhost(Graphics2D g)
	{
		TeleportPreTilePlugin.Ghost ghost = shownGhost();
		if (ghost == null)
		{
			return null;
		}
		LocalPoint here = anchor();

		int z = Perspective.getTileHeight(client, here, client.getTopLevelWorldView().getPlane());
		Projector projector = projector();
		Teleport teleport = ghost.getTracker().getTeleport();
		boolean blur = config.blurRandom() && teleport.isRandomLanding();
		double blurTiles = config.blurDistance() / 100.0;

		List<GroundMarkerReader.Marker> markers = ghost.getMarkers();

		double opacity = config.opacity() / 100.0;
		Style style = style();
		Color defaultColor = style.defaultColor;
		Stroke stroke = new BasicStroke(style.width);
		int fillAlpha = style.fillAlpha;

		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


		for (GroundMarkerReader.Marker m : markers)
		{
			double dx = m.getX() - ghost.getCenterX();
			double dy = m.getY() - ghost.getCenterY();
			double cx = here.getX() + dx * TILE;
			double cy = here.getY() + dy * TILE;
			// overrides only change how these preview markers are drawn; the Tile Markers data is only ever read
			Color own = m.getColor() != null ? m.getColor() : defaultColor;
			Color color = outline(own);
			Color fill = fill(own, fillAlpha);

			Polygon center = tile(projector, cx, cy, z);
			if (center == null)
			{
				continue;
			}
			g.setColor(withAlpha(fill, fill.getAlpha() * opacity));
			g.fill(center);
			if (blur)
			{
				drawBlur(g, center, blurTiles * tileSize(center), color, opacity, style.width);
			}
			g.setStroke(stroke);
			g.setColor(withAlpha(color, color.getAlpha() * opacity));
			g.draw(center);
			if (m.getLabel() != null && !m.getLabel().isEmpty())
			{
				Color text = label(own, color);
				drawLabel(g, center, m.getLabel(), withAlpha(text, text.getAlpha() * opacity));
			}
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
		return null;
	}

	/**
	 * A soft glow around the outline that fades out like a bell curve (a gaussian blur) over {@code reachPx} pixels on
	 * each side. It is drawn as the outline with ever narrower strokes on top of each other, the widest the faintest,
	 * with each stroke's opacity worked out so the piled-up strokes give the curve's opacity at that distance. The
	 * sharp outline is drawn over it afterwards.
	 */
	static void drawBlur(Graphics2D g, Polygon outline, double reachPx, Color color, double opacity, float lineWidth)
	{
		double[] a = blurLayerAlphas(reachPx);
		int n = a.length;
		int baseAlpha = color.getAlpha();
		for (int k = n; k >= 1; k--)
		{
			int alpha = (int) Math.round(baseAlpha * opacity * a[k - 1]);
			if (alpha <= 0)
			{
				continue;
			}
			float width = (float) (lineWidth + 2 * reachPx * k / n);
			g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
			g.draw(outline);
		}
	}

	/**
	 * The opacity of each blur layer, nearest the outline first, for a blur reaching {@code reachPx} pixels. Layer k
	 * (of n) covers out to k/n of the reach, so the band between layers k-1 and k starts (k-1)/n of the reach out; the
	 * layers covering a band pile up to the bell curve's opacity at its start: peak * exp(-d²/2σ²) with σ a third of
	 * the reach, so it is strongest against the outline and has all but faded at the far edge.
	 */
	static double[] blurLayerAlphas(double reachPx)
	{
		int n = (int) Math.max(1, Math.min(BLUR_LAYERS, Math.round(reachPx / 1.5)));
		double sigma = 1 / 3.0;
		double[] target = new double[n + 2];
		for (int k = 1; k <= n; k++)
		{
			double d = (k - 1) / (double) n;
			target[k] = BLUR_PEAK * Math.exp(-0.5 * (d / sigma) * (d / sigma));
		}
		double[] a = new double[n];
		for (int k = n; k >= 1; k--)
		{
			// layers k..n together give target[k]: 1 - (1 - a_k)(1 - target[k + 1]) = target[k]
			a[k - 1] = Math.max(0, 1 - (1 - target[k]) / (1 - target[k + 1]));
		}
		return a;
	}

	/** Roughly how many pixels one tile is across on screen, from its drawn outline. */
	private static double tileSize(Polygon p)
	{
		double sum = 0;
		for (int i = 0; i < p.npoints; i++)
		{
			int j = (i + 1) % p.npoints;
			sum += Math.hypot(p.xpoints[j] - p.xpoints[i], p.ypoints[j] - p.ypoints[i]);
		}
		return sum / p.npoints;
	}

	private static Polygon tile(Projector projector, double cx, double cy, int z)
	{
		double[] sw = projector.project(cx - HALF_TILE, cy - HALF_TILE, z);
		double[] nw = projector.project(cx + HALF_TILE, cy - HALF_TILE, z);
		double[] ne = projector.project(cx + HALF_TILE, cy + HALF_TILE, z);
		double[] se = projector.project(cx - HALF_TILE, cy + HALF_TILE, z);
		if (sw == null || nw == null || ne == null || se == null)
		{
			return null;
		}
		Polygon p = new Polygon();
		p.addPoint((int) Math.round(sw[0]), (int) Math.round(sw[1]));
		p.addPoint((int) Math.round(nw[0]), (int) Math.round(nw[1]));
		p.addPoint((int) Math.round(ne[0]), (int) Math.round(ne[1]));
		p.addPoint((int) Math.round(se[0]), (int) Math.round(se[1]));
		return p;
	}

	private static void drawLabel(Graphics2D g, Polygon tile, String label, Color color)
	{
		int sx = 0;
		int sy = 0;
		for (int i = 0; i < tile.npoints; i++)
		{
			sx += tile.xpoints[i];
			sy += tile.ypoints[i];
		}
		FontMetrics fm = g.getFontMetrics();
		int x = sx / tile.npoints - fm.stringWidth(label) / 2;
		int y = sy / tile.npoints + fm.getAscent() / 2;
		g.setColor(withAlpha(Color.BLACK, color.getAlpha()));
		g.drawString(label, x + 1, y + 1);
		g.setColor(color);
		g.drawString(label, x, y);
	}

	private Projector projector()
	{
		if (client.isGpu())
		{
			return new Projector(client.getCameraFpX(), client.getCameraFpY(), client.getCameraFpZ(),
				client.getCameraFpPitch(), client.getCameraFpYaw(), client.getScale(), client.getViewportWidth(),
				client.getViewportHeight(), client.getViewportXOffset(), client.getViewportYOffset());
		}
		return new Projector(client.getCameraX(), client.getCameraY(), client.getCameraZ(),
			client.getCameraPitch() * JAU14, client.getCameraYaw() * JAU14, client.getScale(),
			client.getViewportWidth(), client.getViewportHeight(), client.getViewportXOffset(),
			client.getViewportYOffset());
	}

	static Color withAlpha(Color c, double alpha)
	{
		int a = (int) Math.round(Math.max(0, Math.min(255, alpha)));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}
}
