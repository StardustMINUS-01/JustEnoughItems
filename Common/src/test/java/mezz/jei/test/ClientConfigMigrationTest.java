package mezz.jei.test;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.common.config.BookmarkAddPosition;
import mezz.jei.common.config.BookmarkRecipeMarkerMode;
import mezz.jei.common.config.CollapsibleColorConfig;
import mezz.jei.common.config.ClientConfigs;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.config.GiveMode;
import mezz.jei.common.config.HistoryDisplaySide;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.config.IngredientGridBackgroundStyle;
import mezz.jei.common.config.IngredientGridLayoutMode;
import mezz.jei.common.config.IngredientGridNavigationMode;
import mezz.jei.common.config.IngredientSortStage;
import mezz.jei.common.config.NavigationVisibility;
import mezz.jei.common.config.SearchBarPosition;
import mezz.jei.common.config.SearchMode;
import mezz.jei.common.config.legacy.LegacyConfigPaths;
import net.mezzdev.config.api.sorting.ISortingConfig;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigFileUtil;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchemaBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientConfigMigrationTest {
	@ParameterizedTest
	@ValueSource(strings = {"root", "profile"})
	public void preservesJeiuSettingsAcrossMigrationAndReload(String location, @TempDir Path tempDir) throws IOException {
		UUID profileId = UUID.randomUUID();
		Path configFile = tempDir.resolve("client").resolve("jei-client.ini");
		Path source = switch (location) {
			case "profile" -> tempDir.resolve("players").resolve(profileId.toString()).resolve("jei-client.ini");
			default -> tempDir.resolve("jei-client.ini");
		};
		Files.createDirectories(source.getParent());
		// Keep the retired UI setting in the source to verify that migration backups preserve it.
		String legacyContent = """
			[appearance]
			quantityFieldEnabled = false
			configScreenTransparency = 73
			[bookmarks]
			bookmarkOutputAsRecipe = true
			recipeMarkerMode = BACKGROUND
			amountFontScale = 87
			showRecipeHandlerIcon = false
			favoriteTreeDepth = 42
			[input]
			copyFullComponents = true
			[collapsible]
			collapsedColor = 0xFF123456
			expandedColor = 0x01234567
			showGroupSize = true
			""";
		Files.writeString(source, legacyContent);
		var disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		for (int load = 0; load < 2; load++) {
			var manager = new ConfigManager("JEIU Migration Test", disabledWatcher, disabledWatcher);
			var schema = new ConfigSchemaBuilder("jei", configFile, "jei.config.client", manager);
			schema.setLegacySources(LegacyConfigPaths.get(tempDir, profileId, "jei-client.ini"));
			var configs = new ClientConfigs(schema, false, manager.createInMemorySortingConfig(Comparator.naturalOrder(), true));
			assertJeiuSettings(configs.getClientConfig());
		}
		assertTrue(Files.exists(configFile));
		assertEquals(legacyContent, Files.readString(source));
		assertEquals(legacyContent, Files.readString(ConfigFileUtil.getBackupPath(source, 1)));
		assertTrue(Files.notExists(ConfigFileUtil.getBackupPath(source, 2)));
	}

	@Test
	public void readsAndReloadsCurrentJeiuSettingsWithoutImportingLegacyFiles(@TempDir Path tempDir) throws IOException {
		Path configFile = tempDir.resolve("client").resolve("jei-client.ini");
		Files.createDirectories(configFile.getParent());
		// Current files use the native schema; old category names are only imported from legacy locations.
		Files.writeString(configFile, """
			[ingredientList]
			quantityFieldEnabled = false
			[bookmarkList]
			bookmarkOutputAsRecipe = true
			recipeMarkerMode = BACKGROUND
			amountFontScale = 87
			showRecipeHandlerIcon = false
			favoriteTreeDepth = 42
			[input]
			copyFullComponents = true
			[collapsible]
			collapsedColor = 0xFF123456
			expandedColor = 0x01234567
			showGroupSize = true
			""");
		UUID profileId = UUID.randomUUID();
		List<Path> legacyPaths = LegacyConfigPaths.get(tempDir, profileId, "jei-client.ini");
		String legacyContent = """
			[appearance]
			quantityFieldEnabled = true
			[bookmarks]
			amountFontScale = 25
			""";
		for (Path legacyPath : legacyPaths) {
			Files.createDirectories(legacyPath.getParent());
			Files.writeString(legacyPath, legacyContent);
		}
		var disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		for (int load = 0; load < 2; load++) {
			var manager = new ConfigManager("Current JEIU Config Test", disabledWatcher, disabledWatcher);
			var schema = new ConfigSchemaBuilder("jei", configFile, "jei.config.client", manager);
			schema.setLegacySources(legacyPaths);
			var configs = new ClientConfigs(schema, false, manager.createInMemorySortingConfig(Comparator.naturalOrder(), true));
			assertJeiuSettings(configs.getClientConfig());
		}
		assertTrue(Files.notExists(ConfigFileUtil.getBackupPath(configFile, 1)));
		for (Path legacyPath : legacyPaths) {
			assertEquals(legacyContent, Files.readString(legacyPath));
			assertTrue(Files.notExists(ConfigFileUtil.getBackupPath(legacyPath, 1)));
		}
	}

	private static void assertJeiuSettings(IClientConfig client) {
		assertFalse(client.quantityFieldEnabled().get());
		assertTrue(client.bookmarkOutputAsRecipe().get());
		assertEquals(BookmarkRecipeMarkerMode.BACKGROUND, client.bookmarkRecipeMarkerMode().get());
		assertEquals(87, client.bookmarkAmountFontScale().get());
		assertFalse(client.showRecipeHandlerIconEnabled().get());
		assertEquals(42, client.favoriteTreeDepth().get());
		assertTrue(client.copyFullComponentsEnabled().get());
		assertEquals(0xFF123456, CollapsibleColorConfig.getCollapsedColor().get());
		assertEquals(0x01234567, CollapsibleColorConfig.getExpandedColor().get());
		assertTrue(CollapsibleColorConfig.getShowGroupSize().get());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void preservesJeiuDefaultsForMissingAndInvalidSettings(boolean invalid, @TempDir Path tempDir) throws IOException {
		Path configFile = tempDir.resolve("jei-client.ini");
		if (invalid) {
			Files.writeString(configFile, """
				[ingredientList]
				quantityFieldEnabled = invalid
				[bookmarkList]
				bookmarkOutputAsRecipe = invalid
				recipeMarkerMode = invalid
				amountFontScale = 24
				showRecipeHandlerIcon = invalid
				favoriteTreeDepth = -1
				[input]
				copyFullComponents = invalid
				[collapsible]
				collapsedColor = 0x100000000
				expandedColor = invalid
				showGroupSize = invalid
				""");
		}
		var disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		var manager = new ConfigManager("JEIU Defaults Test", disabledWatcher, disabledWatcher);
		var schema = new ConfigSchemaBuilder("jei", configFile, "jei.config.client", manager);
		var client = new ClientConfigs(schema, false, manager.createInMemorySortingConfig(Comparator.naturalOrder(), true)).getClientConfig();
		assertTrue(client.quantityFieldEnabled().get());
		assertFalse(client.bookmarkOutputAsRecipe().get());
		assertEquals(BookmarkRecipeMarkerMode.NONE, client.bookmarkRecipeMarkerMode().get());
		assertEquals(65, client.bookmarkAmountFontScale().get());
		assertTrue(client.showRecipeHandlerIconEnabled().get());
		assertEquals(9, client.favoriteTreeDepth().get());
		assertFalse(client.copyFullComponentsEnabled().get());
		assertEquals(CollapsibleColorConfig.DEFAULT_COLOR, CollapsibleColorConfig.getCollapsedColor().get());
		assertEquals(CollapsibleColorConfig.DEFAULT_COLOR, CollapsibleColorConfig.getExpandedColor().get());
		assertFalse(CollapsibleColorConfig.getShowGroupSize().get());
	}

	@ParameterizedTest
	@CsvSource({"'', true", "true, true", "false, false"})
	public void loadsInventoryEffectCompactingSetting(String savedValue, boolean expected, @TempDir Path tempDir) throws IOException {
		Path configFile = tempDir.resolve("jei-client.ini");
		if (!savedValue.isEmpty()) {
			Files.writeString(configFile, "[ingredientList]\ncompactInventoryEffects = " + savedValue + "\n");
		}
		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("JEI Inventory Effects Test", disabledWatcher, disabledWatcher);
		ClientConfigs configs = new ClientConfigs(
			new ConfigSchemaBuilder("jei", configFile, "jei.config.client", configManager),
			false,
			configManager.createInMemorySortingConfig(Comparator.naturalOrder(), true)
		);

		assertEquals(expected, configs.getClientConfig().compactInventoryEffects().get());
	}

	@ParameterizedTest
	@CsvSource({"'', 2", "1, 1", "2, 2", "10, 10", "0, 2", "11, 2"})
	public void loadsRecipeColumnLimitWithDefaultAndRangeValidation(String savedColumns, int expectedColumns, @TempDir Path tempDir) throws IOException {
		Path configFile = tempDir.resolve("jei-client.ini");
		String content = "";
		if (!savedColumns.isEmpty()) {
			content = "[recipes]\nmaxRecipeGuiColumns = " + savedColumns + "\n";
		}
		Files.writeString(configFile, content);
		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("JEI Recipe Columns Test", disabledWatcher, disabledWatcher);
		ClientConfigs configs = new ClientConfigs(
			new ConfigSchemaBuilder("jei", configFile, "jei.config.client", configManager),
			false,
			configManager.createInMemorySortingConfig(Comparator.naturalOrder(), true)
		);

		assertEquals(expectedColumns, configs.getClientConfig().maxRecipeGuiColumns().get());
	}

	@ParameterizedTest
	@CsvSource({"0, 9", "5, 5"})
	public void loadsIndependentHistoryWidthFromExistingConfig(int savedColumns, int expectedColumns, @TempDir Path tempDir) throws IOException {
		Path configFile = tempDir.resolve("jei-client.ini");
		Files.writeString(configFile, """
			[lookups]
			maxColumns = %d
			maxRows = 3

			[ingredientList]
			maxColumns = 4
			""".formatted(savedColumns));
		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("JEI History Width Test", disabledWatcher, disabledWatcher);
		ClientConfigs configs = new ClientConfigs(
			new ConfigSchemaBuilder("jei", configFile, "jei.config.client", configManager),
			false,
			configManager.createInMemorySortingConfig(Comparator.naturalOrder(), true)
		);

		assertEquals(expectedColumns, configs.getClientConfig().maxLookupHistoryColumns().get());
		assertEquals(3, configs.getClientConfig().maxLookupHistoryRows().get());
		assertEquals(4, configs.getIngredientListConfig().maxColumns().get());
	}

	@ParameterizedTest
	@ValueSource(strings = {"resourceLocationSearchMode", "identifierSearchMode"})
	public void loadsEveryReleasedClientConfigValue(String identifierSearchKey, @TempDir Path tempDir) throws IOException {
		Path configDirectory = tempDir.resolve("jei");
		UUID profileId = UUID.randomUUID();
		Path rootLegacyFile = configDirectory.resolve("jei-client.ini");
		Path legacyFile = configDirectory.resolve("players").resolve(profileId.toString()).resolve("jei-client.ini");
		Path configFile = configDirectory.resolve("client").resolve("jei-client.ini");
		Files.createDirectories(legacyFile.getParent());
		Files.writeString(rootLegacyFile, "[appearance]\ncenterSearch = false\n");
		Files.writeString(legacyFile, """
			[appearance]
			centerSearch = true
			recipeGuiHeight = 411
			toastReflowEnabled = false

			[cheating]
			giveMode = INVENTORY
			cheatToHotbarUsingHotkeysEnabled = true
			showHiddenIngredients = true
			showTagRecipesEnabled = false

			[bookmarks]
			addBookmarksToFrontEnabled = true
			bookmarkOutputAsRecipe = false
			dragToRearrangeBookmarksEnabled = false

			[tooltips]
			bookmarkTooltipFeatures = INGREDIENTS
			holdShiftToShowBookmarkTooltipFeatures = false
			showCreativeTabNamesEnabled = true
			tagContentTooltipEnabled = false
			hideSingleTagContentTooltipEnabled = false
			enableRecipesGuiIngredientsSummary = true

			[performance]
			lowMemorySlowSearchEnabled = true

			[lookups]
			lookupFluidContentsEnabled = true
			lookupBlockTagsEnabled = false

			[lookupHistory]
			enabled = true
			maxRows = 6
			maxIngredients = 321
			displaySide = RIGHT

			[advanced]
			catchRenderErrorsEnabled = false
			recipeSyncWarningEnabled = false

			[input]
			dragDelayInMilliseconds = 234
			smoothScrollRate = 17
			recipeSlotCyclingEnabled = false

			[sorting]
			ingredientSortStages = ALPHABETICAL, MOD_NAME
			recipeSorterStages = CRAFTABLE

			[search]
			modNameSearchMode = ENABLED
			tagSearchMode = DISABLED
			tooltipSearchMode = REQUIRE_PREFIX
			colorSearchMode = ENABLED
			%s = REQUIRE_PREFIX
			creativeTabSearchMode = ENABLED
			searchAdvancedTooltips = true
			searchModIds = false
			searchModAliases = false
			searchShortModNames = true
			searchIngredientAliases = false

			[ingredientList]
			maxRows = 12
			maxColumns = 7
			horizontalAlignment = LEFT
			verticalAlignment = BOTTOM
			buttonNavigationVisibility = DISABLED
			drawBackground = true
			layoutMode = MAXIMIZE_AVAILABLE_SPACE
			navigationMode = SCROLLING

			[bookmarkList]
			maxRows = 13
			maxColumns = 6
			horizontalAlignment = RIGHT
			verticalAlignment = CENTER
			buttonNavigationVisibility = AUTO_HIDE
			drawBackground = true
			layoutMode = MAXIMIZE_AVAILABLE_SPACE
			navigationMode = SMOOTH_SCROLLING
			""".formatted(identifierSearchKey));

		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("JEI Config Migration Test", disabledWatcher, disabledWatcher);
		ISortingConfig<String> recipeSorting = configManager.createInMemorySortingConfig(Comparator.naturalOrder(), true);
		ConfigSchemaBuilder schemaBuilder = new ConfigSchemaBuilder("jei", configFile, "jei.config.client", configManager);
		schemaBuilder.setLegacySources(LegacyConfigPaths.get(configDirectory, profileId, "jei-client.ini"));
		ClientConfigs configs = new ClientConfigs(
			schemaBuilder,
			false,
			recipeSorting
		);

		assertTrue(configs.isLowMemorySearchEnabled());
		IClientConfig client = configs.getClientConfig();
		assertEquals(SearchBarPosition.CENTERED, client.searchBarPosition().get());
		assertEquals(411, client.maxRecipeGuiHeight().get());
		assertFalse(client.toastReflowEnabled().get());
		assertEquals(GiveMode.INVENTORY, client.giveMode().get());
		assertTrue(client.cheatToHotbarUsingHotkeysEnabled().get());
		assertTrue(client.showHiddenIngredients().get());
		assertFalse(client.showTagRecipesEnabled().get());
		assertEquals(BookmarkAddPosition.FRONT, client.bookmarkAddPosition().get());
		assertFalse(client.bookmarkOutputAsRecipe().get());
		assertFalse(client.dragToRearrangeBookmarksEnabled().get());
		assertFalse(client.bookmarkTooltipPreviewEnabled().get());
		assertTrue(client.bookmarkTooltipIngredientsEnabled().get());
		assertFalse(client.holdShiftToShowBookmarkTooltipFeaturesEnabled().get());
		assertTrue(client.showCreativeTabNamesEnabled().get());
		assertFalse(client.tagContentTooltipEnabled().get());
		assertFalse(client.hideSingleTagContentTooltipEnabled().get());
		assertTrue(client.ingredientsSummaryEnabled().get());
		assertTrue(client.lookupFluidContentsEnabled().get());
		assertFalse(client.lookupBlockTagsEnabled().get());
		assertTrue(client.lookupHistoryEnabled().get());
		assertEquals(6, client.maxLookupHistoryRows().get());
		assertEquals(321, client.maxLookupHistoryIngredients().get());
		assertEquals(HistoryDisplaySide.RIGHT, client.lookupHistoryDisplaySide().get());
		assertFalse(client.catchRenderErrorsEnabled().get());
		assertFalse(client.recipeSyncWarningEnabled().get());
		assertEquals(234, client.dragDelayMs().get());
		assertTrue(client.smoothScrollingEnabled().get());
		assertEquals(17, client.smoothScrollRate().get());
		assertFalse(client.recipeSlotCyclingEnabled().get());
		assertEquals(List.of(IngredientSortStage.ALPHABETICAL, IngredientSortStage.MOD_NAME), client.ingredientSorterStages().get());
		assertFalse(client.recipeSortingBookmarksEnabled().get());
		assertTrue(client.recipeSortingCraftableEnabled().get());

		IIngredientFilterConfig filter = configs.getIngredientFilterConfig();
		assertEquals(SearchMode.ENABLED, filter.modNameSearchMode().get());
		assertEquals(SearchMode.DISABLED, filter.tagSearchMode().get());
		assertEquals(SearchMode.REQUIRE_PREFIX, filter.tooltipSearchMode().get());
		assertEquals(SearchMode.ENABLED, filter.colorSearchMode().get());
		assertEquals(SearchMode.REQUIRE_PREFIX, filter.resourceLocationSearchMode().get());
		assertEquals(SearchMode.ENABLED, filter.creativeTabSearchMode().get());
		assertTrue(filter.searchAdvancedTooltips().get());
		assertFalse(filter.searchModIds().get());
		assertFalse(filter.searchModAliases().get());
		assertTrue(filter.searchShortModNames().get());
		assertFalse(filter.searchIngredientAliases().get());

		assertGridConfig(
			configs.getIngredientListConfig(),
			12,
			7,
			HorizontalAlignment.LEFT,
			VerticalAlignment.BOTTOM,
			NavigationVisibility.DISABLED,
			IngredientGridLayoutMode.MAXIMIZE_AVAILABLE_SPACE,
			IngredientGridNavigationMode.SCROLLING
		);
		assertGridConfig(
			configs.getBookmarkListConfig(),
			13,
			6,
			HorizontalAlignment.RIGHT,
			VerticalAlignment.CENTER,
			NavigationVisibility.DISABLED,
			IngredientGridLayoutMode.MAXIMIZE_AVAILABLE_SPACE,
			IngredientGridNavigationMode.SCROLLING
		);
		assertTrue(Files.exists(configFile));
		assertEquals(Files.readString(legacyFile), Files.readString(ConfigFileUtil.getBackupPath(legacyFile, 1)));
		assertTrue(Files.notExists(ConfigFileUtil.getBackupPath(rootLegacyFile, 1)));

		ConfigManager reloadedConfigManager = new ConfigManager("Reloaded JEI Client Config", disabledWatcher, disabledWatcher);
		ISortingConfig<String> reloadedRecipeSorting = reloadedConfigManager.createInMemorySortingConfig(Comparator.naturalOrder(), true);
		ClientConfigs reloaded = new ClientConfigs(
			new ConfigSchemaBuilder("jei", configFile, "jei.config.client", reloadedConfigManager),
			false,
			reloadedRecipeSorting
		);
		assertTrue(reloaded.isLowMemorySearchEnabled());
		assertFalse(reloaded.getClientConfig().bookmarkOutputAsRecipe().get());
		assertFalse(reloaded.getClientConfig().recipeSyncWarningEnabled().get());
		assertFalse(reloaded.getClientConfig().recipeSlotCyclingEnabled().get());
		assertEquals(IngredientGridLayoutMode.MAXIMIZE_AVAILABLE_SPACE, reloaded.getIngredientListConfig().layoutMode().get());
		assertEquals(IngredientGridNavigationMode.SCROLLING, reloaded.getBookmarkListConfig().navigationMode().get());
	}

	@ParameterizedTest
	@CsvSource({
		"true, BACKGROUND",
		"false, NONE",
		"NONE, NONE",
		"BACKGROUND, BACKGROUND",
		"BORDER_ONLY, BORDER_ONLY",
		"GRID, GRID",
		"invalid, NONE"
	})
	public void loadsBackgroundStylesAndLegacyBooleans(String savedValue, IngredientGridBackgroundStyle expected, @TempDir Path tempDir) throws IOException {
		Path configFile = tempDir.resolve("jei-client.ini");
		Files.writeString(configFile, "[lists]\ndrawBackground = " + savedValue + "\n");
		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("JEI Background Style Migration Test", disabledWatcher, disabledWatcher);
		ISortingConfig<String> recipeSorting = configManager.createInMemorySortingConfig(Comparator.naturalOrder(), true);
		ClientConfigs configs = new ClientConfigs(
			new ConfigSchemaBuilder("jei", configFile, "jei.config.client", configManager),
			false,
			recipeSorting
		);

		assertEquals(expected, configs.getIngredientListConfig().backgroundStyle().get());
		assertEquals(expected, configs.getBookmarkListConfig().backgroundStyle().get());
		assertEquals(
			Optional.of(List.of(IngredientGridBackgroundStyle.values())),
			configs.getIngredientListConfig().backgroundStyle().getEditorInfo().getSerializer().getAllValidValues()
		);
	}

	@Test
	public void migratesSmoothScrollingNavigationModeFromCurrentConfig(@TempDir Path tempDir) throws IOException {
		Path configFile = tempDir.resolve("jei-client.ini");
		Files.writeString(configFile, """
			[lists]
			navigationMode = SMOOTH_SCROLLING
			""");

		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("JEI Navigation Mode Migration Test", disabledWatcher, disabledWatcher);
		ISortingConfig<String> recipeSorting = configManager.createInMemorySortingConfig(Comparator.naturalOrder(), true);
		ClientConfigs configs = new ClientConfigs(
			new ConfigSchemaBuilder("jei", configFile, "jei.config.client", configManager),
			false,
			recipeSorting
		);

		assertEquals(
			IngredientGridNavigationMode.SCROLLING,
			configs.getIngredientListConfig().navigationMode().get()
		);
		assertEquals(
			Optional.of(List.of(IngredientGridNavigationMode.PAGED, IngredientGridNavigationMode.SCROLLING)),
			configs.getIngredientListConfig().navigationMode().getEditorInfo().getSerializer().getAllValidValues()
		);
	}

	@Test
	public void migratesValidLegacyValuesWhenAnotherValueIsInvalid(@TempDir Path tempDir) throws IOException {
		Path configDirectory = tempDir.resolve("jei");
		Path legacyFile = configDirectory.resolve("jei-client.ini");
		Path configFile = configDirectory.resolve("client").resolve("jei-client.ini");
		Files.createDirectories(configDirectory);
		Files.writeString(legacyFile, """
			[appearance]
			centerSearch = true
			recipeGuiHeight = invalid
			""");

		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("Partial JEI Config Migration Test", disabledWatcher, disabledWatcher);
		ISortingConfig<String> recipeSorting = configManager.createInMemorySortingConfig(Comparator.naturalOrder(), true);
		ConfigSchemaBuilder schemaBuilder = new ConfigSchemaBuilder("jei", configFile, "jei.config.client", configManager);
		schemaBuilder.setLegacySources(LegacyConfigPaths.get(configDirectory, UUID.randomUUID(), "jei-client.ini"));
		ClientConfigs configs = new ClientConfigs(
			schemaBuilder,
			false,
			recipeSorting
		);

		IClientConfig client = configs.getClientConfig();
		assertEquals(SearchBarPosition.CENTERED, client.searchBarPosition().get());
		assertEquals(IClientConfig.defaultRecipeGuiHeight, client.maxRecipeGuiHeight().get());
		assertTrue(Files.exists(configFile));
		assertEquals(Files.readString(legacyFile), Files.readString(ConfigFileUtil.getBackupPath(legacyFile, 1)));
	}

	private static void assertGridConfig(
		IIngredientGridConfig config,
		int maxRows,
		int maxColumns,
		HorizontalAlignment horizontalAlignment,
		VerticalAlignment verticalAlignment,
		NavigationVisibility navigationVisibility,
		IngredientGridLayoutMode layoutMode,
		IngredientGridNavigationMode navigationMode
	) {
		assertEquals(maxRows, config.maxRows().get());
		assertEquals(maxColumns, config.maxColumns().get());
		assertEquals(horizontalAlignment, config.horizontalAlignment().get());
		assertEquals(verticalAlignment, config.verticalAlignment().get());
		assertEquals(navigationVisibility, config.navigationVisibility().get());
		assertEquals(IngredientGridBackgroundStyle.BACKGROUND, config.backgroundStyle().get());
		assertEquals(layoutMode, config.layoutMode().get());
		assertEquals(navigationMode, config.navigationMode().get());
	}

	@Test
	public void migratesLegacyDebugConfigTransactionallyThroughMezzConfig(@TempDir Path tempDir) throws IOException {
		Path configDirectory = tempDir.resolve("jei");
		Path legacyFile = configDirectory.resolve("jei-debug.ini");
		Path configFile = configDirectory.resolve("client").resolve("jei-debug.ini");
		Files.createDirectories(configDirectory);
		Files.writeString(legacyFile, """
			[debug]
			debugMode = true
			debugGuis = true
			debugInputs = true
			debugInfoTooltipsEnabled = true
			logSuffixTreeStats = true
			""");

		ConfigFileWatcherSettings disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		ConfigManager configManager = new ConfigManager("JEI Debug Config Migration Test", disabledWatcher, disabledWatcher);
		ConfigSchemaBuilder schemaBuilder = new ConfigSchemaBuilder("jei", configFile, "jei.config.debug", configManager);
		DebugConfig.create(schemaBuilder);
		schemaBuilder.setLegacySources(LegacyConfigPaths.get(configDirectory, UUID.randomUUID(), "jei-debug.ini"));
		schemaBuilder.build();

		assertTrue(DebugConfig.isDebugIngredientsEnabled());
		assertTrue(DebugConfig.isDebugGuisEnabled());
		assertTrue(DebugConfig.isDebugInputsEnabled());
		assertTrue(DebugConfig.isDebugInfoTooltipsEnabled());
		assertTrue(DebugConfig.isLogSuffixTreeStatsEnabled());
		assertTrue(Files.exists(configFile));
		assertEquals(Files.readString(legacyFile), Files.readString(ConfigFileUtil.getBackupPath(legacyFile, 1)));
	}
}
