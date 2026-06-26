package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.List;
import java.util.Optional;

public final class FavoriteRecipeSlotResolver {
	private FavoriteRecipeSlotResolver() {
	}

	public static ResolvedRecipeIngredients resolve(IRecipeLayoutDrawable<?> layout, ITypedIngredient<?> targetFallback) {
		return new ResolvedRecipeIngredients(
			resolveTarget(layout, targetFallback),
			resolveIngredients(layout, RecipeIngredientRole.INPUT)
		);
	}

	private static Optional<ITypedIngredient<?>> resolveTarget(IRecipeLayoutDrawable<?> layout, ITypedIngredient<?> fallback) {
		List<ITypedIngredient<?>> outputs = resolveIngredients(layout, RecipeIngredientRole.OUTPUT);
		if (outputs.size() == 1) {
			return Optional.of(outputs.get(0));
		}
		return outputs.stream()
			.filter(output -> output.getType().equals(fallback.getType()))
			.findFirst();
	}

	private static List<ITypedIngredient<?>> resolveIngredients(IRecipeLayoutDrawable<?> layout, RecipeIngredientRole role) {
		return layout.getRecipeSlotsView()
			.getSlotViews(role)
			.stream()
			.map(FavoriteRecipeSlotResolver::resolveFirstIngredient)
			.flatMap(Optional::stream)
			.toList();
	}

	private static Optional<ITypedIngredient<?>> resolveFirstIngredient(IRecipeSlotView slot) {
		return slot.getAllIngredients()
			.findFirst();
	}

	public record ResolvedRecipeIngredients(Optional<ITypedIngredient<?>> target, List<ITypedIngredient<?>> inputs) {
		public ResolvedRecipeIngredients {
			target = target == null ? Optional.empty() : target;
			inputs = inputs == null ? List.of() : inputs;
		}
	}
}
