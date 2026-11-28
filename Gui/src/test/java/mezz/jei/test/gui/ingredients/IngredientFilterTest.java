package mezz.jei.test.gui.ingredients;

import it.unimi.dsi.fastutil.ints.IntArrays;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeCatalystLookup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.recipes.IRecipeMaterialCacheInternal;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.common.ingredients.itemStacks.TypedItemStack;
import mezz.jei.common.Internal;
import mezz.jei.common.config.ClientConfigs;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IngredientSortStage;
import mezz.jei.common.config.SearchMode;
import mezz.jei.common.search.compact.CompactSearchStorageBuilder;
import mezz.jei.common.search.compact.PinyinOption;
import mezz.jei.api.search.ISearchStorage;
import mezz.jei.api.search.ISearchStorageBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.gui.search.DynamicRecipeMaterialCache;
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
import java.util.concurrent.CancellationException;
import java.util.stream.IntStream;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
		var uidCalls = new AtomicInteger();
		IIngredientHelper<ItemStack> helper = proxy(IIngredientHelper.class, (p, m, a) -> switch (m.getName()) {
			case "getTagStream" -> java.util.stream.Stream.of(ResourceLocation.parse("c:rocks"));
			case "getUid" -> {
				uidCalls.incrementAndGet();
				yield List.of(baseHelper.getUid((ItemStack) a[0], (UidContext) a[1]), ((ItemStack) a[0]).getComponents());
			}
			default -> m.invoke(baseHelper, a);
		});
		IIngredientManager manager = proxy(IIngredientManager.class, (p, m, a) -> switch (m.getName()) {
			case "getIngredientHelper" -> helper;
			case "getIngredientAliases" -> ((ITypedIngredient<?>) a[0]).getItemStack().orElseThrow().is(Items.STONE) ? List.of("rockalias", "铸件") : List.of();
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
			null, customBackend ? AliasSearchStorageBuilder::new : new CompactSearchStorageBuilder.Factory(configs.getNativeSearchConfig()::pinyinFlags), proxy(IClientToggleState.class, (p, m, a) -> null));

		assertEquals(List.of(Items.DIRT, Items.STONE), items(filter));
		assertTrue(comparisons.get() > 0);
		text.setFilterText("stone | dirt");
		assertEquals(List.of(Items.DIRT, Items.STONE), items(filter));
		text.setFilterText("sto one -dirt");
		assertEquals(List.of(Items.STONE), items(filter));
		text.setFilterText("dirt stone");
		assertTrue(items(filter).isEmpty());
		text.setFilterText("-stone");
		assertEquals(List.of(Items.DIRT), items(filter));
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
		if (!customBackend)
			verifyDynamicMaterials(filter, uidCalls);
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
		assertEquals(customBackend, snapshot.test(item(Items.STONE), "queryalias"), "custom storage query overrides must not be bypassed");
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
		var options = configs.getNativeSearchConfig().pinyinOptions();
		text.setFilterText("zu");
		var before = filter.createRecipeSearchSnapshot(Set.of("zu"));
		assertTrue(items(filter).isEmpty());
		options.get(PinyinOption.ENABLED).set(true);
		options.get(PinyinOption.Z_ZH).set(true);
		assertEquals(customBackend ? List.of() : List.of(Items.STONE), items(filter));
		var during = filter.createRecipeSearchSnapshot(Set.of("zu"), before);
		assertEquals(!customBackend, during.test(item(Items.STONE), "zu"));
		assertFalse(before.test(item(Items.STONE), "zu"));
		options.get(PinyinOption.Z_ZH).set(false);
		assertTrue(items(filter).isEmpty());
		assertEquals(!customBackend, during.test(item(Items.STONE), "zu"));
		verifyRecipeSession(filter, manager);
		if (!customBackend) {
			verifyOrphanSearch(filter, manager, configs);
			verifyIndexedRecipes(filter, true);
			verifyIndexedRecipes(filter, false);
			verifySharedRecipeIndex(filter);
			verifyDeferredCapture(filter);
		}
	}

	private static void verifyDynamicMaterials(IngredientFilter filter, AtomicInteger uidCalls) {
		var query = filter.createRecipeSearchSnapshot(Set.of("missing", "stone", "dynamic"));
		var corpus = new mezz.jei.gui.search.OrphanIngredientCorpus(query);
		var cache = new DynamicRecipeMaterialCache(corpus, 3);
		var stack = new ItemStack(Items.STONE);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Dynamic variant"));
		var variant = ItemStackIngredientTestFixtures.typed(stack);
		var stone = item(Items.STONE);
		List<ITypedIngredient<?>> repeated = List.of(stone, variant, stone, variant, stone, variant);
		assertFalse(cache.matches(repeated, "missing", query));
		int compiled = uidCalls.get();
		assertTrue(cache.matches(repeated, "stone", query));
		assertTrue(cache.matches(repeated, "dynamic", query));
		assertEquals(compiled, uidCalls.get(), "the budget counts distinct IDs, reused across search terms");
		List<ITypedIngredient<?>> oversized = List.of(stone, item(Items.DIRT), variant);
		assertTrue(cache.matches(oversized, "dynamic", query));
		int uncached = uidCalls.get();
		assertFalse(cache.matches(oversized, "missing", query));
		assertTrue(uidCalls.get() > uncached, "an oversized role must not stay in the cache");
		assertEquals(corpus.firstId(), corpus.resolve(variant), "dynamic matching must not consume a permanent orphan ID");
	}

	private static void verifyOrphanSearch(IngredientFilter filter, IIngredientManager manager, ClientConfigs configs) {
		var corpus = new mezz.jei.gui.search.OrphanIngredientCorpus(filter.createRecipeSearchSnapshot(Set.of()));
		var values = new ArrayList<ITypedIngredient<ItemStack>>();
		for (int i = 0; i < 530; i++) {
			var stack = new ItemStack(Items.STONE);
			stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("铁铸件 " + i));
			var value = ItemStackIngredientTestFixtures.typed(stack);
			values.add(value);
			assertEquals(corpus.firstId() + i, corpus.resolve(value));
			assertEquals(corpus.firstId() + i, corpus.resolve(ItemStackIngredientTestFixtures.typed(stack.copy())));
		}
		var flags = configs.getNativeSearchConfig().pinyinOptions();
		flags.get(PinyinOption.Z_ZH).set(true);
		var partial = filter.createRecipeSearchSnapshot(Set.of("525"));
		int last = corpus.firstId() + values.size() - 1;
		assertThrows(CancellationException.class, () -> corpus.words("525", partial, last, () -> { throw new CancellationException(); }));
		assertNull(corpus.cachedWords("525", partial, last));
		partial.useRecipeCorpus(corpus);
		var extra = new ItemStack(Items.STONE);
		extra.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Extra 525"));
		var dynamic = ItemStackIngredientTestFixtures.typed(extra);
		assertTrue(IntStream.range(0, values.size()).parallel().allMatch(i -> partial.test(values.get(i), "525") == (i == 525) && partial.test(dynamic, "525")));
		for (String token : List.of("铁", "tie", "zu", "525", "missing", "rockalias", "@vanillalias")) {
			var query = filter.createRecipeSearchSnapshot(Set.of(token));
			var words = corpus.words(token, query, corpus.firstId() + values.size() - 1, () -> {});
			for (int i : List.of(0, 256, 511, 512, 525, 529)) {
				var id = corpus.firstId() + i;
				assertEquals(query.test(values.get(i), token), (words[id >>> 6] & (1L << id)) != 0, token);
				assertEquals(query.test(values.get(i), token), corpus.test(id, token, query), token);
			}
		}
		configs.getIngredientFilterConfig().tagSearchMode().set(SearchMode.REQUIRE_PREFIX);
		var tags = filter.createRecipeSearchSnapshot(Set.of("#rocks"));
		tags.useRecipeCorpus(corpus);
		assertTrue(corpus.test(corpus.firstId() + 511, "#rocks", tags));
		assertTrue(tags.test(values.get(511), "#rocks"));
		var tagWords = corpus.words("#rocks", tags, last, () -> {});
		assertTrue((tagWords[(corpus.firstId() + 511) >>> 6] & Long.MIN_VALUE) != 0);
		var info = ListElementInfo.create(values.getFirst(), manager, configs.getIngredientFilterConfig(),
			proxy(IModIdHelper.class, (p, m, a) -> m.getName().equals("getModAliases") ? Set.of() : "Minecraft Game"));
		long generation = filter.getRecipeIndexGeneration();
		filter.addIngredient(info);
		assertEquals(generation, filter.getRecipeIndexGeneration());
		var promoted = filter.createRecipeSearchSnapshot(Set.of("铸件"));
		var promotedWords = corpus.words("铸件", promoted, corpus.firstId(), () -> {});
		assertTrue((promotedWords[corpus.firstId() >>> 6] & (1L << corpus.firstId())) != 0);
		flags.get(PinyinOption.Z_ZH).set(false);
	}

	private static void verifyIndexedRecipes(IngredientFilter filter, boolean smallCategory) throws Exception {
		var category = new TestRecipeCategory(RecipeType.create("test", "processing", Integer.class), ResourceLocation.parse("test:recipe"));
		var visible = new ArrayList<>(List.of(3, 1, 2, 0));
		var visibility = new AtomicInteger();
		var state = proxy(ILookupState.class, (p, m, a) -> switch (m.getName()) {
			case "getRecipeCategories" -> List.of(category);
			case "getFocusedRecipes" -> new StaticFocusedRecipes<>(category, new ArrayList<Object>(visible));
			default -> throw new AssertionError(m);
		});
		ItemStack special = new ItemStack(Items.STONE);
		special.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Special Variant"));
		var orphan = TypedItemStack.create(special);
		var revision = new AtomicInteger();
		var smallBuilds = new AtomicInteger();
		Thread owner = Thread.currentThread();
		var inputs = new ArrayList<ITypedIngredient<?>>(List.of(item(Items.DIRT), orphan, TypedItemStack.create(Items.STONE), orphan, TypedItemStack.create(new ItemStack(Items.STONE, 2))));
		var materials = new ArrayList<IRecipeMaterialCacheInternal.RegisteredMaterials>();
		for (int i = 0; i < inputs.size(); i++) {
			final int row = i;
			materials.add(new IRecipeMaterialCacheInternal.RegisteredMaterials() {
				@Override
				public int[] wideSlots(RecipeIngredientRole role) { return IntArrays.EMPTY_ARRAY; }
				@Override
				public List<ITypedIngredient<?>> getIngredients(RecipeIngredientRole role) {
					return role == RecipeIngredientRole.INPUT ? List.of(inputs.get(row)) : List.of();
				}
			});
		}
		var manager = (IRecipeManager) Proxy.newProxyInstance(IRecipeManager.class.getClassLoader(),
			new Class<?>[]{IRecipeManager.class, IRecipeMaterialCacheInternal.class}, (p, m, a) -> switch (m.getName()) {
				case "getRecipeMaterialRevision" -> (long) revision.get();
				case "getRecipeVisibilityRevision" -> (long) visibility.get();
				case "getRegisteredCategories" -> List.of(category);
				case "getRegisteredRecipes" -> {
					if ((int) a[1] == 0) {
						assertTrue(smallCategory, "a small visible subset must not build a large category");
						assertFalse(Thread.currentThread() == owner);
						smallBuilds.incrementAndGet();
						yield List.of(0, 2, 3, 4);
					}
					yield List.of();
				}
				case "getRegisteredRecipeCount" -> smallCategory ? 4 : 38_470;
				case "getRegisteredRecipeMaterials" -> (int) a[1] == 1 ? null : materials.get((int) a[1]);
				case "getCachedDynamicMaterials" -> null;
				case "getRecipeIngredients" -> materials.get((int) a[1]);
				default -> throw new AssertionError(m);
			});
		var session = new RecipeSearchSession(manager, CompactSearchStorageBuilder.FACTORY);
		session.setIngredientSearch(filter);
		try {
			assertEquals(List.of(3, 1), runIndexedQuery(session, state, "i:spec"));
			assertEquals(smallCategory ? 1 : 0, smallBuilds.get());
			inputs.set(1, item(Items.STONE));
			assertEquals(List.of(3), runIndexedQuery(session, state, "i:special"));
			inputs.set(1, orphan);
			assertEquals(List.of(3, 1), runIndexedQuery(session, state, "i:spec"));
			visible.remove(Integer.valueOf(3));
			visible.addFirst(4);
			visibility.incrementAndGet();
			assertTrue(session.needsIngredientRefresh());
			assertEquals(List.of(1), runIndexedQuery(session, state, "i:spec"));
			assertEquals(List.of(4, 2), runIndexedQuery(session, state, "i:stone"));
			inputs.set(0, item(Items.STONE));
			revision.incrementAndGet();
			assertTrue(session.needsIngredientRefresh());
			assertEquals(List.of(4, 2, 0), runIndexedQuery(session, state, "i:stone"));
			assertEquals(List.of(4, 1, 2, 0), runIndexedQuery(session, state, "r:test:recipe"));
		} finally {
			session.clear();
		}
	}

	private static void verifyDeferredCapture(IngredientFilter filter) {
		var first = new TestRecipeCategory(RecipeType.create("test", "first", Integer.class), ResourceLocation.parse("test:first"));
		var second = new TestRecipeCategory(RecipeType.create("test", "second", Integer.class), ResourceLocation.parse("test:second"));
		var seen = new ArrayList<>();
		var owner = Thread.currentThread();
		var state = proxy(ILookupState.class, (p, m, a) -> switch (m.getName()) {
			case "getRecipeCategories" -> List.of(first, second);
			case "getFocusedRecipes" -> {
				assertEquals(owner, Thread.currentThread());
				seen.add(a[0]);
				Thread.sleep(15);
				yield new StaticFocusedRecipes<>((TestRecipeCategory) a[0], List.of(1));
			}
			default -> throw new AssertionError(m);
		});
		var manager = proxy(IRecipeManager.class, (p, m, a) -> { throw new AssertionError(m); });
		var session = new RecipeSearchSession(manager, CompactSearchStorageBuilder.FACTORY);
		session.setIngredientSearch(filter);
		try {
			session.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:test"), RecipePreferenceRules.EMPTY, second);
			assertEquals(List.of(second), seen);
			session.cancel();
			assertTrue(session.tick(RecipePreferenceRules.EMPTY).isEmpty());
			assertEquals(List.of(second), seen);
		} finally {
			session.clear();
		}
	}

	private static void verifySharedRecipeIndex(IngredientFilter filter) throws Exception {
		var type = RecipeType.create("test", "processing", Integer.class);
		var name = ResourceLocation.parse("test:smelting");
		var builds = new AtomicInteger();
		var paths = new AtomicInteger();
		var handled = new AtomicInteger();
		var unhandledRow = new AtomicInteger(-1);
		var workers = java.util.concurrent.ConcurrentHashMap.<Thread>newKeySet();
		@SuppressWarnings("unchecked")
		IRecipeCategory<Integer> category = proxy(IRecipeCategory.class, (p, m, a) -> switch (m.getName()) {
			case "getRecipeType" -> type;
			case "getTitle" -> net.minecraft.network.chat.Component.literal("Processing");
			case "isHandled" -> {
				handled.incrementAndGet();
				yield (int) a[0] != unhandledRow.get();
			}
			case "getRegistryName" -> name;
			case "getSourceRecipeIds" -> {
				paths.incrementAndGet();
				yield List.of();
			}
			default -> throw new AssertionError(m);
		});
		var rows = java.util.stream.IntStream.range(0, 1512).boxed().toList();
		var stone = List.<ITypedIngredient<?>>of(item(Items.STONE));
		var dirt = List.<ITypedIngredient<?>>of(item(Items.DIRT));
		var ordinary = registeredMaterials(stone, stone);
		var distinct = registeredMaterials(stone, dirt);
		var catalysts = proxy(IRecipeCatalystLookup.class, (p, m, a) -> java.util.stream.Stream.empty());
		var manager = (IRecipeManager) Proxy.newProxyInstance(IRecipeManager.class.getClassLoader(),
			new Class<?>[]{IRecipeManager.class, IRecipeMaterialCacheInternal.class}, (p, m, a) -> switch (m.getName()) {
				case "getRecipeVisibilityRevision", "getRecipeMaterialRevision" -> 0L;
				case "getRegisteredCategories" -> List.of(category);
				case "getRegisteredRecipeCount" -> 512;
				case "getRegisteredRecipes" -> {
					builds.incrementAndGet();
					yield rows.subList(0, 512);
				}
				case "getRegisteredRecipeMaterials" -> (int) a[1] == 0 ? distinct : ordinary;
				case "createRecipeCatalystLookup" -> catalysts;
				case "getCachedDynamicMaterials" -> null;
				case "getRecipeIngredients" -> {
					assertTrue((int) a[1] >= 512);
					workers.add(Thread.currentThread());
					java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
					yield ordinary;
				}
				default -> throw new AssertionError(m);
			});
		var session = new RecipeSearchSession(manager, CompactSearchStorageBuilder.FACTORY);
		session.setIngredientSearch(filter);
		var captures = new AtomicInteger();
		java.util.function.Function<List<Integer>, ILookupState> page = visible -> proxy(ILookupState.class, (p, m, a) -> switch (m.getName()) {
			case "getRecipeCategories" -> List.of(category);
			case "getFocusedRecipes" -> {
				captures.incrementAndGet();
				yield new StaticFocusedRecipes<>(category, visible);
			}
			default -> throw new AssertionError(m);
		});
		try {
			var full = page.apply(rows.subList(0, 512));
			session.request(full, RecipeFilterMode.ALL, RecipeSearchQuery.parse(""), RecipePreferenceRules.EMPTY, category);
			assertEquals(List.of(0), runIndexedQuery(session, full, "i:stone o:dirt"));
			assertEquals(1, handled.get(), "only the surviving intersection needs a material check");
			assertEquals(rows.subList(0, 512), runIndexedQuery(session, full, "smelt"));
			for (int i = 0; i < 5; i++) {
				session.resetView();
				var visible = List.of(rows.get(400 + i), rows.get(0), rows.get(400 + i));
				assertEquals(visible, runIndexedQuery(session, page.apply(visible), "smelt"));
			}
			assertEquals(1, builds.get());
			assertEquals(512, paths.get(), "all five views share the registered resource paths");
			unhandledRow.set(0);
			var mixed = page.apply(rows.subList(0, 2));
			assertEquals(List.of(0), runIndexedQuery(session, mixed, "-i:stone"));
			assertEquals(List.of(0, 1), runIndexedQuery(session, mixed, "i:stone | r:smelting"));
			unhandledRow.set(-1);
			var typing = page.apply(rows.subList(100, 112));
			int before = captures.get();
			assertTrue(runIndexedQuery(session, typing, "c:absent").isEmpty());
			assertEquals(before, captures.get(), "prune catalyst-only misses before loading plugin recipes");
			for (String query : List.of("i", "ir", "iro", "iron"))
				runIndexedQuery(session, typing, query);
			assertEquals(before + 1, captures.get());
			var dynamic = rows.subList(512, 1512);
			assertEquals(dynamic, runIndexedQuery(session, page.apply(dynamic), "i:stone"));
			if (Runtime.getRuntime().availableProcessors() >= 3)
				assertTrue(workers.size() > 1, "one category's dynamic rows must use multiple workers");
		} finally {
			session.clear();
		}
	}

	private static IRecipeMaterialCacheInternal.RegisteredMaterials registeredMaterials(
		List<ITypedIngredient<?>> inputs, List<ITypedIngredient<?>> outputs
	) {
		return new IRecipeMaterialCacheInternal.RegisteredMaterials() {
			@Override
			public int[] wideSlots(RecipeIngredientRole role) {
				return IntArrays.EMPTY_ARRAY;
			}
			@Override
			public List<ITypedIngredient<?>> getIngredients(RecipeIngredientRole role) {
				return switch (role) {
					case INPUT -> inputs;
					case OUTPUT -> outputs;
					default -> List.of();
				};
			}
		};
	}

	private static List<?> runIndexedQuery(RecipeSearchSession session, ILookupState state, String query) throws InterruptedException {
		var result = session.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse(query), RecipePreferenceRules.EMPTY);
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
		while (session.isSearching() && System.nanoTime() < deadline) {
			result = session.tick(RecipePreferenceRules.EMPTY);
			if (session.isSearching())
				Thread.sleep(1);
		}
		assertFalse(session.isSearching());
		return result.orElseThrow().recipes().stream().flatMap(focused -> focused.getRecipes().stream()).toList();
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
