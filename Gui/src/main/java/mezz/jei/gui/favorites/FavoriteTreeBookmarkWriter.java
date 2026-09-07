package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.RecipeLayoutProjection;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the favorite recipe tree and writes it as a group of bookmarks,
 * ported from JEI 1.21.1
 * ({@code mezz.jei.gui.favorites.FavoriteTreeBookmarkWriter}).
 */
public final class FavoriteTreeBookmarkWriter {
	private final FavoriteTreeBuilder treeBuilder;
	private final RecipeLayoutResolver layoutResolver;
	private final BookmarkGroupWriter bookmarkGroupWriter;

	public FavoriteTreeBookmarkWriter(
		FavoriteTreeBuilder treeBuilder,
		RecipeLayoutResolver layoutResolver,
		BookmarkGroupWriter bookmarkGroupWriter
	) {
		this.treeBuilder = treeBuilder;
		this.layoutResolver = layoutResolver;
		this.bookmarkGroupWriter = bookmarkGroupWriter;
	}

	public Optional<Integer> save(FocusedRecipe root, int depth) {
		return save(root, depth, Optional.empty(), Map.of());
	}

	public Optional<Integer> save(
		FocusedRecipe root,
		int depth,
		Optional<BookmarkIngredientKey> selectedRootOutputKey,
		Map<Integer, BookmarkIngredientKey> selectedRootInputKeys
	) {
		RecipeLayoutBuildCache layoutCache = new RecipeLayoutBuildCache();
		FavoriteTreeBuilder.FavoriteTreeResult result = treeBuilder.build(
			root,
			depth,
			selectedRootInputKeys,
			layoutCache
		);
		if (result.recipes().isEmpty()) {
			return Optional.empty();
		}

		List<RecipeLayoutProjection> layouts = new ArrayList<>();
		for (FavoriteTreeBuilder.FavoriteTreeRecipe recipe : result.recipes()) {
			Optional<IRecipeLayoutDrawable<?>> layout = recipe.layout()
				.or(() -> layoutCache.get(recipe.recipe()))
				.or(() -> layoutResolver.resolve(recipe.recipe()));
			if (layout.isEmpty()) {
				if (recipe.recipe().equals(root)) {
					return Optional.empty();
				}
				continue;
			}
			Optional<BookmarkIngredientKey> selectedOutputKey = recipe.recipe().equals(root) ?
				selectedRootOutputKey :
				Optional.empty();
			Map<Integer, BookmarkIngredientKey> selectedInputKeys = selectedInputKeys(recipe);
			layouts.add(new RecipeLayoutProjection(layout.get(), selectedOutputKey, selectedInputKeys));
		}
		if (layouts.isEmpty()) {
			return Optional.empty();
		}
		return bookmarkGroupWriter.addGroup(List.copyOf(layouts), false);
	}

	private static Map<Integer, BookmarkIngredientKey> selectedInputKeys(FavoriteTreeBuilder.FavoriteTreeRecipe recipe) {
		Map<Integer, BookmarkIngredientKey> selectedInputKeys = new LinkedHashMap<>();
		for (FavoriteTreeBuilder.FavoriteTreeInput input : recipe.inputs()) {
			Optional<BookmarkIngredientKey> selectedKey = input.selectedFavoriteKey();
			if (selectedKey.isPresent()) {
				selectedInputKeys.put(input.inputSlotIndex(), selectedKey.get());
			}
		}
		return Map.copyOf(selectedInputKeys);
	}

	@FunctionalInterface
	public interface RecipeLayoutResolver {
		Optional<IRecipeLayoutDrawable<?>> resolve(FocusedRecipe recipe);
	}

	@FunctionalInterface
	public interface BookmarkGroupWriter {
		Optional<Integer> addGroup(List<RecipeLayoutProjection> recipeLayouts, boolean preserveAmount);
	}
}
