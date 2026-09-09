package mezz.jei.gui.recipes;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.gui.JeiTooltip;

import java.util.Optional;

/** 1.20.1 stub for recipe-tree candidate selection. */
public interface IIngredientCandidateSource {
	Optional<ITypedIngredient<?>> getSelectedIngredient();
	boolean isValid();
	boolean canSelect();
	boolean select(ITypedIngredient<?> ingredient, boolean synchronize);
	default void addTooltip(JeiTooltip tooltip) {}
}
