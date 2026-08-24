package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkPermutationTooltipState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class BookmarkPermutationTooltipStateTest {
	@Test
	public void keepsTheSlidingWindowWhenTheBookmarkIsReplacedAtTheSameIndex() {
		BookmarkPermutationTooltipState state = new BookmarkPermutationTooltipState();
		List<BookmarkIngredientKey> candidates = candidates(31);

		Assertions.assertEquals(1, state.updateStart(4, candidates, 29));
		Assertions.assertEquals(2, state.updateStart(4, candidates, 30));
	}

	@Test
	public void resetsTheSlidingWindowForAnotherBookmark() {
		BookmarkPermutationTooltipState state = new BookmarkPermutationTooltipState();
		List<BookmarkIngredientKey> candidates = candidates(31);
		Assertions.assertEquals(2, state.updateStart(4, candidates, 30));

		Assertions.assertEquals(0, state.updateStart(5, candidates, 0));
	}

	private static List<BookmarkIngredientKey> candidates(int count) {
		List<BookmarkIngredientKey> candidates = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			candidates.add(BookmarkIngredientKey.of("test", "candidate-" + index));
		}
		return candidates;
	}
}
