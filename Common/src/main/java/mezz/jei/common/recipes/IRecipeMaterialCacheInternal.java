package mezz.jei.common.recipes;

import java.util.List;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Runtime material lifecycle shared by the GUI reload handler and the recipe manager. */
public interface IRecipeMaterialCacheInternal {
	interface RegisteredMaterials extends IIngredientSupplier {
		/** A single zero covers the entire role. Other nonempty arrays are start/end pairs of wide slots. */
		int[] WHOLE_SLOT = {0};

		int[] wideSlots(RecipeIngredientRole role);
	}

	@Nullable
	IIngredientSupplier getCachedDynamicMaterials(IRecipeCategory<?> category, Object recipe, Set<RecipeIngredientRole> roles);

	List<IRecipeCategory<?>> getRegisteredCategories();

	<T> List<T> getVisibleRecipes(IRecipeCategory<T> category);

	int getRegisteredRecipeCount(IRecipeCategory<?> category);

	<T> List<T> getRegisteredRecipes(IRecipeCategory<T> category, int minimumSize);

	void invalidateRecipeMaterials();

	long getRecipeMaterialRevision();

	long getRecipeVisibilityRevision();

	@Nullable
	RegisteredMaterials getRegisteredRecipeMaterials(IRecipeCategory<?> category, Object recipe);
}
