/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record BookmarkGroup(
	int id,
	String title,
	BookmarkViewMode viewMode,
	boolean collapsed,
	boolean craftingMode,
	Set<ResourceLocation> collapsedRecipeIds
) {
	public BookmarkGroup {
		collapsedRecipeIds = Set.copyOf(collapsedRecipeIds);
	}

	public BookmarkGroup(int id, String title) {
		this(id, title, BookmarkViewMode.DEFAULT, false, false, Set.of());
	}

	public BookmarkGroup withViewMode(BookmarkViewMode viewMode) {
		return new BookmarkGroup(id, title, viewMode, collapsed, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup toggleViewMode() {
		return withViewMode(viewMode == BookmarkViewMode.DEFAULT ? BookmarkViewMode.TODO_LIST : BookmarkViewMode.DEFAULT);
	}

	public BookmarkGroup toggleCollapsed() {
		return new BookmarkGroup(id, title, viewMode, !collapsed, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup withCraftingMode(boolean craftingMode) {
		return new BookmarkGroup(id, title, viewMode, collapsed, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup withCollapsedRecipeIds(Set<ResourceLocation> collapsedRecipeIds) {
		return new BookmarkGroup(id, title, viewMode, collapsed, craftingMode, collapsedRecipeIds);
	}
}
