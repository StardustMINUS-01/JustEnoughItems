package mezz.jei.gui.overlay;

import mezz.jei.gui.overlay.elements.IElement;

import java.util.Optional;

public record IngredientListSlotContext(
	IElement<?> element,
	Optional<IElement<?>> hoveredElement,
	int slotIndex,
	int hoveredSlotIndex,
	int rowIndex,
	int hoveredRowIndex
) {
	public IngredientListSlotContext {
		hoveredElement = hoveredElement == null ? Optional.empty() : hoveredElement;
	}
}
