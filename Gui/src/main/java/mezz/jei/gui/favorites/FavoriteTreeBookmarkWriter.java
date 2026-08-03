package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeLayoutProjection;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FavoriteTreeBookmarkWriter {
	public static final int DEFAULT_DEPTH = 9;

	private final FavoriteTreeBuilder treeBuilder;
	private final RecipeLayoutResolver layoutResolver;
	private final BookmarkGroupWriter bookmarkGroupWriter;

	public FavoriteTreeBookmarkWriter(
		FavoriteTreeBuilder treeBuilder,
		RecipeLayoutResolver layoutResolver,
		BookmarkList bookmarks
	) {
		this(treeBuilder, layoutResolver, bookmarks::addRecipeLayoutProjectionBookmarkGroup);
	}

	public FavoriteTreeBookmarkWriter(
		FavoriteTreeBuilder treeBuilder,
		RecipeLayoutResolver layoutResolver,
		BookmarkGroupWriter bookmarkGroupWriter
	) {
		this.treeBuilder = treeBuilder;
		this.layoutResolver = layoutResolver;
		this.bookmarkGroupWriter = bookmarkGroupWriter;
	}

	public Optional<String> save(FocusedRecipe root, int depth) {
		return save(root, depth, Optional.empty());
	}

	public Optional<String> save(FocusedRecipe root, int depth, Optional<BookmarkIngredientKey> selectedRootOutputKey) {
		return save(root, depth, selectedRootOutputKey, Map.of());
	}

	public Optional<String> save(
		FocusedRecipe root,
		int depth,
		Optional<BookmarkIngredientKey> selectedRootOutputKey,
		Map<Integer, BookmarkIngredientKey> selectedRootInputKeys
	) {
		FavoriteTreeBuilder.FavoriteTreeResult result = treeBuilder.build(root, depth);
		if (result.recipes().isEmpty()) {
			return Optional.empty();
		}

		List<RecipeLayoutProjection> layouts = new ArrayList<>();
		boolean rootProjection = true;
		for (FavoriteTreeBuilder.FavoriteTreeRecipe recipe : result.recipes()) {
			// Reuse the layout already built while expanding the tree; fall back to a fresh
			// resolve only for recipes that were built without a layout (tests or custom resolvers).
			Optional<IRecipeLayoutDrawable<?>> layout = recipe.layout()
				.or(() -> layoutResolver.resolve(recipe.recipe()));
			if (layout.isEmpty()) {
				if (recipe.recipe().equals(root)) {
					return Optional.empty();
				}
				continue;
			}
			Optional<BookmarkIngredientKey> selectedOutputKey = rootProjection ? selectedRootOutputKey : Optional.empty();
			Map<Integer, BookmarkIngredientKey> selectedInputKeys = selectedInputKeys(recipe);
			if (rootProjection && !selectedRootInputKeys.isEmpty()) {
				Map<Integer, BookmarkIngredientKey> mergedInputKeys = new LinkedHashMap<>(selectedInputKeys);
				mergedInputKeys.putAll(selectedRootInputKeys);
				selectedInputKeys = Map.copyOf(mergedInputKeys);
			}
			layouts.add(new RecipeLayoutProjection(layout.get(), selectedOutputKey, selectedInputKeys));
			rootProjection = false;
		}
		if (layouts.isEmpty()) {
			return Optional.empty();
		}
		return bookmarkGroupWriter.addGroup(List.copyOf(layouts), false);
	}

	private static Map<Integer, BookmarkIngredientKey> selectedInputKeys(FavoriteTreeBuilder.FavoriteTreeRecipe recipe) {
		Map<Integer, BookmarkIngredientKey> selectedInputKeys = new LinkedHashMap<>();
		List<FavoriteTreeBuilder.FavoriteTreeInput> inputs = recipe.inputs();
		for (int i = 0; i < inputs.size(); i++) {
			Optional<BookmarkIngredientKey> selectedKey = inputs.get(i).selectedFavoriteKey();
			if (selectedKey.isPresent()) {
				selectedInputKeys.put(i, selectedKey.get());
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
		Optional<String> addGroup(List<RecipeLayoutProjection> recipeLayouts, boolean preserveAmount);
	}
}
