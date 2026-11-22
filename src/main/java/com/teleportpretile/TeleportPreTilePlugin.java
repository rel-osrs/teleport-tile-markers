package com.teleportpretile;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import com.teleportpretile.data.ClickedName;
import com.teleportpretile.data.DialogKeys;
import com.teleportpretile.data.FairyRings;
import com.teleportpretile.data.HouseLayout;
import com.teleportpretile.data.LastDestination;
import com.teleportpretile.data.Teleport;
import com.teleportpretile.data.TeleportCatalog;
import com.teleportpretile.data.TeleportTracker;
import com.teleportpretile.data.TileHeights;
import com.teleportpretile.data.Text;
import com.teleportpretile.render.Blink;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Teleport Tile Markers",
	description = "Shows the tile markers at a teleport's destination while you are still teleporting",
	tags = {"teleport", "tile", "marker", "ground", "destination", "pre"}
)
public class TeleportPreTilePlugin extends Plugin implements KeyListener
{
	/** Game ticks a dialog or list opened by a teleport item stays open for the choice to count. */
	private static final int CHOICE_TICKS = 100;
	/** A landing further than this from the teleport's destination (in tiles) wasn't that teleport. */
	private static final int LANDING_TILES = 12;
	private static final int HALF_TILE = Perspective.LOCAL_HALF_TILE_SIZE;

	/**
	 * The teleport in progress and the destination markers to draw for it, placed around (centerX, centerY), the tile
	 * the player will land on. For the house or another instance these are scene tiles of it as last seen, not
	 * world tiles. {@code heights}: the ground's height at the markers, learned on an earlier trip; null to draw them
	 * flat.
	 */
	@Value
	static class Ghost
	{
		TeleportTracker tracker;
		List<GroundMarkerReader.Marker> markers;
		int centerX;
		int centerY;
		TileHeights heights;
	}


	/**
	 * The destination's markers flashing on their real tiles after a trip on which the plugin learned something (the
	 * house's layout, what a "last destination" means), so the player sees it was saved. Markers in world tiles, or
	 * scene tiles for the house.
	 */
	@Value
	static class Learned
	{
		List<GroundMarkerReader.Marker> markers;
		boolean sceneTiles;
		Blink blink;
	}

	/** The Forget learned data setting's name, as the settings panel shows it. */
	private static final String FORGET_LABEL = "Forget learned data";

	/** Per-account config key for the player's house as last seen (see HouseLayout). */
	private static final String HOUSE_KEY = "houseLayout";
	/**
	 * Per-account config key prefix, then a teleport's id, for the instance that teleport landed in last time (a
	 * HouseLayout with one entry): most instances are built the same way every time, so the player's markers in it
	 * can be placed before arriving, as for the house.
	 */
	private static final String INSTANCE_PREFIX = "instance.";
	private static final String INSTANCE_ENTRY = "arrival";
	/**
	 * Per-account config key prefix, then a landing tile "x_y_plane", for the ground's height at the marked tiles
	 * around it (see TileHeights), learned on arrival.
	 */
	private static final String HEIGHTS_PREFIX = "heights.";

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private ConfigManager configManager;
	@Inject
	private TeleportPreTileConfig config;
	@Inject
	private TeleportPreTileOverlay overlay;
	@Inject
	private TeleportPreTileMinimapOverlay minimapOverlay;
	@Inject
	private GroundMarkerReader groundMarkers;
	@Inject
	private Gson gson;


	private TeleportCatalog catalog;

	/** Read by the overlay on the render thread. */
	@Getter
	private volatile Ghost ghost;
	/** Read by the overlay on the render thread. */
	@Getter
	private volatile Learned learned;
	private boolean spotAnimSeen;

