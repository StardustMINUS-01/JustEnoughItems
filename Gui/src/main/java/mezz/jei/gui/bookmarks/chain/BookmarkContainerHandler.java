/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;

import java.util.Map;

public interface BookmarkContainerHandler {
	Map<BookmarkIngredientKey, Long> getStorageAmounts();

	void pullBookmarkItemsFromContainer(BookmarkPullPlanner.BookmarkPullPlan plan);
}
