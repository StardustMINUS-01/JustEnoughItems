package mezz.jei.common.gui;

import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.function.ToIntFunction;

public interface IRecipeSlotBackgroundInternal {
	void setIngredientBackgroundColor(ToIntFunction<ITypedIngredient<?>> color);
}
