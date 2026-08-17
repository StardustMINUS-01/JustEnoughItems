package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.input.FocusedRecipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reuses recipe layouts within a single recipe tree build so the same recipe
 * is not laid out twice (once for candidate matching and once for the tree node).
 * The cache is per-build and is never persisted between builds.
 */
public final class RecipeLayoutBuildCache {
	private final Map<FocusedRecipe, IRecipeLayoutDrawable<?>> layouts = new LinkedHashMap<>();

	public Optional<IRecipeLayoutDrawable<?>> get(FocusedRecipe focusedRecipe) {
		return Optional.ofNullable(layouts.get(focusedRecipe));
	}

	public void put(FocusedRecipe focusedRecipe, IRecipeLayoutDrawable<?> layout) {
		layouts.put(focusedRecipe, layout);
	}

	public Optional<IRecipeLayoutDrawable<?>> getOrBuild(
		FocusedRecipe focusedRecipe,
		Supplier<Optional<IRecipeLayoutDrawable<?>>> layoutSupplier
	) {
		Optional<IRecipeLayoutDrawable<?>> cached = get(focusedRecipe);
		if (cached.isPresent()) {
			return cached;
		}
		Optional<IRecipeLayoutDrawable<?>> built = layoutSupplier.get();
		built.ifPresent(layout -> put(focusedRecipe, layout));
		return built;
	}
}
