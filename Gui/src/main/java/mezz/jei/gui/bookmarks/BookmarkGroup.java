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
		viewMode = viewMode == null ? BookmarkViewMode.DEFAULT : viewMode;
		collapsedRecipeIds = Set.copyOf(collapsedRecipeIds);
	}

	public BookmarkGroup(int id, String title) {
		this(id, title, BookmarkViewMode.DEFAULT, false, false, Set.of());
	}

	public BookmarkGroup withViewMode(BookmarkViewMode viewMode) {
		return new BookmarkGroup(id, title, viewMode, collapsed, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup toggleViewMode() {
		BookmarkViewMode next = viewMode == BookmarkViewMode.DEFAULT ?
			BookmarkViewMode.TODO_LIST :
			BookmarkViewMode.DEFAULT;
		return new BookmarkGroup(id, title, next, collapsed, craftingMode, collapsedRecipeIds);
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

	public boolean isCollapsed() {
		return collapsed;
	}
}
