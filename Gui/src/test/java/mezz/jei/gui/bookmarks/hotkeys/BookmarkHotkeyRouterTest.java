package mezz.jei.gui.bookmarks.hotkeys;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class BookmarkHotkeyRouterTest {
	@Test
	public void altScrollResolvesToggleInputCatalyst() {
		BookmarkHotkeyContext context = recipeBookmarkContext();

		Assertions.assertEquals(
			Optional.of(BookmarkHotkeyAction.TOGGLE_INPUT_NONCONSUMABLE),
			BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, false, true, false)
		);
	}

	@Test
	public void existingScrollCombinationsStayIntact() {
		BookmarkHotkeyContext context = recipeBookmarkContext();

		Assertions.assertEquals(
			Optional.of(BookmarkHotkeyAction.SHIFT_AMOUNT),
			BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, true, false, false)
		);
		Assertions.assertEquals(
			Optional.of(BookmarkHotkeyAction.SHIFT_AMOUNT_STEP),
			BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, true, true, false)
		);
		Assertions.assertEquals(
			Optional.of(BookmarkHotkeyAction.CYCLE_PERMUTATION),
			BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, false, false, true)
		);
	}

	@Test
	public void ctrlAltScrollDoesNotResolveToggle() {
		BookmarkHotkeyContext context = recipeBookmarkContext();

		Assertions.assertEquals(
			Optional.of(BookmarkHotkeyAction.SHIFT_AMOUNT_STEP),
			BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, true, true, false)
		);
		Assertions.assertNotEquals(
			Optional.of(BookmarkHotkeyAction.TOGGLE_INPUT_NONCONSUMABLE),
			BookmarkHotkeyRouter.resolveBookmarkScrollAction(context, true, true, false)
		);
	}

	private static BookmarkHotkeyContext recipeBookmarkContext() {
		return BookmarkHotkeyContext.builder(BookmarkHotkeySubject.RECIPE_BOOKMARK)
			.hasIngredient(true)
			.hasRecipe(true)
			.isBookmarkSlot(true)
			.build();
	}
}
