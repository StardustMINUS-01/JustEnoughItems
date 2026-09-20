package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;

public class FixedRecipePreviewTooltipComponent<R> extends PreviewTooltipComponent<R> {
	public FixedRecipePreviewTooltipComponent(IRecipeLayoutDrawable<R> drawable) {
		super(drawable, ((mezz.jei.gui.recipes.RecipesGui) mezz.jei.common.Internal.getJeiRuntime().getRecipesGui()).getRecipeTransferService());
	}

	@Override
	public void tick() {
	}
}
