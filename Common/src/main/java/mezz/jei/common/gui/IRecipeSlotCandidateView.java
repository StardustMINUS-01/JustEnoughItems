package mezz.jei.common.gui;

import mezz.jei.api.ingredients.ITypedIngredient;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public interface IRecipeSlotCandidateView {
	List<ITypedIngredient<?>> getCandidates();

	default java.util.stream.Stream<ITypedIngredient<?>> getCandidateIngredients() {
		return getCandidates().stream();
	}

	void setSelectedCandidate(@org.jetbrains.annotations.Nullable ITypedIngredient<?> candidate);

	void setDisplayedCandidates(List<ITypedIngredient<?>> candidates);
}
