package mezz.jei.gui.recipes;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.util.SafeIngredientUtil;

import java.util.Optional;

public interface IIngredientCandidateSource {
	Optional<ITypedIngredient<?>> getSelectedIngredient();

	default void addTooltip(JeiTooltip tooltip) {
		getSelectedIngredient().ifPresent(ingredient -> addIngredientTooltip(tooltip, ingredient));
	}

	private static <T> void addIngredientTooltip(JeiTooltip tooltip, ITypedIngredient<T> ingredient) {
		var manager = Internal.getJeiRuntime().getIngredientManager();
		tooltip.setIngredient(ingredient);
		SafeIngredientUtil.getRichTooltip(tooltip, manager, manager.getIngredientRenderer(ingredient.getType()), ingredient);
	}

	boolean isValid();

	boolean canSelect();

	boolean select(ITypedIngredient<?> ingredient, boolean synchronize);
}
