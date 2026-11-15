package mezz.jei.library.recipes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.util.IngredientSupplierHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registered materials live with the manager; plugin-generated recipes use a weighted, bounded cache. */
final class RecipeMaterialCache {
	private final Map<RecipeKey, Materials> registered = new HashMap<>();
	private long generation;
	private final Cache<RecipeKey, Materials> dynamic = CacheBuilder.newBuilder()
		.maximumWeight(65_536)
		.weigher((RecipeKey key, Materials materials) -> {
			long weight = 1;
			for (RecipeIngredientRole role : RecipeIngredientRole.values())
				weight += materials.supplier().getIngredients(role).size();
			return (int) Math.min(Integer.MAX_VALUE, weight);
		})
		.build();

	public synchronized <T> void register(IRecipeCategory<T> category, T recipe, IIngredientSupplier supplier, Set<RecipeIngredientRole> roles) {
		generation++;
		RecipeKey key = new RecipeKey(category, recipe);
		registered.put(key, new Materials(snapshot(supplier, null), Set.copyOf(roles)));
		dynamic.invalidate(key);
	}

	public <T> IIngredientSupplier getIngredients(IRecipeCategory<T> category, T recipe, IIngredientManager ingredientManager, Set<RecipeIngredientRole> roles) {
		roles = Set.copyOf(roles);
		RecipeKey key = new RecipeKey(category, recipe);
		if (!category.isHandled(recipe)) {
			invalidate(category, recipe);
			return role -> List.of();
		}
		Materials materials;
		long capturedGeneration;
		synchronized (this) {
			capturedGeneration = generation;
			materials = registered.get(key);
			if (materials == null)
				materials = dynamic.getIfPresent(key);
			if (materials != null && materials.roles().containsAll(roles))
				return materials.select(roles);
		}
		Set<RecipeIngredientRole> extractionRoles = EnumSet.noneOf(RecipeIngredientRole.class);
		extractionRoles.addAll(roles);
		if (materials != null)
			extractionRoles.removeAll(materials.roles());
		IngredientSupplierHelper.Extraction extraction = IngredientSupplierHelper.extractIngredients(recipe, category, ingredientManager, extractionRoles);
		IIngredientSupplier supplier = snapshot(extraction.supplier(), materials);
		if (materials != null)
			extractionRoles.addAll(materials.roles());
		materials = new Materials(supplier, Set.copyOf(extractionRoles));
		if (!extraction.complete())
			return materials.select(roles);
		synchronized (this) {
			// A reload may finish while a worker is extracting; do not repopulate an invalidated cache.
			if (generation == capturedGeneration) {
				if (registered.containsKey(key)) {
					registered.put(key, materials);
				} else {
					dynamic.put(key, materials);
				}
			}
		}
		return materials.select(roles);
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

	private static IIngredientSupplier snapshot(IIngredientSupplier source, @Nullable Materials previous) {
		Map<RecipeIngredientRole, List<ITypedIngredient<?>>> materials = new EnumMap<>(RecipeIngredientRole.class);
		for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
			List<ITypedIngredient<?>> ingredients = previous != null && previous.roles().contains(role)
				? previous.supplier().getIngredients(role)
				: source.getIngredients(role);
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

	private record Materials(IIngredientSupplier supplier, Set<RecipeIngredientRole> roles) {
		public IIngredientSupplier select(Set<RecipeIngredientRole> selectedRoles) {
			if (roles.equals(selectedRoles))
				return supplier;
			return role -> selectedRoles.contains(role) ? supplier.getIngredients(role) : List.of();
		}
	}
}