	/** Teleports whose dialog or list is open ("Rub" on a glory), waiting for the destination to be picked. */
	private volatile List<Teleport> opened = Collections.emptyList();
	private int openedTick;
	/**
	 * The open chat dialog's texts by child index, and the open list's entries, for picking one with a key; read on
	 * the key thread.
	 */
	private volatile String[] chatEntries;
	private volatile List<String> listEntries = Collections.emptyList();

	@Provides
	TeleportPreTileConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TeleportPreTileConfig.class);
	}

	@Override
	protected void startUp() throws IOException
	{
		if (catalog == null)
		{
			try (InputStream in = TeleportPreTilePlugin.class.getResourceAsStream("teleports.tsv"))
			{
				if (in == null)
				{
					throw new IOException("teleports.tsv is missing from the plugin");
				}
				catalog = TeleportCatalog.load(in);
			}
			log.debug("loaded {} teleports", catalog.all().size());
		}
		overlayManager.add(overlay);
		overlayManager.add(minimapOverlay);
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(minimapOverlay);
		keyManager.unregisterKeyListener(this);
		ghost = null;
		learned = null;
		settling = null;
		clearOpened();
		groundMarkers.invalidate();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		String option = e.getMenuOption();
		int itemId = e.getItemId();
		String target = ClickedName.of(e.getMenuTarget(),
			() -> itemId > 0 ? client.getItemDefinition(itemId).getName() : null,
			() -> e.getWidget() != null ? e.getWidget().getName() : null,
			() -> objectName(e),
			() -> e.getMenuEntry().getNpc() != null ? e.getMenuEntry().getNpc().getName() : null);

		Ghost g = ghost;
		if (g != null && g.getTracker().getTeleport().getKind() != Teleport.Kind.TELEPORT
			&& Text.clean(option).equalsIgnoreCase("Walk here"))
		{
			// walked off instead of boarding; a teleport is kept, as the player may click ahead where they will land
			ghost = null;
		}

		// fairy rings: the dials' code, sent with the interface's Confirm button, or a ring's own option
		String ring = e.getParam1() == InterfaceID.Fairyrings.CONFIRM
			? FairyRings.code(client.getVarbitValue(VarbitID.FAIRYRING_1), client.getVarbitValue(VarbitID.FAIRYRING_2),
				client.getVarbitValue(VarbitID.FAIRYRING_3))
			: FairyRings.codeOfOption(option, target);
		if (ring != null)
		{
			begin(catalog.fairyRing(ring));
			return;
		}

		if (!opened.isEmpty())
		{
			List<String> texts = new ArrayList<>(4);
			texts.add(option);
			texts.add(target);
			Widget w = e.getWidget();
			if (w != null && w.getText() != null)
			{
				texts.add(w.getText());
			}
			if (w != null && w.getName() != null)
			{
				texts.add(w.getName());
			}
			texts.addAll(rowTexts(e.getParam1(), e.getParam0()));
			for (String text : texts)
			{
				List<Teleport> chosen = TeleportCatalog.chosen(opened, text);
				if (!chosen.isEmpty())
				{
					begin(chosen);
					return;
				}
			}
		}

		LastDestination last = LastDestination.clicked(option, target);
		if (last != null)
		{
			Teleport t = catalog.byId(configManager.getConfiguration(TeleportPreTileConfig.GROUP,
				TeleportPreTileConfig.LAST_DESTINATION_PREFIX + last.key(client.getVarbitValue(last.getVarbit()))));
			if (t != null)
			{
				begin(Collections.singletonList(t));
			}
			return;
		}

		List<Teleport> started = nearPlayer(catalog.started(option, target, itemId));
		if (!started.isEmpty())
		{
			begin(started);
			return;
		}

		List<Teleport> opens = nearPlayer(catalog.opened(option, target, itemId));
		if (!opens.isEmpty())
		{
			opened = opens;
			openedTick = client.getTickCount();
			log.debug("click opened {} teleports: '{}' '{}' item {}", opens.size(), option, target, itemId);
			return;
		}
		if (log.isDebugEnabled())
		{
			// what wasn't taken for a teleport, to find clicks the data names differently from the game
			Widget w = e.getWidget();
			log.debug("click not a teleport: '{}' '{}' item {} id {} p0 {} p1 {} widget text '{}' name '{}'{}", option,
				target, itemId, e.getId(), e.getParam0(), e.getParam1(), w == null ? null : w.getText(),
				w == null ? null : w.getName(), opened.isEmpty() ? "" : " (a list of " + opened.size() + " is open)");
		}
	}

	/** The clicked object's name (as the player sees it, for objects that change with varbits), or null. */
	private String objectName(MenuOptionClicked e)
	{
		if (!e.getMenuAction().name().startsWith("GAME_OBJECT_"))
		{
			return null;
		}
		ObjectComposition def = client.getObjectDefinition(e.getId());
		if (def != null && def.getImpostorIds() != null)
		{
			ObjectComposition shown = def.getImpostor();
			def = shown != null ? shown : def;
		}
		return def != null ? def.getName() : null;
	}

	/** Picking a dialog option or list entry with its key, as most players do. */
	@Override
	public void keyPressed(KeyEvent e)
	{
		if (opened.isEmpty())
		{
			return;
		}
		char c = e.getKeyChar();
		// a key the game's list fixes for a choice (the house jewellery box, spirit trees) picks it outright
		List<Teleport> keyed = TeleportCatalog.chosenByKey(opened, c);
		if (!keyed.isEmpty())
		{
			clientThread.invoke(() -> begin(keyed));
			return;
		}
		// either kind may be a leftover from an earlier dialog: the one whose entry is a choice of the opened item wins
		String chat = DialogKeys.chatOption(chatEntries, c);
		String list = DialogKeys.listEntry(listEntries, c);
		if (chat == null && list == null)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			for (String entry : new String[]{chat, list})
			{
				List<Teleport> chosen = entry == null ? Collections.emptyList() : TeleportCatalog.chosen(opened, entry);
				if (!chosen.isEmpty())
				{
					begin(chosen);
					return;
				}
			}
		});
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
	}

	@Subscribe
	public void onClientTick(ClientTick e)
	{
		if (opened.isEmpty())
		{
			return;
		}
		// Keep the open dialog's entries ready for the key listener, which runs off the client thread and may run
		// after the game has already closed the dialog.
		String[] chat = byIndex(client.getWidget(InterfaceID.Chatmenu.OPTIONS));
		if (chat != null)
		{
			chatEntries = chat;
		}
		List<String> list = texts(client.getWidget(InterfaceID.Menu.LJ_LAYER1));
		if (list.isEmpty())
		{
			list = texts(client.getWidget(InterfaceID.TelenexusTeleport.TEXT1));
		}
		if (!list.isEmpty())
		{
			listEntries = list;
		}
	}

	/**
	 * The texts shown on the row clicked in a list whose rows are blank click boxes with the texts in a layer of
	 * their own, as in the house portal nexus: the clicked row's own child, and the texts level with it.
	 */
	private List<String> rowTexts(int widgetId, int row)
	{
		List<String> out = new ArrayList<>();
		if (widgetId != InterfaceID.TelenexusTeleport.ROWS1 || row < 0)
		{
			return out;
		}
		Widget rows = client.getWidget(widgetId);
		Widget clicked = rows == null ? null : rows.getChild(row);
		Widget[] texts = children(client.getWidget(InterfaceID.TelenexusTeleport.TEXT1));
		if (texts == null)
		{
			return out;
		}
		if (row < texts.length && texts[row] != null && texts[row].getText() != null)
		{
			out.add(texts[row].getText());
		}
		if (clicked != null)
		{
			int top = clicked.getRelativeY();
			int bottom = top + Math.max(clicked.getHeight(), 1);
			for (Widget t : texts)
			{
				int mid = t == null ? -1 : t.getRelativeY() + t.getHeight() / 2;
				if (t != null && t.getText() != null && mid >= top && mid < bottom)
				{
					out.add(t.getText());
				}
			}
		}
		log.debug("portal nexus row {}: {}", row, out);
		return out;
	}

	private static Widget[] children(Widget parent)
	{
		if (parent == null || parent.isHidden())
		{
			return null;
		}
		return parent.getDynamicChildren();
	}

	/** The children's texts by child index (null where a child has no text), or null when there are none. */
	private static String[] byIndex(Widget parent)
	{
		Widget[] children = children(parent);
		if (children == null || children.length == 0)
		{
			return null;
		}
		String[] out = new String[children.length];
		boolean any = false;
		for (Widget w : children)
		{
			String t = w == null ? null : Text.clean(w.getText());
			if (t != null && !t.isEmpty() && w.getIndex() >= 0 && w.getIndex() < out.length)
			{
				out[w.getIndex()] = t;
				any = true;
			}
		}
		return any ? out : null;
	}

	private static List<String> texts(Widget parent)
	{
		Widget[] children = children(parent);
		if (children == null)
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>(children.length);
		for (Widget w : children)
		{
			String t = w == null ? null : Text.clean(w.getText());
			if (t != null && !t.isEmpty())
			{
				out.add(t);
			}
		}
		return out;
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged e)
	{
		if (ghost != null && e.getActor() == client.getLocalPlayer())
		{
			spotAnimSeen = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		int tick = client.getTickCount();
		if (!opened.isEmpty() && tick - openedTick > CHOICE_TICKS)
		{
			clearOpened();
		}

		if (settling != null)
		{
			settle(tick);
		}

		Ghost g = ghost;
		Player player = client.getLocalPlayer();
		if (g == null || player == null)
		{
			return;
		}
		WorldPoint at = player.getWorldLocation();
		TeleportTracker.Result result = g.getTracker().tick(tick, at.getX(), at.getY(), at.getPlane(),
			player.getAnimation(), spotAnimSeen);
		if (result == TeleportTracker.Result.ARRIVED)
		{
			ghost = null;
			Teleport t = g.getTracker().getTeleport();
			if (rememberLastDestination(g.getTracker()))
			{
				learned = new Learned(markersFor(t), false, new Blink(System.currentTimeMillis()));
			}
			// the house (and other instances) may first put the player somewhere while it loads and move them a tick
			// or two later: the arrival tile is taken once they stay put
			settling = new Settling(t, t.isHouse() ? HOUSE_KEY : INSTANCE_PREFIX + t.getId(),
				t.isHouse() ? t.getHouseEntry() : INSTANCE_ENTRY, !t.isHouse(), tick);
			settle(tick);
		}
		else if (result == TeleportTracker.Result.CANCELLED)
		{
			ghost = null;
		}
	}

	/** A trip's arrival waiting for the player to stay put before the instance and arrival tile are remembered. */
	private static final class Settling
	{
		final Teleport teleport;
		final String key;
		final String entry;
		final boolean forgetIfNot;
		final int startTick;
		int lastX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;
		int lastPlane = -1;
		int stillTicks;

		Settling(Teleport teleport, String key, String entry, boolean forgetIfNot, int startTick)
		{
			this.teleport = teleport;
			this.key = key;
			this.entry = entry;
			this.forgetIfNot = forgetIfNot;
			this.startTick = startTick;
		}
	}

	/**
	 * Ticks the player must stay on one tile after arriving for that to be the arrival tile. In game the house puts the
	 * player on a loading tile for one tick and then on the real one, so one still tick is enough.
	 */
	private static final int SETTLE_TICKS = 1;
	/** Gives up on settling after this many ticks (the player walked straight off). */
	private static final int SETTLE_MAX_TICKS = 8;

	private Settling settling;

	private void settle(int tick)
	{
		Settling st = settling;
		Player player = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (st == null || player == null || wv == null || player.getLocalLocation() == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		LocalPoint lp = player.getLocalLocation();
		int x = lp.getSceneX();
		int y = lp.getSceneY();
		int plane = wv.getPlane();
		log.debug("{} ({}): tick {} after arriving, scene tile {},{} plane {}{}", st.key, st.entry, tick - st.startTick,
			x, y, plane, wv.isInstance() ? " (instance)" : "");
		boolean jumped = st.lastPlane != plane || Math.max(Math.abs(x - st.lastX), Math.abs(y - st.lastY)) > 0;
		st.lastX = x;
		st.lastY = y;
		st.lastPlane = plane;
		st.stillTicks = jumped ? 0 : st.stillTicks + 1;
		if (st.stillTicks < SETTLE_TICKS && tick - st.startTick < SETTLE_MAX_TICKS)
		{
			return;
		}
		settling = null;
		if (st.stillTicks < SETTLE_TICKS)
		{
			log.debug("{} ({}): the player kept moving after arriving; not saved", st.key, st.entry);
			return;
		}
		if (!st.teleport.isHouse() && !wv.isInstance())
		{
			learnHeights(st.teleport, wv);
		}
		if (rememberInstance(st.key, st.entry, st.forgetIfNot))
		{
			HouseLayout layout = loadLayout(st.key);
			int[] arrivedAt = layout == null ? null : layout.entry(st.entry);
			if (arrivedAt != null)
			{
				learned = new Learned(houseMarkers(layout, arrivedAt), true, new Blink(System.currentTimeMillis()));
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState s = e.getGameState();
		if (s == GameState.LOGIN_SCREEN || s == GameState.HOPPING || s == GameState.CONNECTION_LOST)
		{
			ghost = null;
			learned = null;
			settling = null;
			clearOpened();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (GroundMarkerReader.GROUP.equals(e.getGroup()) && e.getKey().startsWith("region_"))
		{
			groundMarkers.invalidate();
			refreshMarkers();
		}
		else if (TeleportPreTileConfig.GROUP.equals(e.getGroup())
			&& ("drawDistance".equals(e.getKey()) || "learnHeights".equals(e.getKey())))
		{
			refreshMarkers();
		}
		else if (TeleportPreTileConfig.GROUP.equals(e.getGroup()) && TeleportPreTileConfig.FORGET_LEARNED.equals(e.getKey())
			&& "true".equals(e.getNewValue()))
		{
			// the panel has already asked (the setting's warning); forget, then untick the box again
			clientThread.invoke(this::forgetLearned);
		}
	}

	/**
	 * Forgets everything learned on trips for this account: the house's layout, the instances, the ground's heights,
	 * and what each item's "last destination" means. The player's tile markers are not touched.
	 */
	private void forgetLearned()
	{
		String group = TeleportPreTileConfig.GROUP;
		int forgotten = 0;
		String profile = configManager.getRSProfileKey();
		if (profile != null)
		{
			for (String key : configManager.getRSProfileConfigurationKeys(group, profile, ""))
			{
				if (key.equals(HOUSE_KEY) || key.startsWith(INSTANCE_PREFIX) || key.startsWith(HEIGHTS_PREFIX))
				{
					configManager.unsetRSProfileConfiguration(group, key);
					forgotten++;
				}
			}
		}
		for (String full : configManager.getConfigurationKeys(group + "." + TeleportPreTileConfig.LAST_DESTINATION_PREFIX))
		{
			configManager.unsetConfiguration(group, full.substring(group.length() + 1));
			forgotten++;
		}
		ghost = null;
		learned = null;
		settling = null;
		configManager.setConfiguration(group, TeleportPreTileConfig.FORGET_LEARNED, false);
		// the settings panel doesn't redraw when a setting changes behind it, so untick its box too
		SwingUtilities.invokeLater(TeleportPreTilePlugin::untickForgetInPanel);
		log.debug("forgot {} learned entries", forgotten);
	}

	/**
	 * Unticks the Forget learned data box in an open settings panel: the box next to the label with that name.
	 * Unticking a box this way doesn't count as a click, so it changes no setting.
	 */
	private static void untickForgetInPanel()
	{
		for (Window window : Window.getWindows())
		{
			untickForget(window);
		}
	}

	private static void untickForget(Container c)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof JLabel && FORGET_LABEL.equals(((JLabel) child).getText()))
			{
				for (Component sibling : c.getComponents())
				{
					if (sibling instanceof JCheckBox)
					{
						((JCheckBox) sibling).setSelected(false);
					}
				}
			}
			else if (child instanceof Container)
			{
				untickForget((Container) child);
			}
		}
	}

	/** The candidates with the rides boarded somewhere else than near the player left out. */
	private List<Teleport> nearPlayer(List<Teleport> candidates)
	{
		Player player = client.getLocalPlayer();
		if (player == null || candidates.isEmpty())
		{
			return candidates;
		}
		WorldPoint at = player.getWorldLocation();
		return TeleportCatalog.near(candidates, at.getX(), at.getY(), at.getPlane());
	}

	/** Starts showing the destination of the one teleport these candidates resolve to, if they resolve. */
	private void begin(List<Teleport> candidates)
	{
		clearOpened();
		Teleport t = TeleportCatalog.resolve(candidates, client::getVarbitValue);
		Player player = client.getLocalPlayer();
		if (t == null || player == null)
		{
			return;
		}
		WorldPoint at = player.getWorldLocation();
		TeleportTracker tracker = new TeleportTracker(t, client.getTickCount(), at.getX(), at.getY(), at.getPlane(),
			player.getAnimation());
		spotAnimSeen = false;
		learned = null;
		settling = null;
		ghost = ghostFor(tracker);
		log.debug("teleport {} to {},{},{}", t.getName(), t.getX(), t.getY(), t.getPlane());
	}

	/** The markers to draw for this trip and the tile they are placed around. */
	private Ghost ghostFor(TeleportTracker tracker)
	{
		Teleport t = tracker.getTeleport();
		if (!t.isHouse())
		{
			// a teleport into an instance: the instance as it was last time, if it went to one before
			HouseLayout instance = loadLayout(INSTANCE_PREFIX + t.getId());
			int[] at = instance == null ? null : instance.entry(INSTANCE_ENTRY);
			if (at != null)
			{
				List<GroundMarkerReader.Marker> markers = houseMarkers(instance, at);
				log.debug("instance: {} markers around scene tile {},{} plane {}", markers.size(), at[0], at[1], at[2]);
				return new Ghost(tracker, markers, at[0], at[1], null);
			}
			return new Ghost(tracker, markersFor(t), t.getX(), t.getY(), loadHeights(t));
		}
		// the house: the markers of the house as last seen, around the tile the player arrived on then. The first
		// trip shows nothing (tracked all the same, so the layout is learned when the player lands)
		HouseLayout house = loadLayout(HOUSE_KEY);
		int[] at = house == null ? null : house.entry(t.getHouseEntry());
		if (at == null)
		{
			log.debug("house ({}): layout not seen yet, learning it on arrival", t.getHouseEntry());
			return new Ghost(tracker, Collections.emptyList(), 0, 0, null);
		}
		List<GroundMarkerReader.Marker> markers = houseMarkers(house, at);
		log.debug("house ({}): {} markers around scene tile {},{} plane {}", t.getHouseEntry(), markers.size(), at[0],
			at[1], at[2]);
		return new Ghost(tracker, markers, at[0], at[1], null);
	}

	/**
	 * The player's marked tiles in the house, as scene tiles of the house as last seen: each marker is saved at its
	 * room template's tile, and shows wherever that template is placed (as the Tile Markers plugin draws them).
	 */
	private List<GroundMarkerReader.Marker> houseMarkers(HouseLayout house, int[] at)
	{
		int range = config.drawDistance();
		List<GroundMarkerReader.Marker> out = new ArrayList<>();
		for (HouseLayout.Placement p : house.placements(at[2]))
		{
			for (GroundMarkerReader.Marker m : groundMarkers.around(p.templateX + 3, p.templateY + 3, p.templatePlane, 4))
			{
				if (!p.holds(m.getX(), m.getY(), m.getPlane()))
				{
					continue;
				}
				int[] scene = p.toScene(m.getX(), m.getY());
				if (Math.abs(scene[0] - at[0]) <= range && Math.abs(scene[1] - at[1]) <= range)
				{
					out.add(new GroundMarkerReader.Marker(scene[0], scene[1], at[2], m.getColor(), m.getLabel()));
				}
			}
		}
		return out;
	}

	private HouseLayout loadLayout(String key)
	{
		String json = configManager.getRSProfileConfiguration(TeleportPreTileConfig.GROUP, key);
		if (json == null)
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, HouseLayout.class);
		}
		catch (JsonParseException e)
		{
			log.debug("unreadable house layout", e);
			return null;
		}
	}

	/**
	 * After landing in an instance (the house, or any other a teleport goes to): remember its layout (the instance's
	 * template chunks) and where the player arrived for this entry, for the next trip's preview. Saved per account; a
	 * rebuilt house, or an instance built differently, starts afresh.
	 *
	 * @param forgetIfNot when not landing in an instance, forget what was saved (the teleport no longer goes to one)
	 * @return true when something new was saved (a new or rebuilt layout, or a new arrival tile)
	 */
	private boolean rememberInstance(String key, String entry, boolean forgetIfNot)
	{
		WorldView wv = client.getTopLevelWorldView();
		Player player = client.getLocalPlayer();
		if (wv == null || player == null || player.getLocalLocation() == null)
		{
			return false;
		}
		if (!wv.isInstance())
		{
			if (forgetIfNot && configManager.getRSProfileConfiguration(TeleportPreTileConfig.GROUP, key) != null)
			{
				configManager.unsetRSProfileConfiguration(TeleportPreTileConfig.GROUP, key);
			}
			return false;
		}
		int[][][] chunks = wv.getInstanceTemplateChunks();
		HouseLayout house = loadLayout(key);
		if (house == null || !house.sameChunks(chunks))
		{
			house = new HouseLayout(chunks);
		}
		LocalPoint at = player.getLocalLocation();
		int[] before = house.entry(entry);
		int[] now = {at.getSceneX(), at.getSceneY(), wv.getPlane()};
		if (java.util.Arrays.equals(before, now))
		{
			return false;
		}
		house.arrived(entry, now[0], now[1], now[2]);
		configManager.setRSProfileConfiguration(TeleportPreTileConfig.GROUP, key, gson.toJson(house));
		log.debug("{} ({}): layout saved, {} room chunks on plane {}, arrived at scene tile {},{}", key, entry,
			house.placements(wv.getPlane()).size(), wv.getPlane(), at.getSceneX(), at.getSceneY());
		return true;
	}

	private List<GroundMarkerReader.Marker> markersFor(Teleport t)
	{
		return groundMarkers.around(t.getX(), t.getY(), t.getPlane(), config.drawDistance());
	}

	private static String heightsKey(Teleport t)
	{
		return HEIGHTS_PREFIX + t.getX() + "_" + t.getY() + "_" + t.getPlane();
	}

	/** The ground's heights at this teleport's landing spot from an earlier trip, or null (draw flat). */
	private TileHeights loadHeights(Teleport t)
	{
		if (!config.learnHeights())
		{
			return null;
		}
		return TileHeights.decode(configManager.getRSProfileConfiguration(TeleportPreTileConfig.GROUP, heightsKey(t)));
	}

	/**
	 * Just landed from this teleport on the main map and stayed put: remember the ground's height under the marked
	 * tiles around its landing spot, for the next trip. Done on every trip, so markers added since are learned too.
	 */
	private void learnHeights(Teleport t, WorldView wv)
	{
		Player player = client.getLocalPlayer();
		if (!config.learnHeights() || player == null)
		{
			return;
		}
		WorldPoint at = player.getWorldLocation();
		if (at.getPlane() != t.getPlane()
			|| Math.max(Math.abs(at.getX() - t.getX()), Math.abs(at.getY() - t.getY())) > LANDING_TILES)
		{
			return;
		}
		List<GroundMarkerReader.Marker> markers = markersFor(t);
		int[][] marked = new int[markers.size()][];
		for (int i = 0; i < marked.length; i++)
		{
			marked[i] = new int[]{markers.get(i).getX(), markers.get(i).getY()};
		}
		int plane = t.getPlane();
		TileHeights heights = TileHeights.learn(t.getX(), t.getY(), marked, (x, y, corner) ->
		{
			LocalPoint lp = LocalPoint.fromWorld(wv, x, y);
			if (lp == null)
			{
				return null;
			}
			int lx = lp.getX() + (corner == 1 || corner == 2 ? HALF_TILE : -HALF_TILE);
			int ly = lp.getY() + (corner >= 2 ? HALF_TILE : -HALF_TILE);
			if (lx < 0 || ly < 0 || (lx >> 7) >= wv.getSizeX() || (ly >> 7) >= wv.getSizeY())
			{
				return null;
			}
			return Perspective.getTileHeight(client, new LocalPoint(lx, ly, wv), plane);
		});
		if (heights == null)
		{
			return;
		}
		String value = heights.encode();
		String key = heightsKey(t);
		String saved = configManager.getRSProfileConfiguration(TeleportPreTileConfig.GROUP, key);
		if (!value.equals(saved))
		{
			configManager.setRSProfileConfiguration(TeleportPreTileConfig.GROUP, key, value);
			log.debug("{}: heights of {} raised or sloped marked tiles saved", key, heights.size());
		}
		if (heights.addsTo(TileHeights.decode(saved)))
		{
			// a height the next trip will use that it didn't know: flash the markers, as for other things learned
			learned = new Learned(markers, false, new Blink(System.currentTimeMillis()));
		}
	}

	private void refreshMarkers()
	{
		clientThread.invoke(() ->
		{
			Ghost g = ghost;
			if (g != null)
			{
				ghost = ghostFor(g.getTracker());
			}
		});
	}

	/**
	 * After landing from a teleport of an item with a "last destination" option, the item's varbit holds this
	 * destination: remember which teleport that value means (see {@link LastDestination}).
	 */
	/** @return true when a new meaning of the item's "last destination" value was saved */
	private boolean rememberLastDestination(TeleportTracker tracker)
	{
		Teleport t = tracker.getTeleport();
		LastDestination last = LastDestination.of(t);
		if (last == null || client.getTopLevelWorldView().isInstance() || tracker.getArrivalPlane() != t.getPlane()
			|| Math.max(Math.abs(tracker.getArrivalX() - t.getX()), Math.abs(tracker.getArrivalY() - t.getY())) > LANDING_TILES)
		{
			return false;
		}
		int value = client.getVarbitValue(last.getVarbit());
		if (value == 0)
		{
			return false;
		}
		String key = TeleportPreTileConfig.LAST_DESTINATION_PREFIX + last.key(value);
		if (t.getId().equals(configManager.getConfiguration(TeleportPreTileConfig.GROUP, key)))
		{
			return false;
		}
		configManager.setConfiguration(TeleportPreTileConfig.GROUP, key, t.getId());
		return true;
	}

	private void clearOpened()
	{
		opened = Collections.emptyList();
		chatEntries = null;
		listEntries = Collections.emptyList();
	}
}
