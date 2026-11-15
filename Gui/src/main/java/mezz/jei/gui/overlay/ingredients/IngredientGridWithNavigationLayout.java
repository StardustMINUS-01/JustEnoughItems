package mezz.jei.gui.overlay.ingredients;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.ImmutableSize2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.gui.util.AlignmentUtil;

import java.util.Set;
import java.util.function.ToIntFunction;

public record IngredientGridWithNavigationLayout(
	ImmutableRect2i ingredientGridArea,
	int availableSlotCount,
	ImmutableRect2i slotBackgroundArea,
	ImmutableRect2i navigationArea,
	ImmutableRect2i scrollbarArea,
	ImmutableRect2i backgroundArea,
	boolean navigationEnabled,
	boolean scrollbarEnabled
) {
	public static final int NAVIGATION_HEIGHT = 20;
	public static final int BORDER_MARGIN = 6;
	public static final int BORDER_PADDING = 5;
	public static final int INNER_PADDING = 2;

	public static ImmutableRect2i getAvailableGridArea(
		IIngredientGridConfig gridConfig,
		ImmutableRect2i availableArea
	) {
		return getAvailableGridArea(gridConfig, availableArea, true);
	}

	public static ImmutableRect2i getAvailableGridArea(
		IIngredientGridConfig gridConfig,
		ImmutableRect2i availableArea,
		boolean reserveNavigationArea
	) {
		ImmutableRect2i availableGridArea = availableArea
			.insetBy(BORDER_MARGIN);

		if (reserveNavigationArea) {
			availableGridArea = availableGridArea.cropTop(NAVIGATION_HEIGHT + INNER_PADDING);
		}

		if (gridConfig.backgroundStyle().get().isEnabled()) {
			availableGridArea = availableGridArea.insetBy(BORDER_PADDING + INNER_PADDING);
		}

		ImmutableRect2i estimatedGridArea = IngredientGridLayout.calculateBounds(gridConfig, availableGridArea, false);
		if (estimatedGridArea.isEmpty()) {
			return ImmutableRect2i.EMPTY;
		}

		return availableGridArea;
	}

	public static IngredientGridWithNavigationLayout fromGridArea(
		IIngredientGridConfig gridConfig,
		ImmutableRect2i ingredientGridArea,
		boolean navigationEnabled
	) {
		ImmutableRect2i slotBackgroundArea = calculateSlotBackgroundArea(ingredientGridArea, gridConfig);
		ImmutableRect2i navigationArea = calculateNavigationArea(slotBackgroundArea, navigationEnabled);
		return fromGridArea(
			gridConfig,
			ingredientGridArea,
			IngredientGridLayout.calculateAvailableSlotCount(ingredientGridArea, Set.of()),
			navigationArea,
			navigationArea,
			navigationEnabled,
			ImmutableRect2i.EMPTY,
			false
		);
	}

	static IngredientGridWithNavigationLayout fromGridArea(
		IIngredientGridConfig gridConfig,
		ImmutableRect2i ingredientGridArea,
		int availableSlotCount,
		ImmutableRect2i navigationArea,
		ImmutableRect2i backgroundNavigationArea,
		boolean navigationEnabled,
		ImmutableRect2i scrollbarArea,
		boolean scrollbarEnabled
	) {
		ImmutableRect2i slotBackgroundArea = calculateSlotBackgroundArea(ingredientGridArea, gridConfig);
		ImmutableRect2i backgroundArea = MathUtil.union(MathUtil.union(slotBackgroundArea, backgroundNavigationArea), scrollbarArea);
		if (gridConfig.backgroundStyle().get().isEnabled() && !backgroundArea.isEmpty()) {
			backgroundArea = backgroundArea.expandBy(BORDER_PADDING);
		}
		return new IngredientGridWithNavigationLayout(
			ingredientGridArea,
			availableSlotCount,
			slotBackgroundArea,
			navigationArea,
			scrollbarArea,
			backgroundArea,
			navigationEnabled,
			scrollbarEnabled
		);
	}

	public static ImmutableRect2i calculateSlotBackgroundArea(ImmutableRect2i ingredientGridArea, IIngredientGridConfig gridConfig) {
		if (ingredientGridArea.isEmpty()) {
			return ImmutableRect2i.EMPTY;
		}
		if (gridConfig.backgroundStyle().get().isEnabled()) {
			return ingredientGridArea.expandBy(INNER_PADDING);
		} else {
			return ingredientGridArea;
		}
	}

	IngredientGridWithNavigationLayout shrinkToFit(
		IIngredientGridConfig gridConfig,
		Set<ImmutableRect2i> exclusionAreas,
		int ingredientCount
	) {
		return shrinkToFit(gridConfig, exclusionAreas, ingredientCount, area -> ingredientCount);
	}

	IngredientGridWithNavigationLayout shrinkToFitProjected(IIngredientGridConfig gridConfig, Set<ImmutableRect2i> exclusionAreas, ToIntFunction<ImmutableRect2i> slotCount) {
		return shrinkToFit(gridConfig, exclusionAreas, 1, slotCount);
	}

	private IngredientGridWithNavigationLayout shrinkToFit(IIngredientGridConfig gridConfig, Set<ImmutableRect2i> exclusionAreas, int minimumSlotCount, ToIntFunction<ImmutableRect2i> projectedSlotCount) {
		if (!gridConfig.shrinkToFit().get() || !hasRoom()) {
			return this;
		}
		int ingredientCount = projectedSlotCount.applyAsInt(ingredientGridArea);
		if (ingredientCount > availableSlotCount) {
			return this;
		}

		int columns = ingredientGridArea.width() / IngredientGridLayout.INGREDIENT_WIDTH;
		int requiredSlots = Math.max(1, minimumSlotCount);
		int minRows = Math.max(gridConfig.getMinRows(), Math.ceilDiv(requiredSlots, columns));
		for (int rows = minRows; rows * IngredientGridLayout.INGREDIENT_HEIGHT < ingredientGridArea.height(); rows++) {
			ImmutableSize2i size = new ImmutableSize2i(ingredientGridArea.width(), rows * IngredientGridLayout.INGREDIENT_HEIGHT);
			ImmutableRect2i gridArea = AlignmentUtil.align(
				size, ingredientGridArea, gridConfig.horizontalAlignment().get(), gridConfig.verticalAlignment().get()
			);
			int slotCount = IngredientGridLayout.calculateAvailableSlotCount(gridArea, exclusionAreas);
			if (slotCount < Math.max(1, projectedSlotCount.applyAsInt(gridArea))) {
				continue;
			}

			ImmutableRect2i resizedNavigationArea = ImmutableRect2i.EMPTY;
			if (navigationEnabled) {
				resizedNavigationArea = navigationArea.moveDown(gridArea.y() - ingredientGridArea.y());
				if (exclusionAreas.stream().anyMatch(resizedNavigationArea::intersects)) {
					continue;
				}
			}

			ImmutableRect2i resizedSlotBackgroundArea = calculateSlotBackgroundArea(gridArea, gridConfig);
			ImmutableRect2i resizedScrollbarArea = ImmutableRect2i.EMPTY;
			if (scrollbarEnabled) {
				resizedScrollbarArea = new ImmutableRect2i(
					scrollbarArea.x(), resizedSlotBackgroundArea.y(), scrollbarArea.width(), resizedSlotBackgroundArea.height()
				);
			}
			return fromGridArea(
				gridConfig,
				gridArea,
				slotCount,
				resizedNavigationArea,
				calculateNavigationArea(resizedSlotBackgroundArea, navigationEnabled),
				navigationEnabled,
				resizedScrollbarArea,
				scrollbarEnabled
			);
		}
		return this;
	}

	public static ImmutableRect2i calculateNavigationArea(ImmutableRect2i slotBackgroundArea, boolean navigationEnabled) {
		if (!navigationEnabled) {
			return ImmutableRect2i.EMPTY;
		}

		return slotBackgroundArea
			.keepTop(NAVIGATION_HEIGHT)
			.moveUp(NAVIGATION_HEIGHT + INNER_PADDING);
	}

	public boolean hasRoom() {
		return !ingredientGridArea.isEmpty() &&
			availableSlotCount > 0 &&
			(!navigationEnabled || !navigationArea.isEmpty()) &&
			(!scrollbarEnabled || !scrollbarArea.isEmpty());
	}
}
