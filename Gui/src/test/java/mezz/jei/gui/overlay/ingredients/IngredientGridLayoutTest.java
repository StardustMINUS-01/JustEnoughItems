package mezz.jei.gui.overlay.ingredients;

import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IngredientGridLayoutTest {
	private static final int WIDTH = IngredientGridLayout.INGREDIENT_WIDTH;
	private static final int HEIGHT = IngredientGridLayout.INGREDIENT_HEIGHT;

	@Test
	public void usableColumnCountCountsAllColumnsWhenNothingIsBlocked() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, 4 * WIDTH, 2 * HEIGHT);
		assertEquals(4, IngredientGridLayout.calculateUsableColumnCount(area, Set.of(), null));
	}

	@Test
	public void usableColumnCountExcludesBlockedSlotsInFirstRow() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, 4 * WIDTH, 2 * HEIGHT);
		ImmutableRect2i blockedSlot = new ImmutableRect2i(3 * WIDTH + 2, 0, 4, HEIGHT);
		assertEquals(3, IngredientGridLayout.calculateUsableColumnCount(area, Set.of(blockedSlot), null));
	}

	@Test
	public void usableColumnCountExcludesMouseExclusionPoint() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, 4 * WIDTH, 2 * HEIGHT);
		assertEquals(3, IngredientGridLayout.calculateUsableColumnCount(area, Set.of(), new ImmutablePoint2i(0, 0)));
	}

	@Test
	public void usableColumnCountIgnoresBlockedSlotsInLaterRows() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, 4 * WIDTH, 2 * HEIGHT);
		ImmutableRect2i blockedSlot = new ImmutableRect2i(0, HEIGHT + 2, WIDTH, 4);
		assertEquals(4, IngredientGridLayout.calculateUsableColumnCount(area, Set.of(blockedSlot), null));
	}

	@Test
	public void usableColumnCountIgnoresMouseExclusionPointInLaterRows() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, 4 * WIDTH, 2 * HEIGHT);
		assertEquals(4, IngredientGridLayout.calculateUsableColumnCount(area, Set.of(), new ImmutablePoint2i(3 * WIDTH, HEIGHT)));
	}

	@Test
	public void usableColumnCountIsZeroForEmptyArea() {
		assertEquals(0, IngredientGridLayout.calculateUsableColumnCount(ImmutableRect2i.EMPTY, Set.of(), null));
	}
}
