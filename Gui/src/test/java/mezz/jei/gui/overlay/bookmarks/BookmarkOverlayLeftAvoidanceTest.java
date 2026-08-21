package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.common.config.IngredientGridNavigationMode;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.NavigationVisibility;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BookmarkOverlayLeftAvoidanceTest {
	private static final int WIDTH = IngredientGridLayout.INGREDIENT_WIDTH;
	private static final int HEIGHT = IngredientGridLayout.INGREDIENT_HEIGHT;

	@ParameterizedTest
	@EnumSource(value = IngredientGridNavigationMode.class, names = {"SCROLLING", "SMOOTH_SCROLLING"})
	public void scrollbarNavigationUsesWholeRowAvoidance(IngredientGridNavigationMode navigationMode) {
		for (NavigationVisibility navigationVisibility : NavigationVisibility.values()) {
			assertTrue(BookmarkOverlay.shouldAvoidTopLeftExclusions(navigationMode, navigationVisibility));
		}
	}

	@Test
	public void enabledPagedNavigationUsesUpstreamAvoidance() {
		assertFalse(BookmarkOverlay.shouldAvoidTopLeftExclusions(
			IngredientGridNavigationMode.PAGED,
			NavigationVisibility.ENABLED
		));
	}

	@ParameterizedTest
	@EnumSource(value = NavigationVisibility.class, names = {"AUTO_HIDE", "DISABLED"})
	public void hiddenPagedNavigationUsesWholeRowAvoidance(NavigationVisibility navigationVisibility) {
		assertTrue(BookmarkOverlay.shouldAvoidTopLeftExclusions(IngredientGridNavigationMode.PAGED, navigationVisibility));
	}

	@Test
	public void groupPanelClipAreaUsesGridVerticalBoundsAndExtendsLeft() {
		ImmutableRect2i gridArea = new ImmutableRect2i(20, 30, 90, 72);

		ImmutableRect2i result = BookmarkOverlay.calculateGroupPanelClipArea(gridArea);

		assertEquals(new ImmutableRect2i(13, 30, 7, 72), result);
	}

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

	@Test
	public void avoidedExclusionDoesNotBlockSmoothScrollingPartialRow() {
		ImmutableRect2i area = new ImmutableRect2i(0, 0, 2 * WIDTH, 4 * HEIGHT);
		ImmutableRect2i exclusion = new ImmutableRect2i(-10, 0, 20, HEIGHT);
		ImmutableRect2i adjustedArea = BookmarkOverlay.avoidTopLeftExclusions(area, Set.of(exclusion));

		Set<ImmutableRect2i> contentExclusions = BookmarkOverlay.filterContentExclusionAreas(adjustedArea, Set.of(exclusion));
		IngredientGridLayout.SlotLayout firstSlot = IngredientGridLayout.calculateSlots(
			adjustedArea,
			contentExclusions,
			null,
			1
		).getFirst();

		assertFalse(firstSlot.blocked());
	}

	@Test
	public void contentExclusionsKeepAreasThatReachAdjustedArea() {
		ImmutableRect2i area = new ImmutableRect2i(0, HEIGHT, 2 * WIDTH, 3 * HEIGHT);
		ImmutableRect2i exclusion = new ImmutableRect2i(WIDTH, HEIGHT, WIDTH, HEIGHT);

		Set<ImmutableRect2i> result = BookmarkOverlay.filterContentExclusionAreas(area, Set.of(exclusion));

		assertEquals(Set.of(exclusion), result);
	}

	@Test
	public void contentMouseExclusionDropsPointAboveAdjustedArea() {
		ImmutableRect2i area = new ImmutableRect2i(0, HEIGHT, 2 * WIDTH, 3 * HEIGHT);
		ImmutablePoint2i point = new ImmutablePoint2i(0, HEIGHT - 1);

		ImmutablePoint2i result = BookmarkOverlay.filterContentMouseExclusionPoint(area, point);

		assertNull(result);
	}

	@Test
	public void contentMouseExclusionKeepsPointBelowAdjustedArea() {
		ImmutableRect2i area = new ImmutableRect2i(0, HEIGHT, 2 * WIDTH, 3 * HEIGHT);
		ImmutablePoint2i point = new ImmutablePoint2i(0, 4 * HEIGHT);

		ImmutablePoint2i result = BookmarkOverlay.filterContentMouseExclusionPoint(area, point);

		assertEquals(point, result);
	}

	@Test
	public void contentMouseExclusionKeepsPointInsideAdjustedArea() {
		ImmutableRect2i area = new ImmutableRect2i(0, HEIGHT, 2 * WIDTH, 3 * HEIGHT);
		ImmutablePoint2i point = new ImmutablePoint2i(WIDTH, 2 * HEIGHT);

		ImmutablePoint2i result = BookmarkOverlay.filterContentMouseExclusionPoint(area, point);

		assertEquals(point, result);
	}
}
