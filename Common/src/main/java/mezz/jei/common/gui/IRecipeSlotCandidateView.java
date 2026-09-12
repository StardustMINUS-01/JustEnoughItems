package mezz.jei.common.gui;

import mezz.jei.api.ingredients.ITypedIngredient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

@ApiStatus.Internal
public interface IRecipeSlotCandidateView {
	Stream<ITypedIngredient<?>> getCandidateIngredients();

	void setDisplayedCandidates(List<ITypedIngredient<?>> candidates);

	void setSelectedCandidate(@Nullable ITypedIngredient<?> candidate);
}
