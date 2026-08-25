package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.CandidateTooltipComponent;
import mezz.jei.common.gui.JeiTooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
		List<BookmarkIngredientKey> resolvedKeys = new ArrayList<>(permutationKeys.size());
		List<ITypedIngredient<?>> candidates = new ArrayList<>(permutationKeys.size());
		for (BookmarkIngredientKey key : permutationKeys) {
			resolvePermutation(ingredientManager, key).ifPresent(ingredient -> {
				resolvedKeys.add(key);
				candidates.add(ingredient);
			});
		}
		if (candidates.size() <= 1) {
			return;
		}
		int selectedIndex = resolvedKeys.indexOf(selectedKey);
		int windowStart = tooltipState.updateStart(sourceKey, resolvedKeys, selectedIndex);
		tooltip.add(CandidateTooltipComponent.create(ingredientManager, candidates, selectedIndex, windowStart));
	}

	private static boolean isEnabled(List<BookmarkIngredientKey> permutationKeys) {
		return permutationKeys.size() > 1 &&
			Internal.getJeiClientConfigs().getClientConfig().tagContentTooltipEnabled().getValue();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Optional<ITypedIngredient<?>> resolvePermutation(IIngredientManager ingredientManager, BookmarkIngredientKey key) {
		return ingredientManager.getIngredientTypeForUid(key.ingredientTypeUid())
			.flatMap(type -> ingredientManager.getTypedIngredientByUid((IIngredientType) type, key.ingredientUid()))
			.map(typedIngredient -> (ITypedIngredient<?>) typedIngredient);
	}
}
