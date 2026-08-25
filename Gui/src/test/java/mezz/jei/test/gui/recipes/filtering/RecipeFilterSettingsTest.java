package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.filtering.RecipeFilterSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecipeFilterSettingsTest {
	@Test
	public void cyclesThroughTheThreeFilterModes() {
		RecipeFilterSettings settings = new RecipeFilterSettings();

		Assertions.assertEquals(RecipeFilterMode.ALL, settings.getMode());
		Assertions.assertEquals(RecipeFilterMode.PREFERRED, settings.cycleMode());
		Assertions.assertEquals(RecipeFilterMode.NOT_PREFERRED, settings.cycleMode());
		Assertions.assertEquals(RecipeFilterMode.ALL, settings.cycleMode());
	}

	@Test
	public void commitsCancelsAndClearsSearchText() {
		RecipeFilterSettings settings = new RecipeFilterSettings();
		settings.setDraftQuery("i:#c:ingots");

		Assertions.assertTrue(settings.commitQuery());
		Assertions.assertEquals("i:#c:ingots", settings.getAppliedQuery());

		settings.setDraftQuery("unfinished");
		settings.cancelQuery();
		Assertions.assertEquals("i:#c:ingots", settings.getDraftQuery());

		Assertions.assertTrue(settings.clearQuery());
		Assertions.assertEquals("", settings.getDraftQuery());
		Assertions.assertEquals("", settings.getAppliedQuery());
		Assertions.assertFalse(settings.clearQuery());
	}

	@Test
	public void restoresARecipeNavigationSnapshot() {
		RecipeFilterSettings settings = new RecipeFilterSettings();
		settings.setDraftQuery("discarded");
		settings.commitQuery();
		settings.cycleMode();

		settings.restore(RecipeFilterMode.NOT_PREFERRED, "i:#c:ingots");

		Assertions.assertEquals(RecipeFilterMode.NOT_PREFERRED, settings.getMode());
		Assertions.assertEquals("i:#c:ingots", settings.getDraftQuery());
		Assertions.assertEquals("i:#c:ingots", settings.getAppliedQuery());
	}
}
