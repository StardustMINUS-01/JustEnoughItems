package mezz.jei.test.gui.overlay;

import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IngredientGridWithNavigationPagingTest {
	@Test
	public void pageNumberIsPreservedWhenLayoutChanges() {
		Assertions.assertEquals(24, IngredientGridWithNavigation.getFirstItemIndexForPage(2, 30, 12));
		Assertions.assertEquals(10, IngredientGridWithNavigation.getFirstItemIndexForPage(2, 15, 10));
	}

	@Test
	public void invalidPageInputsUseFirstPage() {
		Assertions.assertEquals(0, IngredientGridWithNavigation.getFirstItemIndexForPage(2, 0, 10));
		Assertions.assertEquals(0, IngredientGridWithNavigation.getFirstItemIndexForPage(2, 15, 0));
	}
}
