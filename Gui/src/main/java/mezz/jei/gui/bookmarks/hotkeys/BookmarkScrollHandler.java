package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;

/** Shared bookmark edits; navigation is owned by the enclosing view. */
public final class BookmarkScrollHandler {
	private BookmarkScrollHandler() { }

	public static boolean apply(BookmarkList bookmarks, IBookmark bookmark, double delta,
		boolean control, boolean alt, boolean shift, long amountStep, java.util.function.LongPredicate shiftAmount) {
		if (delta == 0) {
			return false;
		}
		var metadata = bookmarks.getBookmarkMetadata(bookmark);
		var context = BookmarkHotkeyContext.builder(metadata.recipeUid() == null ? BookmarkHotkeySubject.ITEM_BOOKMARK : BookmarkHotkeySubject.RECIPE_BOOKMARK)
			.hasIngredient(true).hasRecipe(metadata.recipeUid() != null).isBookmarkSlot(true).build();
		var action = BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, control, alt, shift);
		if (action.isEmpty()) {
			return false;
		}
		long direction = delta > 0 ? 1 : -1;
		return switch (action.get()) {
			case SHIFT_AMOUNT -> shiftAmount.test(direction);
			case SHIFT_AMOUNT_STEP -> shiftAmount.test(direction * amountStep);
			case CYCLE_PERMUTATION -> bookmarks.cycleBookmarkPermutation(bookmark, direction);
			case TOGGLE_INPUT_NONCONSUMABLE -> bookmarks.toggleBookmarkInputCatalyst(bookmark);
			default -> false;
		};
	}
}
