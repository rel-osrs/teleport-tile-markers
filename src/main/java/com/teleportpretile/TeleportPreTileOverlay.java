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
	/** Most outlines drawn in one frame for the blur; fewer samples per marker when there are many markers. */
	private static final int BLUR_BUDGET = 2000;
	/** How far the blur reaches each way for a teleport the data says lands on a random tile, in tiles. */
	private static final double BLUR_TILES = 0.25;
	/** How opaque the blurred outline is in its middle, compared with a sharp outline. */
	private static final double BLUR_PEAK = 0.7;

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
		double spreadX = blur ? BLUR_TILES : 0;
		double spreadY = spreadX;

		List<GroundMarkerReader.Marker> markers = ghost.getMarkers();
		int nx = samples(spreadX);
		int ny = samples(spreadY);
		while (nx * ny * markers.size() > BLUR_BUDGET && (nx > 3 || ny > 3))
		{
			nx = Math.max(Math.min(nx, 3), nx - 2);
			ny = Math.max(Math.min(ny, 3), ny - 2);
		}

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
			g.setColor(withAlpha(fill, fill.getAlpha() * opacity * (blur ? 0.6 : 1)));
			g.fill(center);
			g.setStroke(stroke);
			if (!blur)
			{
				g.setColor(withAlpha(color, color.getAlpha() * opacity));
				g.draw(center);
			}
			else
			{
				drawBlurred(g, projector, center, cx, cy, z, spreadX, spreadY, nx, ny, color, opacity, style.width);
			}
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
	 * The outline drawn at many small offsets around its place, so it shows slightly blurred, reaching
	 * {@code spreadX} and {@code spreadY} tiles each way. The offsets are weighted like a bell curve, so the blur is
	 * strongest on the outline and fades out. Each stroke is about as wide as the gap between neighbouring offsets on
	 * screen, so the strokes run together instead of showing as separate lines.
	 */
	private static void drawBlurred(Graphics2D g, Projector projector, Polygon center, double cx, double cy, int z,
		double spreadX, double spreadY, int nx, int ny, Color color, double opacity, float minWidth)
	{
		double[] ox = offsets(spreadX, nx);
		double[] oy = offsets(spreadY, ny);
		double[] wx = weights(ox, spreadX);
		double[] wy = weights(oy, spreadY);
		double sumX = 0;
		double sumY = 0;
		for (double w : wx)
		{
			sumX += w;
		}
		for (double w : wy)
		{
			sumY += w;
		}
		// along an edge the strokes with the same offset across it pile up; size them so the middle row reaches the
		// peak opacity: 1 - (1 - a)^sum ~ peak
		double each = -Math.log(1 - BLUR_PEAK) / Math.max(sumX, sumY);

		double tilePx = tileSize(center);
		double gap = Math.max(nx > 1 ? 2 * spreadX / (nx - 1) : 0, ny > 1 ? 2 * spreadY / (ny - 1) : 0) * tilePx;
		g.setStroke(new BasicStroke((float) Math.max(minWidth, gap * 1.15), BasicStroke.CAP_ROUND,
			BasicStroke.JOIN_ROUND));
		int baseAlpha = color.getAlpha();
		for (int i = 0; i < nx; i++)
		{
			for (int j = 0; j < ny; j++)
			{
				double a = 1 - Math.exp(-each * wx[i] * wy[j]);
				int alpha = (int) Math.round(baseAlpha * opacity * Math.min(1, a));
				if (alpha <= 0)
				{
					continue;
				}
				Polygon p = tile(projector, cx + ox[i] * TILE, cy + oy[j] * TILE, z);
				if (p != null)
				{
					g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
					g.draw(p);
				}
			}
		}
	}

	/** {@code n} evenly spaced offsets from -spread to +spread (just 0 when n is 1). */
	private static double[] offsets(double spread, int n)
	{
		double[] o = new double[n];
		for (int i = 0; i < n; i++)
		{
			o[i] = n == 1 ? 0 : -spread + 2 * spread * i / (n - 1);
		}
		return o;
	}

	/** Bell curve weights, 1 in the middle and about 0.14 at the edge of the blur. */
	private static double[] weights(double[] offsets, double spread)
	{
		double[] w = new double[offsets.length];
		double sigma = Math.max(spread, 1e-6) / 2;
		for (int i = 0; i < w.length; i++)
		{
			double t = offsets[i] / sigma;
			w[i] = Math.exp(-0.5 * t * t);
		}
		return w;
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

	/** Samples across a spread of {@code spread} tiles each way: odd, so one sits in the middle. */
	private static int samples(double spread)
	{
		return spread <= 0 ? 1 : Math.min(11, 2 * (int) Math.ceil(spread * 8) + 1);
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
