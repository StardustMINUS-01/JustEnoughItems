package mezz.jei.gui.bookmarks;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record BookmarkGroup(
	String id,
	String title,
	boolean newLine,
	boolean resultOnly,
	boolean craftingMode,
	Set<ResourceLocation> collapsedRecipeIds
) {
	public BookmarkGroup {
		collapsedRecipeIds = Set.copyOf(collapsedRecipeIds);
	}

	public BookmarkGroup(String id, String title) {
		this(id, title, false, false, false, Set.of());
	}

	public BookmarkGroup withNewLine(boolean newLine) {
		return new BookmarkGroup(id, title, newLine, resultOnly, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup withResultOnly(boolean resultOnly) {
		return new BookmarkGroup(id, title, newLine, resultOnly, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup withCraftingMode(boolean craftingMode) {
		return new BookmarkGroup(id, title, newLine, resultOnly, craftingMode, collapsedRecipeIds);
	}

	public BookmarkGroup withCollapsedRecipeIds(Set<ResourceLocation> collapsedRecipeIds) {
		return new BookmarkGroup(id, title, newLine, resultOnly, craftingMode, collapsedRecipeIds);
	}
}
