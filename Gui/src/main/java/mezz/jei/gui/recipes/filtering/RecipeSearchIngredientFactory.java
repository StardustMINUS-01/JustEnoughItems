package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.stream.Collectors;

public final class RecipeSearchIngredientFactory {
	private RecipeSearchIngredientFactory() {
	}

	public static <T> RecipeSearchIngredient create(
		ITypedIngredient<T> typedIngredient,
		IIngredientManager ingredientManager
	) {
		IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		T ingredient = typedIngredient.getIngredient();
		Set<String> tags = helper.getTagStream(ingredient)
			.map(ResourceLocation::toString)
			.collect(Collectors.toUnmodifiableSet());
		return new RecipeSearchIngredient(
			helper.getDisplayName(ingredient),
			helper.getResourceLocation(ingredient),
			helper.getDisplayModId(ingredient),
			tags
		);
	}
}
