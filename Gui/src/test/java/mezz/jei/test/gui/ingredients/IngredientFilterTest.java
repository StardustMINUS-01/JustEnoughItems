package mezz.jei.test.gui.ingredients;

import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.common.Internal;
import mezz.jei.common.config.ClientConfigs;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IngredientSortStage;
import mezz.jei.common.config.SearchMode;
import mezz.jei.common.search.compact.CompactSearchStorageBuilder;
import mezz.jei.api.search.ISearchStorage;
import mezz.jei.api.search.ISearchStorageBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.recipes.filtering.RecipeSearchSession;
import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeCategory;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.ingredients.IIngredientSupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import mezz.jei.gui.config.IngredientTypeSortingConfig;
import mezz.jei.gui.filter.FilterTextSource;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.ingredients.ListElementInfo;
import mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchemaBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IngredientFilterTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void maintainsSortedResultsAndRecipeSearch(boolean customBackend, @TempDir Path directory) throws Exception {
		var disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		var configManager = new ConfigManager("Ingredient filter test", disabledWatcher, disabledWatcher);
		var configs = new ClientConfigs(new ConfigSchemaBuilder("jei", directory.resolve("client.ini"), "jei.config.client", configManager),
			false, configManager.createInMemorySortingConfig(Comparator.<String>naturalOrder(), true));
		var previousConfigs = Internal.getOptionalClientConfigs();
		Internal.setClientConfigs(configs);
		try {
			verifySortedResults(configs, configManager, customBackend);
		} finally {
			configs.onRuntimeStopped();
			Internal.setClientConfigs(previousConfigs.orElse(null));
		}
	}

	private static void verifySortedResults(ClientConfigs configs, ConfigManager configManager, boolean customBackend) throws Exception {
		var config = configs.getClientConfig();
		var stages = config.ingredientSorterStages();
		stages.set(List.of(IngredientSortStage.MOD_NAME));
		var searchConfig = configs.getIngredientFilterConfig();
		List.of(searchConfig.modNameSearchMode(), searchConfig.tagSearchMode(), searchConfig.tooltipSearchMode(),
			searchConfig.colorSearchMode(), searchConfig.resourceLocationSearchMode(), searchConfig.creativeTabSearchMode())
			.forEach(value -> value.set(SearchMode.DISABLED));
		var baseManager = ItemStackIngredientTestFixtures.ingredientManager();
		var baseHelper = ItemStackIngredientTestFixtures.itemHelper();
		IIngredientHelper<ItemStack> helper = proxy(IIngredientHelper.class, (p, m, a) -> switch (m.getName()) {
			case "getTagStream" -> java.util.stream.Stream.of(ResourceLocation.parse("c:rocks"));
			case "getUid" -> List.of(baseHelper.getUid((ItemStack) a[0], (UidContext) a[1]), ((ItemStack) a[0]).getComponents());
			default -> m.invoke(baseHelper, a);
		});
		IIngredientManager manager = proxy(IIngredientManager.class, (p, m, a) -> switch (m.getName()) {
			case "getIngredientHelper" -> helper;
			case "getIngredientAliases" -> ((ITypedIngredient<?>) a[0]).getItemStack().orElseThrow().is(Items.STONE) ? List.of("rockalias") : List.of();
			case "getRegisteredIngredientTypes" -> List.of(VanillaTypes.ITEM_STACK);
			default -> m.invoke(baseManager, a);
		});
		IModIdHelper mods = proxy(IModIdHelper.class, (p, m, a) -> m.getName().equals("getModAliases") ? Set.of("vanillalias") : "Minecraft Game");
		var hidden = new HashSet<Item>();
		IIngredientVisibility visibility = proxy(IIngredientVisibility.class, (p, m, a) -> !hidden.contains(((mezz.jei.api.ingredients.ITypedIngredient<?>) a[0]).getItemStack().orElseThrow().getItem()));
		var text = new FilterTextSource();
		AtomicInteger comparisons = new AtomicInteger();
		List<IListElementInfo<?>> infos = new ArrayList<>();
		infos.add(ListElementInfo.create(item(Items.STONE), manager, searchConfig, mods));
		infos.add(ListElementInfo.create(item(Items.DIRT), manager, searchConfig, mods));
		var filter = new IngredientFilter(text, config, searchConfig, manager, values -> {
			values.sort(Comparator.comparing(info -> info.getResourceLocation().toString()));
			if (stages.get().getFirst() != IngredientSortStage.MOD_NAME) {
				java.util.Collections.reverse(values);
			}
			for (int i = 0; i < values.size(); i++) {
				values.get(i).getElement().setSortedIndex(i);
			}
			return (a, b) -> {
				comparisons.incrementAndGet();
				return Integer.compare(a.getSortedIndex(), b.getSortedIndex());
			};
		}, infos, mods, visibility,
			new IngredientTypeSortingConfig(configManager.createInMemorySortingConfig(Comparator.<String>naturalOrder(), true)),
			null, customBackend ? AliasSearchStorageBuilder::new : CompactSearchStorageBuilder.FACTORY, proxy(IClientToggleState.class, (p, m, a) -> null));

		assertEquals(List.of(Items.DIRT, Items.STONE), items(filter));
		assertTrue(comparisons.get() > 0);
		text.setFilterText("stone");
		assertEquals(List.of(Items.STONE), items(filter));
		comparisons.set(0);
		text.setFilterText("");
		assertEquals(List.of(Items.DIRT, Items.STONE), items(filter));
		assertEquals(0, comparisons.get());
		hidden.add(Items.DIRT);
		filter.updateHidden();
		assertEquals(List.of(Items.STONE), items(filter));
		hidden.clear();
		filter.updateHidden();
		assertEquals(List.of(Items.DIRT, Items.STONE), items(filter));
		assertEquals(0, comparisons.get());
		filter.addIngredient(ListElementInfo.create(item(Items.APPLE), manager, searchConfig, mods));
		assertEquals(3, items(filter).size());
		assertEquals(Set.of(Items.DIRT, Items.STONE, Items.APPLE), Set.copyOf(items(filter)));
		assertTrue(comparisons.get() > 0);
		stages.set(List.of(IngredientSortStage.ALPHABETICAL));
		assertEquals(List.of(Items.STONE, Items.DIRT, Items.APPLE), items(filter));
		filter.rebuildItemFilter();
		assertEquals(List.of(Items.STONE, Items.DIRT, Items.APPLE), items(filter));
		verifyRecipeSnapshots(filter, text, configs, hidden, manager, customBackend);
	}

	private static void verifyRecipeSnapshots(IngredientFilter filter, FilterTextSource text, ClientConfigs configs,
		Set<Item> hidden, IIngredientManager manager, boolean customBackend) throws Exception {
		var config = configs.getIngredientFilterConfig();
		config.searchIngredientAliases().set(true);
		config.tagSearchMode().set(SearchMode.REQUIRE_PREFIX);
		config.modNameSearchMode().set(SearchMode.REQUIRE_PREFIX);
		config.resourceLocationSearchMode().set(SearchMode.REQUIRE_PREFIX);
		config.searchModAliases().set(true);
		List<String> tokens = List.of("stone", "rockalias", "shitou", "queryalias", "#rocks", "#c:rocks", "@vanillalias", "&minecraft:stone", "rocks", "$missing", "%missing", "^missing");
		var snapshot = filter.createRecipeSearchSnapshot(Set.copyOf(tokens));
		for (String token : tokens) {
			text.setFilterText(token);
			Set<Item> expected = Set.copyOf(items(filter));
			for (Item item : List.of(Items.STONE, Items.DIRT, Items.APPLE))
				assertEquals(expected.contains(item), snapshot.test(item(item), token), token);
		}
		assertTrue(snapshot.test(item(Items.STONE), "rockalias"));
		var narrowed = filter.createRecipeSearchSnapshot(Set.of("rockalias", "stone"), snapshot);
		assertTrue(narrowed.test(item(Items.STONE), "rockalias"));
		assertFalse(narrowed.test(item(Items.DIRT), "rockalias"));
		var changed = filter.createRecipeSearchSnapshot(Set.of("rockalias", "dirt"), narrowed);
		assertTrue(changed.test(item(Items.STONE), "rockalias"));
		assertTrue(changed.test(item(Items.DIRT), "dirt"));
		assertFalse(changed.test(item(Items.STONE), "dirt"));
		assertTrue(narrowed.test(item(Items.STONE), "stone"), "a later query must not mutate an earlier snapshot");
		assertEquals(customBackend, snapshot.test(item(Items.STONE), "shitou"));
		assertEquals(customBackend, snapshot.test(item(Items.STONE), "queryalias"), "custom Lucene subclass query overrides must not be bypassed");
		assertTrue(snapshot.test(item(Items.STONE), "#rocks"));
		assertFalse(snapshot.test(item(Items.STONE), "#c:rocks"));
		assertFalse(snapshot.test(item(Items.STONE), "rocks"));
		assertTrue(snapshot.test(item(Items.COAL), "#rocks"), "unregistered ingredients use the same field extraction");
		assertTrue(snapshot.test(item(Items.COAL), "@vanillalias"));
		ItemStack variant = new ItemStack(Items.STONE);
		variant.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Variant Stone"));
		var variantSnapshot = filter.createRecipeSearchSnapshot(Set.of("variant", "shitou"));
		assertTrue(variantSnapshot.test(ItemStackIngredientTestFixtures.typed(variant), "variant"));
		assertFalse(variantSnapshot.test(item(Items.STONE), "variant"));
		assertEquals(customBackend, variantSnapshot.test(ItemStackIngredientTestFixtures.typed(variant), "shitou"));
		assertTrue(java.util.stream.IntStream.range(0, 200).parallel().allMatch(i -> snapshot.test(item(Items.STONE), "rockalias")));
		hidden.add(Items.STONE);
		filter.updateHidden();
		text.setFilterText("stone");
		assertTrue(items(filter).isEmpty());
		assertTrue(filter.createRecipeSearchSnapshot(Set.of("stone")).test(item(Items.STONE), "stone"));
		hidden.clear();
		filter.updateHidden();
		long revision = filter.getIngredientSearchRevision();
		config.searchIngredientAliases().set(false);
		config.tagSearchMode().set(SearchMode.DISABLED);
		var updated = filter.createRecipeSearchSnapshot(Set.of("rockalias", "#rocks"), snapshot);
		assertTrue(filter.getIngredientSearchRevision() > revision);
		assertFalse(updated.test(item(Items.STONE), "rockalias"));
		assertFalse(updated.test(item(Items.COAL), "#rocks"));
		assertTrue(snapshot.test(item(Items.STONE), "rockalias"), "published indexed results are immutable");
		var beforeAdd = filter.createRecipeSearchSnapshot(Set.of("coal"), updated);
		filter.addIngredient(ListElementInfo.create(item(Items.COAL), manager, config,
			proxy(IModIdHelper.class, (p, m, a) -> m.getName().equals("getModAliases") ? Set.of() : "Minecraft Game")));
		var afterAdd = filter.createRecipeSearchSnapshot(Set.of("coal"), beforeAdd);
		assertTrue(afterAdd.test(item(Items.COAL), "coal"));
		assertFalse(afterAdd.test(item(Items.STONE), "coal"));
		config.searchIngredientAliases().set(true);
		var aliasesEnabled = filter.createRecipeSearchSnapshot(Set.of("rockalias"), updated);
		assertTrue(aliasesEnabled.test(item(Items.STONE), "rockalias"));
		filter.rebuildItemFilter();
		var rebuilt = filter.createRecipeSearchSnapshot(Set.of("rockalias"), aliasesEnabled);
		assertTrue(rebuilt.test(item(Items.STONE), "rockalias"));
		verifyRecipeSession(filter, manager);
	}

	private static void verifyRecipeSession(IngredientFilter filter, IIngredientManager manager) throws Exception {
		var category = new TestRecipeCategory(RecipeType.create("test", "processing", String.class), ResourceLocation.parse("test:recipe"));
		ILookupState state = proxy(ILookupState.class, (p, m, a) -> switch (m.getName()) {
			case "getRecipeCategories" -> List.of(category);
			case "getFocusedRecipes" -> new StaticFocusedRecipes<>(category, List.of("recipe"));
			default -> throw new AssertionError(m);
		});
		IRecipeManager recipes = proxy(IRecipeManager.class, (p, m, a) -> {
			if (m.getName().equals("getRecipeIngredients"))
				return (IIngredientSupplier) role -> List.of(item(Items.STONE));
			throw new AssertionError(m);
		});
		RecipeSearchSession session = new RecipeSearchSession(recipes, CompactSearchStorageBuilder.FACTORY);
		session.setIngredientSearch(filter);
		try {
			var query = RecipeSearchQuery.parse("i:stone");
			session.request(state, RecipeFilterMode.ALL, query);
			RecipeSearchSession.Result result = null;
			long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
			while (session.isSearching() && System.nanoTime() < deadline) {
				result = session.tick(RecipePreferenceRules.EMPTY).orElse(result);
				Thread.sleep(1);
			}
			assertFalse(session.isSearching());
			assertEquals(List.of("recipe"), result.recipes().getFirst().getRecipes());
			assertTrue(query.matchesInputCandidate(item(Items.STONE), result.ingredientMatcher()));
			assertFalse(query.matchesInputCandidate(item(Items.DIRT), result.ingredientMatcher()));
			assertFalse(session.needsIngredientRefresh());
			filter.onIngredientsUpdated(manager.getIngredientHelper(VanillaTypes.ITEM_STACK), List.of(item(Items.STONE)));
			assertTrue(session.needsIngredientRefresh());
		} finally {
			session.clear();
		}
	}

	private static final class AliasSearchStorageBuilder<T> implements ISearchStorageBuilder<T> {
		private final ISearchStorage<T> backing = new CompactSearchStorageBuilder<T>().build();
		private final ISearchStorage<T> storage = new ISearchStorage<>() {
			@Override
			public void getSearchResults(String token, java.util.function.Consumer<java.util.Collection<T>> consumer) {
				backing.getSearchResults(token.equals("queryalias") ? "shitou" : token, consumer);
			}
			@Override
			public void getAllElements(java.util.function.Consumer<java.util.Collection<T>> consumer) {
				backing.getAllElements(consumer);
			}
			@Override
			public void put(String key, T value) {
				backing.put(key, value);
			}
			@Override
			public String statistics() {
				return backing.statistics();
			}

		};

		@Override
		public void put(String key, T value) {
			storage.put(key, value);
			if (key.contains("stone"))
				storage.put("shitou", value);
		}

		@Override
		public ISearchStorage<T> build() {
			return storage;
		}
	}

	private static List<Item> items(IngredientFilter filter) {
		return filter.getElements().stream().map(value -> value.getTypedIngredient().getItemStack().orElseThrow().getItem()).toList();
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
