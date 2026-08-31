package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.bookmarks.PreviewTooltipComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

final class FavoriteRecipePreviewState {
	private final Supplier<Optional<IRecipeLayoutDrawable<?>>> recipeLayoutSupplier;
	private @Nullable Optional<PreviewTooltipComponent<?>> previewTooltipComponent;

	FavoriteRecipePreviewState(Supplier<Optional<IRecipeLayoutDrawable<?>>> recipeLayoutSupplier) {
		this.recipeLayoutSupplier = recipeLayoutSupplier;
	}

	void addTo(JeiTooltip tooltip) {
		Optional<PreviewTooltipComponent<?>> component = getPreviewTooltipComponent();
		component.ifPresent(tooltip::add);
	}

	void tick() {
		if (previewTooltipComponent != null) {
			previewTooltipComponent.ifPresent(PreviewTooltipComponent::tick);
		}
	}

	private Optional<PreviewTooltipComponent<?>> getPreviewTooltipComponent() {
		if (previewTooltipComponent == null) {
			previewTooltipComponent = recipeLayoutSupplier.get()
				.map(PreviewTooltipComponent::new);
		}
		return previewTooltipComponent;
	}
}
