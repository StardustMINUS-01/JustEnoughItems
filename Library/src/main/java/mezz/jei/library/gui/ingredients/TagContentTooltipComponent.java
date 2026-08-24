package mezz.jei.library.gui.ingredients;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.common.gui.CandidateTooltipComponent;

import java.util.List;

public class TagContentTooltipComponent<T> extends CandidateTooltipComponent<T> {
	public TagContentTooltipComponent(IIngredientRenderer<T> renderer, List<T> ingredients) {
		super(renderer, ingredients);
	}

	public TagContentTooltipComponent(IIngredientRenderer<T> renderer, List<T> ingredients, int selectedIndex, int windowStart) {
		super(renderer, ingredients, selectedIndex, windowStart);
	}
}
