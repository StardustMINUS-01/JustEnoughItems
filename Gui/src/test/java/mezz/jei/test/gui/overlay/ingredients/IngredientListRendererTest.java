package mezz.jei.test.gui.overlay.ingredients;

import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class IngredientListRendererTest {
	private static final int SLOT_SIZE = 18;

	@Test
	public void countsBlockedColumns() {
		IngredientListRenderer renderer = new IngredientListRenderer(null, false);
		for (int row = 0; row < 2; row++) {
			for (int col = 0; col < 9; col++) {
				IngredientListSlot slot = new IngredientListSlot(
					col * SLOT_SIZE,
					row * SLOT_SIZE,
					SLOT_SIZE,
					SLOT_SIZE,
					1
				);
				slot.setBlocked(row == 0 && col >= 4);
				renderer.add(slot);
			}
		}
		Assertions.assertEquals(9, renderer.getColumnCount());
	}

	@Test
	public void invalidatesRepeatedLayout() {
		IngredientListRenderer renderer = new IngredientListRenderer(null, false);
		List<IElement<?>> elements = List.of();

		renderer.set(0, elements);
		int first = renderer.getLayoutVersion();
		renderer.set(0, elements);
		int second = renderer.getLayoutVersion();

		Assertions.assertNotEquals(first, second);
	}
}
