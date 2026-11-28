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

import mezz.jei.common.recipes.IRecipeMaterialCacheInternal.RegisteredMaterials;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.util.Set;

/** Registered materials live with the manager; plugin-generated recipes use a weighted, bounded cache. */
final class RecipeMaterialCache {
	private final Map<RecipeKey, Materials> registered = new ConcurrentHashMap<>();
	private volatile long generation;
	private volatile long revision;
	private static final RecipeIngredientRole[] ROLES = RecipeIngredientRole.values();
	private static final Materials EMPTY = new Materials(role -> List.of(), null, 0);
	private final Cache<RecipeKey, Materials> dynamic = CacheBuilder.newBuilder()
		.maximumWeight(65_536)
		.weigher((RecipeKey key, Materials materials) -> {
			long weight = 1;
			for (RecipeIngredientRole role : ROLES)
				weight += materials.getIngredients(role).size();
			return (int) Math.min(Integer.MAX_VALUE, weight);
		})
		.build();

	public synchronized <T> void register(IRecipeCategory<T> category, T recipe, IIngredientSupplier supplier, Set<RecipeIngredientRole> roles) {
		generation++;
		RecipeKey key = new RecipeKey(category, recipe);
		registered.put(key, new Materials(supplier, null, mask(roles)));
		dynamic.invalidate(key);
		revision++;
	}

	public <T> IIngredientSupplier getIngredients(IRecipeCategory<T> category, T recipe, IIngredientManager ingredientManager, Set<RecipeIngredientRole> roles) {
		int requested = mask(roles);
		RecipeKey key = new RecipeKey(category, recipe);
		if (!category.isHandled(recipe)) {
			invalidate(key, false);
			return EMPTY;
		}
		long capturedGeneration = generation;
		Materials materials = registered.get(key);
		if (materials == null)
			materials = dynamic.getIfPresent(key);
		int known = materials == null ? 0 : materials.roles;
		if (materials != null && (known & requested) == requested)
			return materials.select(requested);
		Set<RecipeIngredientRole> extractionRoles = EnumSet.noneOf(RecipeIngredientRole.class);
		for (var role : ROLES)
			if ((requested & ~known & (1 << role.ordinal())) != 0)
				extractionRoles.add(role);
		var extraction = IngredientSupplierHelper.extractIngredients(recipe, category, ingredientManager, extractionRoles);
		var initial = materials;
		materials = new Materials(extraction.supplier(), initial, known | requested);
		if (!extraction.complete())
			return materials.select(requested);
		synchronized (this) {
			// A reload may finish while a worker is extracting; do not repopulate an invalidated cache.
			if (generation == capturedGeneration) {
				var current = registered.get(key);
				boolean isRegistered = current != null;
				if (!isRegistered)
					current = dynamic.getIfPresent(key);
				if (current != null && current != initial)
					materials = new Materials(materials, current, current.roles | materials.roles);
				if (isRegistered) {
					registered.put(key, materials);
					if (registered(category, recipe) != null)
						revision++;
				} else {
					dynamic.put(key, materials);
				}
			}
		}
		return materials.select(requested);
	}

	public <T> void invalidate(IRecipeCategory<T> category, T recipe) {
		invalidate(new RecipeKey(category, recipe), true);
	}

	private synchronized void invalidate(RecipeKey key, boolean explicit) {
		generation++;
		var old = registered.replace(key, EMPTY);
		boolean changed = explicit || old != null && old != EMPTY || dynamic.getIfPresent(key) != null;
		dynamic.invalidate(key);
		if (changed)
			revision++;
	}

	public synchronized void invalidateAll() {
		generation++;
		// Keep registered identities so lazy rebuilding does not turn registered recipes into evictable entries.
		registered.replaceAll((key, supplier) -> EMPTY);
		dynamic.invalidateAll();
		revision++;
	}

	public synchronized void clear() {
		generation++;
		registered.clear();
		dynamic.invalidateAll();
		revision++;
	}

	synchronized void categoryVisibilityChanged() {
		revision++;
	}

	long revision() {
		return revision;
	}

	@Nullable
	RegisteredMaterials registered(IRecipeCategory<?> category, Object recipe) {
		var materials = registered.get(new RecipeKey(category, recipe));
		int needed = (1 << RecipeIngredientRole.INPUT.ordinal()) | (1 << RecipeIngredientRole.OUTPUT.ordinal());
		return materials != null && (materials.roles & needed) == needed ? materials : null;
	}

	@Nullable
	IIngredientSupplier cachedDynamic(IRecipeCategory<?> category, Object recipe, Set<RecipeIngredientRole> roles) {
		var materials = dynamic.getIfPresent(new RecipeKey(category, recipe));
		int needed = mask(roles);
		return materials != null && (materials.roles & needed) == needed ? materials : null;
	}

	private static int mask(Set<RecipeIngredientRole> roles) {
		int bits = 0;
		for (var role : ROLES)
			if (roles.contains(role))
				bits |= 1 << role.ordinal();
		return bits;
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

	private static final class Materials implements RegisteredMaterials {
		private final int roles;
		private final int populated;
		private final int[] inputSlots, outputSlots;
		@Override
		public int[] wideSlots(RecipeIngredientRole role) {
			return role == RecipeIngredientRole.INPUT ? inputSlots : outputSlots;
		}
		private static int[] slots(IIngredientSupplier source, Materials previous, RecipeIngredientRole role) {
			if (previous != null && (previous.roles & (1 << role.ordinal())) != 0)
				return previous.wideSlots(role);
			return source instanceof RegisteredMaterials slots ? slots.wideSlots(role) : IntArrays.EMPTY_ARRAY;
		}
		private final List<ITypedIngredient<?>>[] byRole;
		private volatile @Nullable Selection selections;

		@SuppressWarnings("unchecked")
		Materials(IIngredientSupplier source, @Nullable Materials previous, int roles) {
			this.roles = roles;
			inputSlots = slots(source, previous, RecipeIngredientRole.INPUT);
			outputSlots = slots(source, previous, RecipeIngredientRole.OUTPUT);
			byRole = new List[ROLES.length];
			int populated = 0;
			for (var role : ROLES) {
				int bit = 1 << role.ordinal();
				var list = previous != null && (previous.roles & bit) != 0 ? previous.getIngredients(role) : List.copyOf(source.getIngredients(role));
				byRole[role.ordinal()] = list;
				if (!list.isEmpty())
					populated |= bit;
			}
			this.populated = populated;
		}

		@Override
		public List<ITypedIngredient<?>> getIngredients(RecipeIngredientRole role) {
			return byRole[role.ordinal()];
		}

		IIngredientSupplier select(int requested) {
			int bits = requested & populated;
			if (bits == populated)
				return this;
			if (bits == 0)
				return EMPTY;
			var head = selections;
			for (var view = head; view != null; view = view.next)
				if (view.mask == bits)
					return view;
			return selections = new Selection(this, bits, head);
		}
	}

	private record Selection(Materials materials, int mask, @Nullable Selection next) implements IIngredientSupplier {
		@Override
		public List<ITypedIngredient<?>> getIngredients(RecipeIngredientRole role) {
			return (mask & (1 << role.ordinal())) == 0 ? List.of() : materials.getIngredients(role);
		}
	}
}
