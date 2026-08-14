package mezz.jei.gui.bookmarks;

import java.util.List;

/**
 * A group of recipe-tree bookmark entries that share the same recipe,
 * mirroring the 1.21.1 bookmark group semantics
 * ({@code BookmarkList#addRecipeBookmarkEntryGroup}). The {@code title} is
 * derived from the recipe's primary output ingredient (or first input, or
 * {@code "Recipe"} as a fallback), matching 1.21.1
 * {@code getRecipeBookmarkGroupTitle}. Entries of the same group are written
 * contiguously so a single recipe is kept together.
 */
public record RecipeTreeBookmarkGroup(
	String title,
	List<RecipeTreeBookmarkEntry> entries
) {
	public RecipeTreeBookmarkGroup {
		entries = entries == null ? List.of() : List.copyOf(entries);
	}
}
