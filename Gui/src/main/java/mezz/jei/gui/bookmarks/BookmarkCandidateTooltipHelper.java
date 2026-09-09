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

	public static void addTo(JeiTooltip tooltip, BookmarkCandidateTooltipState state,
		List<BookmarkIngredientKey> candidates, java.util.function.Supplier<mezz.jei.gui.recipes.IIngredientCandidateSource> source) {
		if (!Internal.getJeiClientConfigs().getClientConfig().isTagContentTooltipEnabled()) {
			return;
		}
		state.getOrCreate(candidates).ifPresent(grid -> {
			var candidateSource = source.get();
			grid.setSelectedIngredient(candidateSource.getSelectedIngredient());
			grid.setMousePosition(-10000, -10000);
			tooltip.add(grid);
			if (Internal.getKeyMappings().getPauseRecipeCycling().isDown() &&
				Internal.getJeiRuntime().getRecipesGui() instanceof mezz.jei.gui.recipes.RecipesGui gui) {
				gui.showCandidateTooltip(candidateSource, grid, (int) mezz.jei.gui.input.MouseUtil.getX(), (int) mezz.jei.gui.input.MouseUtil.getY());
			}
		});
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
			Internal.getJeiClientConfigs().getClientConfig().isTagContentTooltipEnabled();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Optional<ITypedIngredient<?>> resolvePermutation(IIngredientManager ingredientManager, BookmarkIngredientKey key) {
		return ingredientManager.getIngredientTypeForUid(key.ingredientTypeUid())
			.flatMap(type -> ingredientManager.getTypedIngredientByUid((IIngredientType) type, key.ingredientUid()))
			.map(typedIngredient -> (ITypedIngredient<?>) typedIngredient);
	}
}
