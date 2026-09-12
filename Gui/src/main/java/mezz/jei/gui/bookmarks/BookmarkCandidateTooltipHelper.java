package mezz.jei.gui.bookmarks;

import mezz.jei.common.Internal;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.input.MouseUtil;
import mezz.jei.gui.recipes.IIngredientCandidateSource;
import mezz.jei.gui.recipes.RecipesGui;

import java.util.List;
import java.util.function.Supplier;

public final class BookmarkCandidateTooltipHelper {
	private BookmarkCandidateTooltipHelper() {}

	public static void addTo(JeiTooltip tooltip, BookmarkCandidateTooltipState state,
		List<BookmarkIngredientKey> candidates, Supplier<IIngredientCandidateSource> source) {
		if (!Internal.getJeiClientConfigs().getClientConfig().tagContentTooltipEnabled().getValue()) {
			return;
		}
		state.getOrCreate(candidates).ifPresent(grid -> {
			var candidateSource = source.get();
			grid.setSelectedIngredient(candidateSource.getSelectedIngredient());
			grid.setMousePosition(-10000, -10000);
			tooltip.add(grid);
			if (Internal.getKeyMappings().getPauseRecipeCycling().isDown() &&
				Internal.getJeiRuntime().getRecipesGui() instanceof RecipesGui gui
			) {
				gui.showCandidateTooltip(candidateSource, grid, (int) MouseUtil.getX(), (int) MouseUtil.getY());
			}
		});
	}
}
