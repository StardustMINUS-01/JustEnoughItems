package mezz.jei.common.gui;

import mezz.jei.api.ingredients.ITypedIngredient;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public interface IRecipeSlotCandidateView {
	void setDisplayedCandidates(List<ITypedIngredient<?>> candidates);
}
