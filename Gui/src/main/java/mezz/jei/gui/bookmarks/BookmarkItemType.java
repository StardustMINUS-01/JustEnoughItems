/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks;

import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Nullable;

public enum BookmarkItemType {
	ITEM(null),
	RESULT(RecipeIngredientRole.OUTPUT),
	INGREDIENT(RecipeIngredientRole.INPUT),
	NONCONSUMABLE(RecipeIngredientRole.INPUT);

	private final @Nullable RecipeIngredientRole recipeRole;

	BookmarkItemType(@Nullable RecipeIngredientRole recipeRole) {
		this.recipeRole = recipeRole;
	}

	public static BookmarkItemType fromRecipeRole(RecipeIngredientRole role) {
		return switch (role) {
			case INPUT -> INGREDIENT;
			case OUTPUT -> RESULT;
			default -> ITEM;
		};
	}

	public boolean isRecipeAssociated() {
		return recipeRole != null;
	}

	public @Nullable RecipeIngredientRole recipeRole() {
		return recipeRole;
	}

	public boolean isGraphInput() {
		return this == INGREDIENT;
	}

	public boolean isGraphOutput() {
		return this == RESULT;
	}

	public boolean isGraphMember() {
		return isGraphInput() || isGraphOutput();
	}

	public boolean isNonConsumable() {
		return this == NONCONSUMABLE;
	}

	public boolean scalesWithMultiplier() {
		return this != NONCONSUMABLE;
	}
}
