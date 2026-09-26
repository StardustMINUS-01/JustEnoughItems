package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.filtering.RecipeFilterSettings;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecipeFilterSettingsTest {
	@Test
	public void partitionsDisabledAndPreferredRecipes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
		RecipeFilterSettings settings = new RecipeFilterSettings();
		Assertions.assertEquals(RecipeFilterMode.DEFAULT, settings.getMode());
		var disabled = net.minecraft.resources.ResourceLocation.parse("test:disabled");
		var enabled = net.minecraft.resources.ResourceLocation.parse("test:enabled");
		var manual = new mezz.jei.gui.input.FocusedRecipe(enabled, net.minecraft.resources.ResourceLocation.parse("test:manual"));
		var state = new mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.State(java.util.Set.of(disabled), java.util.Set.of(disabled), java.util.Set.of(manual));
		Assertions.assertFalse(state.allows(disabled, RecipeFilterMode.DEFAULT));
		Assertions.assertFalse(state.allows(disabled, RecipeFilterMode.PREFERRED));
		Assertions.assertFalse(state.allows(disabled, RecipeFilterMode.NOT_PREFERRED));
		Assertions.assertTrue(state.allows(disabled, RecipeFilterMode.DISABLED));
		Assertions.assertTrue(state.allows(disabled, RecipeFilterMode.ALL));
		Assertions.assertTrue(state.allows(enabled, RecipeFilterMode.DEFAULT));
		Assertions.assertFalse(state.allows(enabled, RecipeFilterMode.DISABLED));
		Assertions.assertTrue(state.isPreferred(enabled, manual, java.util.Set.of()));
		Assertions.assertTrue(state.isPreferred(disabled, null, java.util.Set.of()), "disabling must not erase category preference");
		try {
			var file = directory.resolve("categories.json");
			var favorites = new mezz.jei.gui.favorites.FavoriteRecipeStore();
			mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.load(file, favorites, () -> {});
			mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.toggle(disabled, true);
			mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.toggle(disabled, false);
			mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.clear();
			mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.load(file, favorites, () -> {});
			Assertions.assertEquals(java.util.Set.of(disabled), mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.get().disabled());
			Assertions.assertEquals(java.util.Set.of(disabled), mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.get().preferred());
		} finally {
			mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.clear();
		}
	}

	@Test
	public void editsQuery() {
		RecipeFilterSettings settings = new RecipeFilterSettings();
		settings.setDraftQuery("i:#c:ingots");

		Assertions.assertTrue(settings.commitQuery());
		Assertions.assertFalse(settings.commitQuery());
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
	public void restoresSnapshot() {
		RecipeFilterSettings settings = new RecipeFilterSettings();
		settings.setDraftQuery("discarded");
		settings.commitQuery();
		settings.setMode(RecipeFilterMode.PREFERRED);

		settings.restore(RecipeFilterMode.NOT_PREFERRED, "i:#c:ingots");

		Assertions.assertEquals(RecipeFilterMode.NOT_PREFERRED, settings.getMode());
		Assertions.assertEquals("i:#c:ingots", settings.getDraftQuery());
		Assertions.assertEquals("i:#c:ingots", settings.getAppliedQuery());
	}
}
