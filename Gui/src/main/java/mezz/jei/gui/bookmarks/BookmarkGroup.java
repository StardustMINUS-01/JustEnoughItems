package mezz.jei.gui.bookmarks;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record BookmarkGroup(
	String id,
	String title,
	BookmarkViewMode viewMode,
	BookmarkViewMode expandedViewMode,
	boolean craftingMode,
	Set<ResourceLocation> collapsedRecipeIds
) {
	public BookmarkGroup {
		viewMode = viewMode == null ? BookmarkViewMode.DEFAULT : viewMode;
		expandedViewMode = expandedViewMode == null ? viewMode : expandedViewMode;
		collapsedRecipeIds = Set.copyOf(collapsedRecipeIds);
	}

	public BookmarkGroup(String id, String title) {
		this(id, title, BookmarkViewMode.DEFAULT, BookmarkViewMode.DEFAULT, false, Set.of());
	}

	public BookmarkGroup withViewMode(BookmarkViewMode viewMode) {
		BookmarkViewMode effectiveViewMode = viewMode == null ? BookmarkViewMode.DEFAULT : viewMode;
		BookmarkViewMode expandedViewMode = effectiveViewMode == BookmarkViewMode.COLLAPSED ?
			this.expandedViewMode :
			effectiveViewMode;
		return new BookmarkGroup(id, title, effectiveViewMode, expandedViewMode, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup toggleViewMode() {
		BookmarkViewMode expandedViewMode = this.expandedViewMode == BookmarkViewMode.DEFAULT ?
			BookmarkViewMode.TODO_LIST :
			BookmarkViewMode.DEFAULT;
		if (viewMode == BookmarkViewMode.COLLAPSED) {
			return new BookmarkGroup(id, title, viewMode, expandedViewMode, craftingMode, collapsedRecipeIds);
		}
		return new BookmarkGroup(id, title, expandedViewMode, expandedViewMode, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup toggleCollapsed() {
		if (viewMode == BookmarkViewMode.COLLAPSED) {
			BookmarkViewMode expandedViewMode = this.expandedViewMode == BookmarkViewMode.COLLAPSED ?
				BookmarkViewMode.DEFAULT :
				this.expandedViewMode;
			return new BookmarkGroup(id, title, expandedViewMode, expandedViewMode, craftingMode, collapsedRecipeIds);
		}
		return new BookmarkGroup(id, title, BookmarkViewMode.COLLAPSED, viewMode, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup withCraftingMode(boolean craftingMode) {
		return new BookmarkGroup(id, title, viewMode, expandedViewMode, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup withCollapsedRecipeIds(Set<ResourceLocation> collapsedRecipeIds) {
		return new BookmarkGroup(id, title, viewMode, expandedViewMode, craftingMode, collapsedRecipeIds);
	}
}
