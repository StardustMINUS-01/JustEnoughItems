package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;

import java.util.List;

public final class BookmarkCandidateTooltipHelper {
	private BookmarkCandidateTooltipHelper() {
	}

	public static void addTo(
		JeiTooltip tooltip,
		BookmarkPermutationTooltipState tooltipState,
		Object sourceKey,
		ITypedIngredient<?> selectedIngredient,
		List<BookmarkIngredientKey> permutationKeys
	) {
		if (!isEnabled(permutationKeys)) {
			return;
		}
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		BookmarkIngredientKey selectedKey = BookmarkItemMetadataFactory.createPermutationKey(selectedIngredient, ingredientManager);
		addTo(tooltip, tooltipState, sourceKey, selectedKey, permutationKeys, ingredientManager);
	}

	public static void addTo(
		JeiTooltip tooltip,
		BookmarkPermutationTooltipState tooltipState,
		Object sourceKey,
		BookmarkIngredientKey selectedKey,
		List<BookmarkIngredientKey> permutationKeys
	) {
		if (!isEnabled(permutationKeys)) {
			return;
		}
		IIngredientManager ingredientManager = Internal.getJeiRuntime().getIngredientManager();
		addTo(tooltip, tooltipState, sourceKey, selectedKey, permutationKeys, ingredientManager);
	}

	private static void addTo(
		JeiTooltip tooltip,
		BookmarkPermutationTooltipState tooltipState,
		Object sourceKey,
		BookmarkIngredientKey selectedKey,
		List<BookmarkIngredientKey> permutationKeys,
		IIngredientManager ingredientManager
	) {
		tooltipState.getOrCreateTooltip(
			sourceKey,
			permutationKeys,
			selectedKey,
			ingredientManager
		).ifPresent(tooltip::add);
	}

	private static boolean isEnabled(List<BookmarkIngredientKey> permutationKeys) {
		return permutationKeys.size() > 1 &&
			Internal.getJeiClientConfigs().getClientConfig().tagContentTooltipEnabled().getValue();
	}

}
