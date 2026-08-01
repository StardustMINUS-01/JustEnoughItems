package mezz.jei.gui.overlay.ingredients;

import mezz.jei.gui.input.IRecipeFocusSource;
import mezz.jei.gui.overlay.elements.IElement;

import java.util.List;
import java.util.stream.Stream;

public interface IIngredientGrid extends IRecipeFocusSource {
	boolean isMouseOver(double mouseX, double mouseY);

	int size();

	int getColumnCount();

	/**
	 * The number of usable columns: unblocked slots in the first row that has any
	 * unblocked slot. Unlike {@link #getColumnCount()}, this excludes slots blocked
	 * by GUI exclusion areas or the mouse exclusion point, so it matches the visible
	 * slots that elements are actually rendered into.
	 */
	int getUsableColumnCount();

	int getRowCount();

	void set(int firstItemIndex, List<IElement<?>> ingredientList);

	default void set(int firstItemIndex, int smoothScrollRowPixelOffset, List<IElement<?>> ingredientList) {
		set(firstItemIndex, ingredientList);
	}

	Stream<IElement<?>> getVisibleElements();

	void tick();
}
