/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mirrors GTNH NEI's NEIRecipeWidget.scrollPermutations(...) using JEI display overrides.
 */
public final class RecipeLayoutPermutationSelector {
	private RecipeLayoutPermutationSelector() {
	}

	public static boolean scrollPermutation(
		IRecipeLayoutDrawable<?> recipeLayout,
		double mouseX,
		double mouseY,
		double scrollDelta,
		boolean hasShift
	) {
		if (!hasShift || scrollDelta == 0 || !recipeLayout.isMouseOver(mouseX, mouseY)) {
			return false;
		}

		Optional<RecipeSlotUnderMouse> hoveredSlot = recipeLayout.getSlotUnderMouse(mouseX, mouseY);
		if (hoveredSlot.isEmpty()) {
			return false;
		}

		IRecipeSlotDrawable hovered = hoveredSlot.get().slot();
		if (!isInputOrCatalyst(hovered)) {
			return false;
		}

		List<ITypedIngredient<?>> permutations = hovered.getAllIngredients().toList();
		if (permutations.size() <= 1) {
			return false;
		}

		Optional<ITypedIngredient<?>> displayed = hovered.getDisplayedIngredient();
		if (displayed.isEmpty()) {
			return false;
		}

		int currentIndex = indexOf(permutations, displayed.get());
		if (currentIndex < 0) {
			return false;
		}

		int direction = (int) Math.signum(scrollDelta);
		int nextIndex = Math.floorMod(currentIndex - direction, permutations.size());
		ITypedIngredient<?> selected = permutations.get(nextIndex);

		return applySelectedPermutation(recipeLayout, selected);
	}

	private static boolean applySelectedPermutation(IRecipeLayoutDrawable<?> recipeLayout, ITypedIngredient<?> selected) {
		boolean applied = false;
		for (IRecipeSlotView slotView : recipeLayout.getRecipeSlotsView().getSlotViews()) {
			if (!(slotView instanceof IRecipeSlotDrawable slot) || !isInputOrCatalyst(slot)) {
				continue;
			}
			boolean containsSelected = slot.getAllIngredients()
				.anyMatch(ingredient -> isSameIngredient(ingredient, selected));
			if (containsSelected) {
				slot.createDisplayOverrides()
					.addTypedIngredient(selected);
				applied = true;
			}
		}
		return applied;
	}

	private static boolean isInputOrCatalyst(IRecipeSlotView slot) {
		RecipeIngredientRole role = slot.getRole();
		return role == RecipeIngredientRole.INPUT || role == RecipeIngredientRole.CATALYST;
	}

	private static int indexOf(List<ITypedIngredient<?>> ingredients, ITypedIngredient<?> selected) {
		for (int i = 0; i < ingredients.size(); i++) {
			if (isSameIngredient(ingredients.get(i), selected)) {
				return i;
			}
		}
		return -1;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean isSameIngredient(ITypedIngredient<?> first, ITypedIngredient<?> second) {
		if (!first.getType().equals(second.getType())) {
			return false;
		}

		Object firstIngredient = first.getIngredient();
		Object secondIngredient = second.getIngredient();
		try {
			IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
			IIngredientType ingredientType = first.getType();
			IIngredientHelper ingredientHelper = ingredientManager.getIngredientHelper(ingredientType);
			String firstUid = ingredientHelper.getUniqueId(firstIngredient, UidContext.Ingredient);
			String secondUid = ingredientHelper.getUniqueId(secondIngredient, UidContext.Ingredient);
			return firstUid.equals(secondUid);
		} catch (RuntimeException ignored) {
			return Objects.equals(firstIngredient, secondIngredient);
		}
	}
}
