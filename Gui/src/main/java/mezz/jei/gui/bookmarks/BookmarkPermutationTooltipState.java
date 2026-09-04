package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.gui.CandidateTooltipComponent;
import mezz.jei.common.gui.CandidateTooltipWindow;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BookmarkPermutationTooltipState {
	private @Nullable Object sourceKey;
	private List<BookmarkIngredientKey> candidates = List.of();
	private @Nullable ResolvedCandidates resolvedCandidates;
	private @Nullable BookmarkIngredientKey selectedKey;
	private @Nullable CandidateTooltipComponent<?> tooltipComponent;
	private int windowStart;

	public int updateStart(int bookmarkIndex, List<BookmarkIngredientKey> candidates, int selectedIndex) {
		return updateStart(Integer.valueOf(bookmarkIndex), candidates, selectedIndex);
	}

	public int updateStart(Object sourceKey, List<BookmarkIngredientKey> candidates, int selectedIndex) {
		updateSource(sourceKey, candidates);
		this.windowStart = CandidateTooltipWindow.updateStart(candidates.size(), selectedIndex, windowStart);
		return windowStart;
	}

	Optional<CandidateTooltipComponent<?>> getOrCreateTooltip(
		Object sourceKey,
		List<BookmarkIngredientKey> candidates,
		BookmarkIngredientKey selectedKey,
		IIngredientManager ingredientManager
	) {
		if (updateSource(sourceKey, candidates) || resolvedCandidates == null) {
			resolvedCandidates = resolveCandidates(candidates);
		}
		if (resolvedCandidates.ingredients().size() <= 1) {
			return Optional.empty();
		}
		if (tooltipComponent == null || !Objects.equals(selectedKey, this.selectedKey)) {
			this.selectedKey = selectedKey;
			int selectedIndex = resolvedCandidates.keys().indexOf(selectedKey);
			this.windowStart = CandidateTooltipWindow.updateStart(
				resolvedCandidates.ingredients().size(),
				selectedIndex,
				windowStart
			);
			this.tooltipComponent = CandidateTooltipComponent.create(
				ingredientManager,
				resolvedCandidates.ingredients(),
				selectedIndex,
				windowStart
			);
		}
		return Optional.of(tooltipComponent);
	}

	private static ResolvedCandidates resolveCandidates(List<BookmarkIngredientKey> candidates) {
		List<BookmarkIngredientKey> keys = candidates.stream()
			.filter(key -> key.typedIngredient() != null)
			.toList();
		List<ITypedIngredient<?>> ingredients = keys.stream()
			.map(BookmarkIngredientKey::typedIngredient)
			.toList();
		return new ResolvedCandidates(keys, ingredients);
	}

	private boolean updateSource(Object sourceKey, List<BookmarkIngredientKey> candidates) {
		boolean sameSource = sourceKey == this.sourceKey || Objects.equals(sourceKey, this.sourceKey);
		boolean sameCandidates = candidates == this.candidates || this.candidates.equals(candidates);
		this.sourceKey = sourceKey;
		this.candidates = candidates;
		if (sameSource && sameCandidates) {
			return false;
		}
		this.resolvedCandidates = null;
		this.selectedKey = null;
		this.tooltipComponent = null;
		this.windowStart = 0;
		return true;
	}

	record ResolvedCandidates(
		List<BookmarkIngredientKey> keys,
		List<ITypedIngredient<?>> ingredients
	) {
	}
}
