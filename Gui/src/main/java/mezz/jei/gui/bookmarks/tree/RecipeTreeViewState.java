package mezz.jei.gui.bookmarks.tree;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Session-only values: never retain screens, ingredients, inventories or recipe layouts. */
public record RecipeTreeViewState(
	double zoom, double centerX, double centerY,
	boolean bookmarksVisible, boolean summaryVisible, boolean demandVisible, boolean remainingView,
	String search, int sidebarScroll, int bookmarkRow, Expansion expansion
) {
	public record Expansion(List<Branch> branches, int selected) {
		public Expansion { branches = List.copyOf(branches); }
	}

	public record Branch(int parent, int child, @Nullable ResourceLocation recipeType, @Nullable ResourceLocation recipeUid,
		boolean recipe, boolean expanded) {}
}
