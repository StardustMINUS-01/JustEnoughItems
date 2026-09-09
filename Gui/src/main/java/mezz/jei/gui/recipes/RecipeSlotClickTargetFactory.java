package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IMouseOverable;
import mezz.jei.gui.overlay.elements.IngredientElement;

import java.util.Optional;

public final class RecipeSlotClickTargetFactory {
	public RecipeSlotClickTargetFactory() {
	}

	public Optional<IClickableIngredientInternal<?>> create(
		IRecipeLayoutDrawable<?> recipeLayout,
		double mouseX,
		double mouseY
	) {
		return recipeLayout.getSlotUnderMouse(mouseX, mouseY)
			.flatMap(slotUnderMouse -> create(
				slotUnderMouse,
				createMouseOverable(recipeLayout, slotUnderMouse)
			));
	}

	Optional<IClickableIngredientInternal<?>> create(
		RecipeSlotUnderMouse slotUnderMouse,
		IMouseOverable mouseOverable
	) {
		return slotUnderMouse.slot()
			.getDisplayedIngredient()
			.map(ingredient -> create(slotUnderMouse.slot(), ingredient, mouseOverable));
	}

	private <T> IClickableIngredientInternal<T> create(
		IRecipeSlotView slot,
		ITypedIngredient<T> ingredient,
		IMouseOverable mouseOverable
	) {
		return new ClickableIngredientInternal<>(new IngredientElement<>(ingredient), mouseOverable, false, true);
	}

	static IMouseOverable createMouseOverable(
		IRecipeLayoutDrawable<?> recipeLayout,
		RecipeSlotUnderMouse expected
	) {
		return (mouseX, mouseY) -> recipeLayout.getSlotUnderMouse(mouseX, mouseY)
			.map(RecipeSlotUnderMouse::slot)
			.filter(slot -> slot == expected.slot())
			.isPresent();
	}
}
