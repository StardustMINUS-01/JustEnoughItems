package mezz.jei.gui.overlay.history;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.common.config.NavigationVisibility;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.ingredients.IngredientGridButtonNavigationLayout;
import mezz.jei.gui.overlay.ingredients.IngredientGridConfigTestFixtures.TestGridConfig;
import mezz.jei.gui.overlay.ingredients.IngredientGridLayout;
import mezz.jei.gui.overlay.ingredients.IngredientGridScrollbarLayout;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigationLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LookupHistoryOverlayLayoutTest {
	@ParameterizedTest
	@EnumSource(HorizontalAlignment.class)
	public void visualAreaFollowsOwnerHorizontalAlignment(HorizontalAlignment alignment) {
		for (boolean background : new boolean[]{false, true}) {
			TestGridConfig config = new TestGridConfig().maxColumns(4).maxRows(2)
				.horizontalAlignment(alignment).drawBackground(background).navigationVisibility(NavigationVisibility.DISABLED);
			for (boolean scrollbar : new boolean[]{false, true}) {
				for (int x : new int[]{0, 150}) {
					ImmutableRect2i displayArea = new ImmutableRect2i(x, 0, 250, 300);
					int historyHeight = LookupHistoryOverlayLayout.getDisplayHeight(2, background, scrollbar);
					var owner = calculate(config, displayArea.cropBottom(historyHeight), scrollbar);
					var history = calculate(config, displayArea.keepBottom(historyHeight), scrollbar);
					assertTrue(history.hasRoom());
					assertEquals(2 * IngredientGridLayout.INGREDIENT_HEIGHT, history.ingredientGridArea().height());
					assertEquals(owner.backgroundArea().x(), history.backgroundArea().x());
					assertEquals(owner.backgroundArea().width(), history.backgroundArea().width());
				}
			}
		}
	}

	@ParameterizedTest
	@EnumSource(VerticalAlignment.class)
	public void visualBottomEdgeDoesNotChangeWhenBackgroundIsEnabled(VerticalAlignment alignment) {
		ImmutableRect2i displayArea = new ImmutableRect2i(0, 0, 250, 300);
		for (boolean scrollbar : new boolean[]{false, true}) {
			int bottom = -1;
			for (boolean background : new boolean[]{false, true}) {
				TestGridConfig config = new TestGridConfig().maxColumns(4).maxRows(2)
					.verticalAlignment(alignment).drawBackground(background).navigationVisibility(NavigationVisibility.DISABLED);
				int height = LookupHistoryOverlayLayout.getDisplayHeight(2, background, scrollbar);
				var layout = calculate(config, displayArea.keepBottom(height), scrollbar);
				assertTrue(layout.hasRoom());
				int currentBottom = layout.backgroundArea().y() + layout.backgroundArea().height();
				if (bottom >= 0) {
					assertEquals(bottom, currentBottom);
				}
				bottom = currentBottom;
			}
		}
	}

	private static IngredientGridWithNavigationLayout calculate(TestGridConfig config, ImmutableRect2i area, boolean scrollbar) {
		return scrollbar
			? IngredientGridScrollbarLayout.calculate(config, area, Set.of(), 100, false)
			: IngredientGridButtonNavigationLayout.calculate(config, area, Set.of(), 100);
	}

	@Test
	public void pagedHistoryReservesNavigationHeight() {
		int maxRows = 3;
		int scrollingHeight = LookupHistoryOverlayLayout.getDisplayHeight(maxRows, true, true);
		int pagedHeight = LookupHistoryOverlayLayout.getDisplayHeight(maxRows, true, false);

		assertEquals(
			IngredientGridWithNavigationLayout.NAVIGATION_HEIGHT + IngredientGridWithNavigationLayout.INNER_PADDING,
			pagedHeight - scrollingHeight
		);
	}

	@Test
	public void displayHeightIncludesRowsAndPanelPadding() {
		int maxRows = 3;
		int expectedHeight = (maxRows * IngredientGridLayout.INGREDIENT_HEIGHT) +
			(2 * IngredientGridWithNavigationLayout.BORDER_MARGIN) +
			(2 * (IngredientGridWithNavigationLayout.BORDER_PADDING +
				IngredientGridWithNavigationLayout.INNER_PADDING));

		int displayHeight = LookupHistoryOverlayLayout.getDisplayHeight(maxRows, true, true);

		assertEquals(expectedHeight, displayHeight);
	}
}
