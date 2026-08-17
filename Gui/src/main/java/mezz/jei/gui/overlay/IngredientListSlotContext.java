package mezz.jei.gui.overlay;

import mezz.jei.gui.overlay.elements.IElement;

import java.util.Optional;

public record IngredientListSlotContext(
	IElement<?> element,
	Optional<IElement<?>> hoveredElement,
	int slotIndex,
	int hoveredSlotIndex,
	int rowIndex,
	int hoveredRowIndex,
	int columnCount,
	int slotCount,
	int layoutVersion
) {
	public IngredientListSlotContext {
		hoveredElement = hoveredElement == null ? Optional.empty() : hoveredElement;
	}
}
