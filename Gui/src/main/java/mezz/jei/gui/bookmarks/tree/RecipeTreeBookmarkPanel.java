package mezz.jei.gui.bookmarks.tree;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkDisplayEntry;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkScrollHandler;
import mezz.jei.gui.overlay.bookmarks.BookmarkChainSlotVisuals;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotDisplayMode;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisualContext;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.LayoutPlaceholderElement;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The existing bookmark grid, scoped to one group and navigated in whole rows. */
public final class RecipeTreeBookmarkPanel {
	private static final int CELL = IngredientGridLayout.INGREDIENT_WIDTH;
	private final BookmarkList bookmarks;
	private final int groupId;
	private final IIngredientManager ingredients;
	private final IngredientListRenderer renderer;
	private final IngredientGridTooltipHelper tooltipHelper;
	private final Map<IElement<?>, BookmarkDisplayEntry<IBookmark>> entries = new IdentityHashMap<>();
	private List<IElement<?>> elements = List.of();
	private ImmutableRect2i area = ImmutableRect2i.EMPTY;
	private int columns = 1, rows = 1, firstRow;
	private boolean draggingScrollbar;
	private long version = -1;

	public RecipeTreeBookmarkPanel(BookmarkList bookmarks, int groupId, IIngredientManager ingredients) {
		this.bookmarks = bookmarks;
		this.groupId = groupId;
		this.ingredients = ingredients;
		renderer = new IngredientListRenderer(ingredients, false);
		tooltipHelper = new IngredientGridTooltipHelper(ingredients, Internal.getJeiClientConfigs().getIngredientFilterConfig(),
			Internal.getClientToggleState(), Internal.getKeyMappings(), Internal.getJeiRuntime().getJeiHelpers().getColorHelper());
		renderer.setSlotVisualsResolver(context -> {
			var entry = entries.get(context.element());
			var mode = Screen.hasControlDown() ? BookmarkSlotDisplayMode.REAL : Screen.hasShiftDown() ? BookmarkSlotDisplayMode.SHIFT : BookmarkSlotDisplayMode.DEFAULT;
			return entry == null ? Optional.empty() : BookmarkChainSlotVisuals.create(entry,
				new BookmarkSlotVisualContext(mode, context.hoveredElement().map(entries::get).map(value -> (BookmarkDisplayEntry<?>) value),
					context.rowIndex(), context.hoveredRowIndex(), Internal.getJeiClientConfigs().getClientConfig().bookmarkRecipeMarkerMode().getValue()));
		});
	}

