package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FavoriteRecipeSlotResolver {
	private FavoriteRecipeSlotResolver() {
	}

	public static ResolvedRecipeIngredients resolve(
		IRecipeLayoutDrawable<?> layout,
		ITypedIngredient<?> targetFallback,
		IIngredientManager ingredientManager,
		Map<Integer, BookmarkIngredientKey> selectedInputs
	) {
		return new ResolvedRecipeIngredients(
			resolveTarget(layout, targetFallback),
			resolveIngredients(layout, RecipeIngredientRole.INPUT, ingredientManager, selectedInputs)
		);
	}

	private static Optional<ITypedIngredient<?>> resolveTarget(IRecipeLayoutDrawable<?> layout, ITypedIngredient<?> fallback) {
		List<ITypedIngredient<?>> outputs = resolveIngredients(layout, RecipeIngredientRole.OUTPUT, null, Map.of());
		if (outputs.size() == 1) {
			return Optional.of(outputs.get(0));
		}
		return outputs.stream()
			.filter(output -> output.getType().equals(fallback.getType()))
			.findFirst();
	}

	private static List<ITypedIngredient<?>> resolveIngredients(
		IRecipeLayoutDrawable<?> layout,
		RecipeIngredientRole role,
		IIngredientManager ingredientManager,
		Map<Integer, BookmarkIngredientKey> selectedInputs
	) {
		final int[] index = {0};
		return layout.getRecipeSlotsView()
			.getSlotViews(role)
			.stream()
			.map(slot -> resolveSlotIngredient(slot, index[0]++, ingredientManager, selectedInputs))
			.flatMap(Optional::stream)
			.toList();
	}

	private static Optional<ITypedIngredient<?>> resolveSlotIngredient(
		IRecipeSlotView slot,
		int slotIndex,
		IIngredientManager ingredientManager,
		Map<Integer, BookmarkIngredientKey> selectedInputs
	) {
		BookmarkIngredientKey selectedKey = selectedInputs.get(slotIndex);
		if (selectedKey != null && ingredientManager != null) {
			Optional<ITypedIngredient<?>> selected = slot.getAllIngredients()
				.filter(ingredient -> selectedKey.equals(
					BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)
				))
				.findFirst();
			if (selected.isPresent()) {
				return selected;
			}
		}
		return resolveFirstIngredient(slot);
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
