package mezz.jei.common.gui;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;

public interface IRecipeSlotHotkeyScreenInternal {
	void addSlotHotkeys(ITooltipBuilder tooltip, IRecipeSlotDrawable slot, ITypedIngredient<?> ingredient);
}