	public void updateBounds(int width, int top, int height) {
		int nextColumns = Math.max(1, (width - 12) / CELL);
		int nextRows = Math.max(1, (height - top - 8) / CELL);
		var next = new ImmutableRect2i(4, top + 4, nextColumns * CELL, nextRows * CELL);
		if (next.equals(area)) {
			refresh(); return;
		}
		area = next;
		columns = nextColumns;
		rows = nextRows;
		renderer.clear();
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < columns; col++) {
				renderer.add(new IngredientListSlot(area.x() + col * CELL, area.y() + row * CELL, CELL, CELL, IngredientGridLayout.INGREDIENT_PADDING));
			}
		}
		version = -1;
		refresh();
	}

	public void refresh() {
		if (area.width() == 0 || version == bookmarks.getChangeVersion()) {
			return;
		}
		version = bookmarks.getChangeVersion();
		entries.clear();
		List<IElement<?>> next = new ArrayList<>();
		for (var slot : bookmarks.getGroupEditorSlots(groupId, columns)) {
			while (next.size() < slot.slotIndex()) { next.add(LayoutPlaceholderElement.INSTANCE); }
			var element = bookmarks.createDisplayElement(slot.entry());
			next.add(element);
			entries.put(element, slot.entry());
		}
		elements = List.copyOf(next);
		restoreFirstRow(firstRow);
	}

	public int firstRow() { return firstRow; }

	public void restoreFirstRow(int row) {
		firstRow = scrollRow(row, 0, elements.size(), columns, rows);
		renderer.set(Math.min(elements.size(), firstRow * columns), elements);
	}

	public Optional<IElement<?>> elementAt(double x, double y) {
		if (!area.contains(x, y)) {
			return Optional.empty();
		}
		return renderer.getSlots().filter(slot -> slot.isMouseOver(x, y)).findFirst().flatMap(IngredientListSlot::getOptionalElement);
	}

	public Optional<IBookmark> bookmarkAt(double x, double y) { return elementAt(x, y).flatMap(IElement::getBookmark); }

	public boolean clickScrollbar(double x, double y, int width) {
		if (x < width - 6 || y < area.y() || y >= area.y() + area.height() || elements.size() <= rows * columns) {
			return false;
		}
		draggingScrollbar = true;
		return dragScrollbar(y);
	}

	public boolean dragScrollbar(double y) {
		if (!draggingScrollbar) {
			return false;
		}
		int totalRows = Math.ceilDiv(elements.size(), columns);
		int thumb = Math.max(6, area.height() * rows / Math.max(1, totalRows));
		restoreFirstRow((int) Math.round((y - area.y() - thumb / 2.0) * (totalRows - rows) / Math.max(1, area.height() - thumb)));
		return true;
	}

	public void release() { draggingScrollbar = false; }

	public void scroll(double x, double y, double delta, boolean control, boolean alt, boolean shift) {
		var hovered = bookmarkAt(x, y);
		if (hovered.isPresent() && (control || alt || shift)) {
			var overlay = Internal.getJeiRuntime().getBookmarkOverlay();
			long step = overlay instanceof BookmarkOverlay bookmarkOverlay ? bookmarkOverlay.getScrollStep().getEffectiveStep() : 64;
			hovered.ifPresent(bookmark -> BookmarkScrollHandler.apply(bookmarks, bookmark, delta, control, alt, shift, step,
				amount -> bookmarks.shiftRecipeAmount(bookmark, amount)));
			refresh();
			return;
		}
		int next = scrollRow(firstRow, delta, elements.size(), columns, rows);
		if (next != firstRow) {
			restoreFirstRow(next);
		}
	}

	public static int scrollRow(int current, double delta, int size, int columns, int visibleRows) {
		int max = Math.max(0, Math.ceilDiv(size, Math.max(1, columns)) - visibleRows);
		return Math.clamp(current - (int) Math.signum(delta), 0, max);
	}

	public void draw(GuiGraphics graphics, int width, int top, int height, int mouseX, int mouseY) {
		graphics.fill(0, top, width, height, 0x66000000);
		renderer.render(graphics);
		renderer.getSlots().filter(slot -> slot.isMouseOver(mouseX, mouseY)).findFirst()
			.ifPresent(slot -> IngredientGrid.drawHighlight(graphics, slot.getArea()));
		int totalRows = Math.ceilDiv(elements.size(), columns);
		if (totalRows > rows) {
			int track = area.height();
			int thumb = Math.max(6, track * rows / totalRows);
			int y = area.y() + (track - thumb) * firstRow / (totalRows - rows);
			graphics.fill(width - 4, area.y(), width - 2, area.y() + track, 0xFF333333);
			graphics.fill(width - 4, y, width - 2, y + thumb, 0xFFAAAAAA);
		}
	}

	public void drawTooltip(GuiGraphics graphics, int x, int y) { elementAt(x, y).ifPresent(element -> tooltip(graphics, x, y, element)); }

	private <T> void tooltip(GuiGraphics graphics, int x, int y, IElement<T> element) {
		JeiTooltip tooltip = new JeiTooltip();
		var type = element.getTypedIngredient().getType();
		tooltip.setIngredient(element.getTypedIngredient());
		element.getTooltip(tooltip, tooltipHelper, ingredients.getIngredientRenderer(type), ingredients.getIngredientHelper(type));
		tooltip.draw(graphics, x, y);
	}
}
