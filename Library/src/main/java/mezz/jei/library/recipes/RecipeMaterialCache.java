package mezz.jei.library.recipes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.util.IngredientSupplierHelper;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Registered materials live with the manager; plugin-generated recipes use a weighted, bounded cache. */
final class RecipeMaterialCache {
	private final Map<RecipeKey, IIngredientSupplier> registered = new HashMap<>();
	private long generation;
	private final Cache<RecipeKey, IIngredientSupplier> dynamic = CacheBuilder.newBuilder()
		.maximumWeight(65_536)
		.weigher((RecipeKey key, IIngredientSupplier supplier) -> {
			long weight = 1;
			for (RecipeIngredientRole role : RecipeIngredientRole.values())
				weight += supplier.getIngredients(role).size();
			return (int) Math.min(Integer.MAX_VALUE, weight);
		})
		.build();

	public synchronized <T> void register(IRecipeCategory<T> category, T recipe, IIngredientSupplier supplier) {
		generation++;
		RecipeKey key = new RecipeKey(category, recipe);
		registered.put(key, snapshot(supplier));
		dynamic.invalidate(key);
	}

	public <T> IIngredientSupplier getIngredients(IRecipeCategory<T> category, T recipe, IIngredientManager ingredientManager) {
		RecipeKey key = new RecipeKey(category, recipe);
		if (!category.isHandled(recipe)) {
			invalidate(category, recipe);
			return role -> List.of();
		}
		IIngredientSupplier supplier;
		long capturedGeneration;
		synchronized (this) {
			capturedGeneration = generation;
			supplier = registered.get(key);
			if (supplier == null)
				supplier = dynamic.getIfPresent(key);
			if (supplier != null)
				return supplier;
		}
		IngredientSupplierHelper.Extraction extraction = IngredientSupplierHelper.extractIngredients(recipe, category, ingredientManager);
		if (!extraction.complete())
			return extraction.supplier();
		supplier = snapshot(extraction.supplier());
		synchronized (this) {
			// A reload may finish while a worker is extracting; do not repopulate an invalidated cache.
			if (generation == capturedGeneration) {
				if (registered.containsKey(key)) {
					registered.put(key, supplier);
				} else {
					dynamic.put(key, supplier);
				}
			}
		}
		return supplier;
	}

	public synchronized <T> void invalidate(IRecipeCategory<T> category, T recipe) {
		generation++;
		RecipeKey key = new RecipeKey(category, recipe);
		if (registered.containsKey(key))
			registered.put(key, null);
		dynamic.invalidate(key);
	}

	public synchronized void invalidateAll() {
		generation++;
		// Keep registered identities so lazy rebuilding does not turn registered recipes into evictable entries.
		registered.replaceAll((key, supplier) -> null);
		dynamic.invalidateAll();
	}

	public synchronized void clear() {
		generation++;
		registered.clear();
		dynamic.invalidateAll();
	}

	private static IIngredientSupplier snapshot(IIngredientSupplier source) {
		Map<RecipeIngredientRole, List<ITypedIngredient<?>>> materials = new EnumMap<>(RecipeIngredientRole.class);
		for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
			List<ITypedIngredient<?>> ingredients = source.getIngredients(role);
			if (!ingredients.isEmpty())
				materials.put(role, List.copyOf(ingredients));
		}
		return role -> materials.getOrDefault(role, List.of());
	}

	private record RecipeKey(IRecipeCategory<?> category, Object recipe) {
		@Override
		public boolean equals(Object other) {
			return other instanceof RecipeKey key && category == key.category && recipe == key.recipe;
		}

		@Override
		public int hashCode() {
			return 31 * System.identityHashCode(category) + System.identityHashCode(recipe);
		}
	}
}
