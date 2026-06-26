package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkList;
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
	private final Runnable beforeSave;

	public FavoriteTreeBookmarkWriter(
		FavoriteTreeBuilder treeBuilder,
		RecipeLayoutResolver layoutResolver,
		BookmarkList bookmarks
	) {
		this(treeBuilder, layoutResolver, bookmarks::addRecipeLayoutProjectionBookmarkGroup, () -> {});
	}

	public FavoriteTreeBookmarkWriter(
		FavoriteTreeBuilder treeBuilder,
		RecipeLayoutResolver layoutResolver,
		BookmarkGroupWriter bookmarkGroupWriter
	) {
		this(treeBuilder, layoutResolver, bookmarkGroupWriter, () -> {});
	}

	public FavoriteTreeBookmarkWriter(
		FavoriteTreeBuilder treeBuilder,
		RecipeLayoutResolver layoutResolver,
		BookmarkGroupWriter bookmarkGroupWriter,
		Runnable beforeSave
	) {
		this.treeBuilder = treeBuilder;
		this.layoutResolver = layoutResolver;
		this.bookmarkGroupWriter = bookmarkGroupWriter;
		this.beforeSave = beforeSave;
	}

	public Optional<String> save(FocusedRecipe root, int depth) {
		return save(root, depth, Optional.empty());
	}

	public Optional<String> save(FocusedRecipe root, int depth, Optional<BookmarkIngredientKey> selectedRootOutputKey) {
		beforeSave.run();
		FavoriteTreeBuilder.FavoriteTreeResult result = treeBuilder.build(root, depth);
		if (result.recipes().isEmpty()) {
			return Optional.empty();
		}

		List<RecipeLayoutProjection> layouts = new ArrayList<>();
		boolean rootProjection = true;
		for (FavoriteTreeBuilder.FavoriteTreeRecipe recipe : result.recipes()) {
			Optional<IRecipeLayoutDrawable<?>> layout = layoutResolver.resolve(recipe.recipe());
			if (layout.isEmpty()) {
				if (recipe.recipe().equals(root)) {
					return Optional.empty();
				}
				continue;
			}
			Optional<BookmarkIngredientKey> selectedOutputKey = rootProjection ? selectedRootOutputKey : Optional.empty();
			layouts.add(new RecipeLayoutProjection(layout.get(), selectedOutputKey, selectedInputKeys(recipe)));
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

	public record RecipeLayoutProjection(
		IRecipeLayoutDrawable<?> layout,
		Optional<BookmarkIngredientKey> selectedOutputKey,
		Map<Integer, BookmarkIngredientKey> selectedInputKeys
	) {
		public RecipeLayoutProjection {
			selectedOutputKey = selectedOutputKey == null ? Optional.empty() : selectedOutputKey;
			selectedInputKeys = selectedInputKeys == null ? Map.of() : Map.copyOf(selectedInputKeys);
		}

		public RecipeLayoutProjection(
			IRecipeLayoutDrawable<?> layout,
			Map<Integer, BookmarkIngredientKey> selectedInputKeys
		) {
			this(layout, Optional.empty(), selectedInputKeys);
		}

		public Optional<BookmarkIngredientKey> selectedInputKey(int inputSlotIndex) {
			return Optional.ofNullable(selectedInputKeys.get(inputSlotIndex));
		}
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
