package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.search.DynamicRecipeMaterialCache;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class RecipeSearchPage {
	private final ILookupState state;
	final List<IRecipeCategory<?>> categories;
	final IdentityHashMap<IRecipeCategory<?>, List<ITypedIngredient<?>>> catalysts = new IdentityHashMap<>();
	private final IdentityHashMap<IRecipeCategory<?>, Slot> slots = new IdentityHashMap<>();
	private final GlobalRecipeIndexCache global;
	final DynamicRecipeMaterialCache dynamicMaterials;

	private static final class Slot {
		final int order;
		IFocusedRecipes<?> focused;
		volatile PageRecipeIndex<?> projection;
		Slot(int order) { this.order = order; }
	}

	RecipeSearchPage(ILookupState state, RecipeFilterMode mode, GlobalRecipeIndexCache global) {
		this.state = state;
		this.global = global;
		dynamicMaterials = global == null ? null : new DynamicRecipeMaterialCache(global.orphans, 65_536);
		var preferences = RecipeCategoryPreferences.get();
		categories = state.getRecipeCategories().stream().filter(c -> preferences.allows(c.getRecipeType().getUid(), mode)).toList();
		for (var category : categories)
			slots.put(category, new Slot(slots.size()));
	}

	boolean allowsSameCategories(RecipeFilterMode mode) {
		var preferences = RecipeCategoryPreferences.get();
		for (var c : state.getRecipeCategories())
			if (preferences.allows(c.getRecipeType().getUid(), mode) != slots.containsKey(c))
				return false;
		return true;
	}

	int order(IRecipeCategory<?> category) { return slots.get(category).order; }

	IFocusedRecipes<?> capture(IRecipeCategory<?> category) {
		var slot = slots.get(category);
		if (slot.focused == null)
			slot.focused = freeze(state.getFocusedRecipes(category));
		return slot.focused;
	}

	private static <T> IFocusedRecipes<T> freeze(IFocusedRecipes<T> focused) {
		return new StaticFocusedRecipes<>(focused.getRecipeCategory(), List.copyOf(focused.getRecipes()));
	}

	boolean fullyIndexed() {
		for (var slot : slots.values()) {
			var projection = slot.projection;
			if (projection == null || projection.hasDynamic())
				return false;
		}
		return true;
	}

	List<IFocusedRecipes<?>> narrow(List<IFocusedRecipes<?>> source, List<IFocusedRecipes<?>> previous, Set<IRecipeCategory<?>> exact) {
		var byCategory = new IdentityHashMap<IRecipeCategory<?>, IFocusedRecipes<?>>();
		for (var entry : previous)
			byCategory.put(entry.getRecipeCategory(), entry);
		var result = new ArrayList<IFocusedRecipes<?>>();
		for (var focused : source) {
			var category = focused.getRecipeCategory();
			var projection = slots.get(category).projection;
			var entry = byCategory.get(category);
			if (exact != null && projection != null && (entry == null || projection.ownsRows(entry.getRecipes())))
				exact.add(category);
			if (projection == null)
				result.add(focused);
			else if (projection.hasDynamic())
				result.add(projection.includeDynamic(entry == null ? List.of() : entry.getRecipes()));
			else if (entry != null)
				result.add(projection.ownsRows(entry.getRecipes()) ? entry : focused);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	<T> PageRecipeIndex<T> index(IRecipeCategory<T> category, boolean build) {
		if (global == null)
			return null;
		var slot = slots.get(category);
		var projection = (PageRecipeIndex<T>) slot.projection;
		if (projection == null) {
			var index = build ? global.build(category, 0) : global.ready(category);
			if (index == null)
				return null;
			var rows = (List<T>) slot.focused.getRecipes();
			if (!build && rows != index.recipes && rows.size() > 2048)
				return null;
			slot.projection = projection = new PageRecipeIndex<>(index, rows);
		}
		return projection;
	}
}
