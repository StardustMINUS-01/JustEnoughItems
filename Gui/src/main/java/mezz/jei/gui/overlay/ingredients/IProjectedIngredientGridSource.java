package mezz.jei.gui.overlay.ingredients;

import java.util.List;

/** A source whose row breaks or indentation occupy additional grid slots. */
public interface IProjectedIngredientGridSource extends IIngredientGridSource {
	int getProjectedSlotCount(int columns, List<Integer> usableColumnsPerRow);
}
