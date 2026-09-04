package mezz.jei.gui.bookmarks.hotkeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BookmarkHotkeyPlanner {
	private BookmarkHotkeyPlanner() {
	}

	public static List<BookmarkHotkeyAvailability> plan(BookmarkHotkeyContext context) {
		return plan(context, BookmarkHotkeyBridge.none());
	}

	public static List<BookmarkHotkeyAvailability> plan(BookmarkHotkeyContext context, BookmarkHotkeyBridge bridge) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(bridge, "bridge");
		List<BookmarkHotkeyAvailability> actions = new ArrayList<>();
		switch (context.subject()) {
			case INGREDIENT -> addIngredientActions(actions, context);
			case ITEM_BOOKMARK -> addItemBookmarkActions(actions, context);
			case RECIPE_BOOKMARK -> addRecipeBookmarkActions(actions, context);
			case EMPTY_GROUP_PANEL -> add(actions, BookmarkHotkeyAction.GROUP_CREATE_OR_INCLUDE_DRAG, BookmarkHotkeySupport.SUPPORTED);
			case DEFAULT_GROUP_CONTROL -> addDefaultGroupActions(actions, context);
			case GROUP -> addGroupActions(actions, context);
		}
		addFutureIngredientActions(actions, context);
		return actions.stream()
			.map(action -> new BookmarkHotkeyAvailability(
				action.action(),
				Objects.requireNonNull(
					bridge.getSupport(action.action(), action.support(), context),
					"bridge support"
				)
			))
			.toList();
	}

	private static void addIngredientActions(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyContext context) {
		if (!context.hasIngredient()) {
			return;
		}
		add(actions, BookmarkHotkeyAction.OPEN_RECIPE, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.OPEN_USAGE, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.ADD_BOOKMARK, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.ADD_BOOKMARK_WITH_COUNT, BookmarkHotkeySupport.SUPPORTED);
		if (context.hasRecipe()) {
			add(actions, BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK, BookmarkHotkeySupport.SUPPORTED);
			add(actions, BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK_WITH_COUNT, BookmarkHotkeySupport.SUPPORTED);
		}
	}

	private static void addItemBookmarkActions(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyContext context) {
		if (!context.hasIngredient()) {
			return;
		}
		add(actions, BookmarkHotkeyAction.OPEN_RECIPE, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.OPEN_USAGE, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.REMOVE_ITEM_BOOKMARK, BookmarkHotkeySupport.SUPPORTED);
		addScrollActions(actions);
		addPullActions(actions, context);
	}

	private static void addRecipeBookmarkActions(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyContext context) {
		add(actions, BookmarkHotkeyAction.REMOVE_RECIPE_BOOKMARK, BookmarkHotkeySupport.SUPPORTED);
		addScrollActions(actions);
		add(actions, BookmarkHotkeyAction.TOGGLE_INPUT_NONCONSUMABLE, BookmarkHotkeySupport.SUPPORTED);
		if (context.canTransferRecipe()) {
			add(actions, BookmarkHotkeyAction.TRANSFER_RECIPE_ONCE, BookmarkHotkeySupport.SUPPORTED);
		}
		if (context.canMaxTransferRecipe()) {
			add(actions, BookmarkHotkeyAction.TRANSFER_RECIPE_MAX, BookmarkHotkeySupport.SUPPORTED);
		}
		addPullActions(actions, context);
	}

	private static void addGroupActions(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyContext context) {
		add(actions, BookmarkHotkeyAction.GROUP_CREATE_OR_INCLUDE_DRAG, BookmarkHotkeySupport.SUPPORTED);
		if (!context.isGrouped()) {
			return;
		}
		add(actions, BookmarkHotkeyAction.GROUP_EXCLUDE_DRAG, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_MOVE_DRAG, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_DROP_DRAG, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_TOGGLE_COLLAPSED, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_REMOVE, BookmarkHotkeySupport.SUPPORTED);
		addPullActions(actions, context);
		if (context.isCraftingGroup() && context.hasAutoCraftingBridge()) {
			add(actions, BookmarkHotkeyAction.CRAFT_ALL, BookmarkHotkeySupport.NEEDS_BRIDGE);
			add(actions, BookmarkHotkeyAction.CRAFT_MISSING, BookmarkHotkeySupport.NEEDS_BRIDGE);
		}
		if (context.hasOverlayRendererBridge()) {
			add(actions, BookmarkHotkeyAction.OVERLAY_RECIPE, BookmarkHotkeySupport.NEEDS_BRIDGE);
		}
		if (context.hasCraftingGridBridge()) {
			add(actions, BookmarkHotkeyAction.FILL_CRAFTING_GRID, BookmarkHotkeySupport.NEEDS_BRIDGE);
			add(actions, BookmarkHotkeyAction.FILL_CRAFTING_GRID_QUANTITY, BookmarkHotkeySupport.NEEDS_BRIDGE);
		}
		add(actions, BookmarkHotkeyAction.SHIFT_AMOUNT, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.SHIFT_AMOUNT_STEP, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.SHIFT_GROUP_AMOUNT, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.CYCLE_PERMUTATION, BookmarkHotkeySupport.SUPPORTED);
	}

	private static void addDefaultGroupActions(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyContext context) {
		add(actions, BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_TOGGLE_COLLAPSED, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING, BookmarkHotkeySupport.SUPPORTED);
		addPullActions(actions, context);
		add(actions, BookmarkHotkeyAction.SHIFT_AMOUNT, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.SHIFT_AMOUNT_STEP, BookmarkHotkeySupport.SUPPORTED);
	}

	private static void addScrollActions(List<BookmarkHotkeyAvailability> actions) {
		add(actions, BookmarkHotkeyAction.SHIFT_AMOUNT, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.SHIFT_AMOUNT_STEP, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.CYCLE_PERMUTATION, BookmarkHotkeySupport.SUPPORTED);
	}

	private static void addPullActions(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyContext context) {
		if (context.hasBookmarkContainerHandler()) {
			add(actions, BookmarkHotkeyAction.PULL_ITEMS, BookmarkHotkeySupport.NEEDS_BRIDGE);
			add(actions, BookmarkHotkeyAction.PULL_ITEMS_SHIFT, BookmarkHotkeySupport.NEEDS_BRIDGE);
		}
	}

	private static void addFutureIngredientActions(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyContext context) {
		if (!context.hasIngredient()) {
			return;
		}
		add(actions, BookmarkHotkeyAction.COPY_NAME, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.COPY_OREDICT, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.COPY_ID, BookmarkHotkeySupport.SUPPORTED);
		add(actions, BookmarkHotkeyAction.FAVORITE_RECIPE, BookmarkHotkeySupport.UNSUPPORTED_FOR_NOW);
	}

	private static void add(
		List<BookmarkHotkeyAvailability> actions,
		BookmarkHotkeyAction action,
		BookmarkHotkeySupport support
	) {
		actions.add(new BookmarkHotkeyAvailability(action, support));
	}
}
