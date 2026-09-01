package mezz.jei.library.gui.ingredients;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.common.gui.CandidateTooltipComponent;

import java.util.List;

/**
 * 1.20.1 port: delegates to {@link CandidateTooltipComponent} so the permutation
 * tooltip renders the scrolling candidate window and highlights the selected
 * ingredient (matching 1.21.1). Previously this was a stub that ignored
 * selectedIndex/windowStart and drew no highlight.
 */
public class TagContentTooltipComponent<T> extends CandidateTooltipComponent<T> {
	public TagContentTooltipComponent(IIngredientRenderer<T> renderer, List<T> ingredients) {
		super(renderer, ingredients);
	}

	public TagContentTooltipComponent(IIngredientRenderer<T> renderer, List<T> ingredients, int selectedIndex, int windowStart) {
		super(renderer, ingredients, selectedIndex, windowStart);
	}
}
