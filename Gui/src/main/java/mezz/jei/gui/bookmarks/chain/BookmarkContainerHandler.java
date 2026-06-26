package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;

import java.util.Map;

public interface BookmarkContainerHandler {
	Map<BookmarkIngredientKey, Long> getStorageAmounts();

	void pullBookmarkItemsFromContainer(BookmarkPullPlanner.BookmarkPullPlan plan);
}
