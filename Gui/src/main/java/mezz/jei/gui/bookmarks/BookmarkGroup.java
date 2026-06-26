package mezz.jei.gui.bookmarks;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record BookmarkGroup(
	String id,
	String title,
	BookmarkViewMode viewMode,
	boolean craftingMode,
	boolean collapsed,
	Set<ResourceLocation> collapsedRecipeIds
) {
	public BookmarkGroup {
		collapsedRecipeIds = Set.copyOf(collapsedRecipeIds);
	}

	public BookmarkGroup(String id, String title, boolean collapsed) {
		this(id, title, BookmarkViewMode.DEFAULT, false, collapsed, Set.of());
	}

	public BookmarkGroup withCollapsed(boolean collapsed) {
		return new BookmarkGroup(id, title, viewMode, craftingMode, collapsed, collapsedRecipeIds);
	}

	public BookmarkGroup withViewMode(BookmarkViewMode viewMode) {
		return new BookmarkGroup(id, title, viewMode, craftingMode, collapsed, collapsedRecipeIds);
	}

	public BookmarkGroup withCraftingMode(boolean craftingMode) {
		return new BookmarkGroup(id, title, viewMode, craftingMode, collapsed, collapsedRecipeIds);
	}

	public BookmarkGroup withCollapsedRecipeIds(Set<ResourceLocation> collapsedRecipeIds) {
		return new BookmarkGroup(id, title, viewMode, craftingMode, collapsed, collapsedRecipeIds);
	}
}
