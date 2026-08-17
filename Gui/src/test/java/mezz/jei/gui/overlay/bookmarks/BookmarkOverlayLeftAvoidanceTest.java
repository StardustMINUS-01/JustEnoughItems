package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BookmarkOverlayLeftAvoidanceTest {
	private static final int WIDTH = IngredientGridLayout.INGREDIENT_WIDTH;
	private static final int HEIGHT = IngredientGridLayout.INGREDIENT_HEIGHT;

	@Test
	public void topLeftExclusionShiftsAreaDownToRowBoundary() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, WIDTH, 4 * HEIGHT);
		ImmutableRect2i exclusion = new ImmutableRect2i(-10, 2, 20, 10);

		ImmutableRect2i result = BookmarkOverlay.avoidTopLeftExclusions(area, Set.of(exclusion));

		assertEquals(HEIGHT, result.y(), "area top should be aligned to the next whole row");
		assertEquals(area.height() - HEIGHT, result.height(), "area should lose exactly the blocked row");
	}

	@Test
	public void exclusionNotCoveringFirstCellLeavesAreaUnchanged() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, WIDTH, 4 * HEIGHT);
		ImmutableRect2i exclusion = new ImmutableRect2i(WIDTH, 0, WIDTH, HEIGHT);

		ImmutableRect2i result = BookmarkOverlay.avoidTopLeftExclusions(area, Set.of(exclusion));

		assertEquals(area, result);
	}

	@Test
	public void tinySliverLeavesAreaUnchanged() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, WIDTH, 4 * HEIGHT);
		ImmutableRect2i exclusion = new ImmutableRect2i(-10, 0, 12, 1);

		ImmutableRect2i result = BookmarkOverlay.avoidTopLeftExclusions(area, Set.of(exclusion));

		assertEquals(area, result);
	}

	@Test
	public void exclusionBelowFirstRowLeavesAreaUnchanged() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, WIDTH, 4 * HEIGHT);
		ImmutableRect2i exclusion = new ImmutableRect2i(-10, HEIGHT + 2, 20, HEIGHT);

		ImmutableRect2i result = BookmarkOverlay.avoidTopLeftExclusions(area, Set.of(exclusion));

		assertEquals(area, result);
	}

	@Test
	public void multipleTopLeftExclusionsShiftToTheLowestBottom() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, WIDTH, 5 * HEIGHT);
		ImmutableRect2i first = new ImmutableRect2i(-10, 0, 20, HEIGHT);
		ImmutableRect2i second = new ImmutableRect2i(-10, HEIGHT + 2, 20, HEIGHT);

		ImmutableRect2i result = BookmarkOverlay.avoidTopLeftExclusions(area, Set.of(first, second));

		assertEquals(3 * HEIGHT, result.y(), "shift should clear the lowest top-left exclusion");
	}

	@Test
	public void exclusionConsumingAllHeightLeavesAreaUnchanged() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, WIDTH, HEIGHT);
		ImmutableRect2i exclusion = new ImmutableRect2i(-10, 0, 20, HEIGHT);

		ImmutableRect2i result = BookmarkOverlay.avoidTopLeftExclusions(area, Set.of(exclusion));

		assertEquals(area, result);
	}
}
