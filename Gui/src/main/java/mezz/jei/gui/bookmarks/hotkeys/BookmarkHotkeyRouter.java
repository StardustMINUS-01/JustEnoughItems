package mezz.jei.gui.bookmarks.hotkeys;

import java.util.List;
import java.util.Optional;

public final class BookmarkHotkeyRouter {
	public enum KeyboardKey {
		C,
		D,
		X
	}

	private BookmarkHotkeyRouter() {
	}

	public static Optional<BookmarkHotkeyAction> resolveBookmarkKeyAction(
		BookmarkHotkeyContext context,
		boolean shiftDown,
		boolean controlDown
	) {
		List<BookmarkHotkeyAvailability> availableActions = BookmarkHotkeyPlanner.plan(context);
		BookmarkHotkeyAction requestedAction = switch (context.subject()) {
			case INGREDIENT -> resolveIngredientBookmarkAction(shiftDown, controlDown);
			case ITEM_BOOKMARK -> BookmarkHotkeyAction.REMOVE_ITEM_BOOKMARK;
			case RECIPE_BOOKMARK -> BookmarkHotkeyAction.REMOVE_RECIPE_BOOKMARK;
			case GROUP -> shiftDown ? BookmarkHotkeyAction.GROUP_REMOVE : null;
			case DEFAULT_GROUP_CONTROL, EMPTY_GROUP_PANEL -> null;
		};
		if (requestedAction == null || !isSupported(availableActions, requestedAction)) {
			return Optional.empty();
		}
		return Optional.of(requestedAction);
	}

	public static Optional<BookmarkHotkeyAction> resolveBookmarkScrollAction(
		BookmarkHotkeyContext context,
		boolean controlDown,
		boolean altDown,
		boolean shiftDown
	) {
		List<BookmarkHotkeyAvailability> availableActions = BookmarkHotkeyPlanner.plan(context);
		BookmarkHotkeyAction requestedAction = null;
		if (controlDown) {
			requestedAction = altDown ? BookmarkHotkeyAction.SHIFT_AMOUNT_STEP : BookmarkHotkeyAction.SHIFT_AMOUNT;
		} else if (shiftDown) {
			requestedAction = BookmarkHotkeyAction.CYCLE_PERMUTATION;
		} else if (altDown) {
			requestedAction = BookmarkHotkeyAction.TOGGLE_INPUT_CATALYST;
		}
		if (requestedAction == null || !isSupported(availableActions, requestedAction)) {
			return Optional.empty();
		}
		return Optional.of(requestedAction);
	}

	public static Optional<BookmarkHotkeyAction> resolveIngredientKeyboardAction(
		BookmarkHotkeyContext context,
		KeyboardKey key,
		boolean controlDown
	) {
		if (!controlDown || !context.hasIngredient()) {
			return Optional.empty();
		}
		List<BookmarkHotkeyAvailability> availableActions = BookmarkHotkeyPlanner.plan(context);
		BookmarkHotkeyAction requestedAction = switch (key) {
			case C -> BookmarkHotkeyAction.COPY_NAME;
			case D -> BookmarkHotkeyAction.COPY_OREDICT;
			case X -> BookmarkHotkeyAction.COPY_ID;
		};
		if (!isSupported(availableActions, requestedAction)) {
			return Optional.empty();
		}
		return Optional.of(requestedAction);
	}

	public static Optional<BookmarkHotkeyAction> resolveGroupMouseAction(
		BookmarkHotkeyContext context,
		BookmarkHotkeyMouseButton mouseButton,
		boolean shiftDown,
		boolean altDown
	) {
		List<BookmarkHotkeyAvailability> availableActions = BookmarkHotkeyPlanner.plan(context);
		BookmarkHotkeyAction requestedAction = switch (context.subject()) {
			case GROUP -> resolveGroupedMouseAction(mouseButton, shiftDown, altDown);
			case DEFAULT_GROUP_CONTROL -> resolveDefaultGroupControlMouseAction(mouseButton, shiftDown, altDown);
			case EMPTY_GROUP_PANEL -> resolveEmptyGroupPanelMouseAction(mouseButton, shiftDown, altDown);
			case INGREDIENT, ITEM_BOOKMARK, RECIPE_BOOKMARK -> null;
		};
		if (requestedAction == null || !isSupported(availableActions, requestedAction)) {
			return Optional.empty();
		}
		return Optional.of(requestedAction);
	}

	private static BookmarkHotkeyAction resolveIngredientBookmarkAction(boolean shiftDown, boolean controlDown) {
		if (shiftDown && controlDown) {
			return BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK_WITH_COUNT;
		}
		if (shiftDown) {
			return BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK;
		}
		if (controlDown) {
			return BookmarkHotkeyAction.ADD_BOOKMARK_WITH_COUNT;
		}
		return BookmarkHotkeyAction.ADD_BOOKMARK;
	}

	private static BookmarkHotkeyAction resolveGroupedMouseAction(
		BookmarkHotkeyMouseButton mouseButton,
		boolean shiftDown,
		boolean altDown
	) {
		if (altDown) {
			return switch (mouseButton) {
				case LEFT -> BookmarkHotkeyAction.GROUP_TOGGLE_COLLAPSED;
				case RIGHT -> null;
			};
		}
		return switch (mouseButton) {
			case LEFT -> shiftDown ? BookmarkHotkeyAction.GROUP_MOVE_DRAG : BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE;
			case RIGHT -> shiftDown ? null : BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING;
		};
	}

	private static BookmarkHotkeyAction resolveEmptyGroupPanelMouseAction(
		BookmarkHotkeyMouseButton mouseButton,
		boolean shiftDown,
		boolean altDown
	) {
		if (mouseButton == BookmarkHotkeyMouseButton.LEFT && !shiftDown && !altDown) {
			return BookmarkHotkeyAction.GROUP_CREATE_OR_INCLUDE_DRAG;
		}
		return null;
	}

	private static BookmarkHotkeyAction resolveDefaultGroupControlMouseAction(
		BookmarkHotkeyMouseButton mouseButton,
		boolean shiftDown,
		boolean altDown
	) {
		if (shiftDown) {
			return null;
		}
		if (altDown) {
			return switch (mouseButton) {
				case LEFT -> BookmarkHotkeyAction.GROUP_TOGGLE_COLLAPSED;
				case RIGHT -> null;
			};
		}
		return switch (mouseButton) {
			case LEFT -> BookmarkHotkeyAction.GROUP_TOGGLE_VIEW_MODE;
			case RIGHT -> BookmarkHotkeyAction.GROUP_TOGGLE_CRAFTING;
		};
	}

	private static boolean isSupported(List<BookmarkHotkeyAvailability> actions, BookmarkHotkeyAction action) {
		return actions.stream()
			.anyMatch(availability -> availability.action() == action && availability.support() == BookmarkHotkeySupport.SUPPORTED);
	}
}
