package mezz.jei.gui.collapsible;

import mezz.jei.gui.bookmarks.BookmarkSlotBorder;
import mezz.jei.gui.overlay.IngredientListSlotContext;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkSlotVisuals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Maps collapsible group slots to the existing bookmark slot visuals:
 * background, merged group border and an amount badge for collapsed groups.
 * The border sides are computed from the slot grid, mirroring GTNH NEI.
 */
public final class CollapsibleSlotVisualsProvider {
	private static final int BORDER_ALPHA_BOOST = 102;

	private final Supplier<List<IngredientListSlot>> rawSlotsSupplier;
	private final Supplier<CollapsibleSettings> settingsSupplier;
	private Map<Integer, String> groupBySlotIndex = Map.of();
	private int slotCount;
	private int columnCount = -1;
	private int layoutVersion = -1;
	private boolean dirty = true;

	public CollapsibleSlotVisualsProvider(
		Supplier<List<IngredientListSlot>> rawSlotsSupplier,
		Supplier<CollapsibleSettings> settingsSupplier
	) {
		this.rawSlotsSupplier = rawSlotsSupplier;
		this.settingsSupplier = settingsSupplier;
	}

	public void invalidate() {
		this.dirty = true;
	}

	public Optional<BookmarkSlotVisuals> apply(IngredientListSlotContext context) {
		updateIfNeeded(context);
		if (!(context.element() instanceof CollapsedGroupElement<?> groupElement)) {
			return Optional.empty();
		}
		int size = groupElement.getGroupSize();
		if (size <= 1) {
			return Optional.empty();
		}
		String groupId = groupElement.group().id();
		if (!groupId.equals(groupBySlotIndex.get(context.slotIndex()))) {
			return Optional.empty();
		}
		boolean collapsed = groupElement.isCollapsed();
		int columns = context.columnCount();
		int slotIndex = context.slotIndex();
		boolean left = slotIndex % columns == 0 || !sameGroup(slotIndex - 1, groupId);
		boolean right = slotIndex % columns == columns - 1 || !sameGroup(slotIndex + 1, groupId);
		boolean top = slotIndex < columns || !sameGroup(slotIndex - columns, groupId);
		boolean bottom = slotIndex + columns >= slotCount || !sameGroup(slotIndex + columns, groupId);
		CollapsibleSettings settings = settingsSupplier.get();
		int backgroundColor = collapsed ? settings.collapsedColor() : settings.expandedColor();
		BookmarkSlotBorder border = new BookmarkSlotBorder(
			groupId, boostAlpha(backgroundColor), left, right, top, bottom);
		return Optional.of(new BookmarkSlotVisuals(
			OptionalInt.of(backgroundColor),
			OptionalInt.empty(),
			collapsed ? Optional.of(String.valueOf(size)) : Optional.empty(),
			OptionalInt.of(0xFFFFFFFF),
			Optional.empty(),
			OptionalInt.empty(),
			Optional.empty(),
			OptionalInt.empty(),
			Optional.of(border)
		));
	}

	private void updateIfNeeded(IngredientListSlotContext context) {
		if (!dirty &&
			columnCount == context.columnCount() &&
			slotCount == context.slotCount() &&
			layoutVersion == context.layoutVersion()
		) {
			return;
		}
		this.dirty = false;
		this.columnCount = context.columnCount();
		this.layoutVersion = context.layoutVersion();
		Map<Integer, String> map = new HashMap<>();
		List<IngredientListSlot> slots = rawSlotsSupplier.get();
		this.slotCount = slots.size();
		for (int i = 0; i < slots.size(); i++) {
			IElement<?> element = slots.get(i).getElement();
			if (element instanceof CollapsedGroupElement<?> groupElement) {
				map.put(i, groupElement.group().id());
			}
		}
		this.groupBySlotIndex = map;
	}

	private boolean sameGroup(int slotIndex, String groupId) {
		return groupId.equals(groupBySlotIndex.get(slotIndex));
	}

	/**
	 * GTNH NEI keeps the RGB and only boosts the alpha for the border color.
	 */
	private static int boostAlpha(int argb) {
		int alpha = (argb >>> 24) & 0xFF;
		int boosted = Math.min(alpha + BORDER_ALPHA_BOOST, 255);
		return (boosted << 24) | (argb & 0xFFFFFF);
	}
}
