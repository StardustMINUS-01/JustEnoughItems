package mezz.jei.gui.startup;

import mezz.jei.common.platform.Services;
import mezz.jei.gui.config.BookmarkJsonConfig;
import mezz.jei.gui.config.CollapsibleConfig;
import mezz.jei.gui.config.CollapsibleStateStore;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.config.ILookupHistoryConfig;
import mezz.jei.gui.config.IngredientTypeSortingConfig;
import mezz.jei.gui.config.LookupHistoryJsonConfig;
import mezz.jei.gui.config.ModNameSortingConfig;
import mezz.jei.gui.config.RecipePreferenceConfig;

import java.nio.file.Path;

public record GuiConfigData(
	Path configDir,
	IBookmarkConfig bookmarkConfig,
	RecipePreferenceConfig recipePreferenceConfig,
	CollapsibleConfig collapsibleConfig,
	CollapsibleStateStore collapsibleStateStore,
	ILookupHistoryConfig lookupHistoryConfig,
	ModNameSortingConfig modNameSortingConfig,
	IngredientTypeSortingConfig ingredientTypeSortingConfig
) {
	private static final CollapsibleConfig COLLAPSIBLE_CONFIG = createCollapsibleConfig();

	private static CollapsibleConfig createCollapsibleConfig() {
		CollapsibleConfig config = new CollapsibleConfig(Services.PLATFORM.getConfigHelper().createJeiConfigDir());
		if (!Services.PLATFORM.getModHelper().isModLoaded("collapsible_groups")) {
			config.initialize();
		}
		return config;
	}

	public static GuiConfigData create() {
		Path configDir = Services.PLATFORM.getConfigHelper().createJeiConfigDir();

		IBookmarkConfig bookmarkConfig = new BookmarkJsonConfig(configDir);
		RecipePreferenceConfig recipePreferenceConfig = new RecipePreferenceConfig(configDir);
		CollapsibleStateStore collapsibleStateStore = new CollapsibleStateStore(configDir);
		ILookupHistoryConfig lookupHistoryConfig = new LookupHistoryJsonConfig(configDir);
		ModNameSortingConfig ingredientModNameSortingConfig = new ModNameSortingConfig(configDir.resolve("ingredient-list-mod-sort-order.ini"));
		IngredientTypeSortingConfig ingredientTypeSortingConfig = new IngredientTypeSortingConfig(configDir.resolve("ingredient-list-type-sort-order.ini"));

		return new GuiConfigData(
			configDir,
			bookmarkConfig,
			recipePreferenceConfig,
			COLLAPSIBLE_CONFIG,
			collapsibleStateStore,
			lookupHistoryConfig,
			ingredientModNameSortingConfig,
			ingredientTypeSortingConfig
		);
	}
}
