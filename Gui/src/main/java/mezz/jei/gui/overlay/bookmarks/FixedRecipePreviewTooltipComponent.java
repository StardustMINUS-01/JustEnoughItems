package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;

public class FixedRecipePreviewTooltipComponent<R> extends PreviewTooltipComponent<R> {
	public FixedRecipePreviewTooltipComponent(IRecipeLayoutDrawable<R> drawable) {
		super(drawable);
	}

	@Override
	public void tick() {
	}
}
