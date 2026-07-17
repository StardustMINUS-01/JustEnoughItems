package mezz.jei.gui.bookmarks;

import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Nullable;

public enum BookmarkItemType {
	ITEM(null),
	RESULT(RecipeIngredientRole.OUTPUT),
	INGREDIENT(RecipeIngredientRole.INPUT),
	CATALYST(RecipeIngredientRole.INPUT);

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

	public boolean isCatalyst() {
		return this == CATALYST;
	}

	public boolean scalesWithMultiplier() {
		return this != CATALYST;
	}
}
