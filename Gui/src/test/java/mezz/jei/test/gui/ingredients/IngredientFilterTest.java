package mezz.jei.test.gui.ingredients;

import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.search.ISearchStorageBuilder;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IngredientFilterConfig;
import mezz.jei.common.config.IngredientSortStage;
import mezz.jei.common.config.file.ConfigSchemaBuilder;
import mezz.jei.common.config.file.serializers.ListSerializer;
import mezz.jei.common.config.file.serializers.EnumSerializer;
import mezz.jei.common.search.GeneralizedSuffixTreeSearchStorage;
import mezz.jei.common.search.SearchMode;
import mezz.jei.common.search.SearchStorageBuilderAdapter;
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

class IngredientFilterTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void maintainsSortedResults(boolean lowMemory) {
		var builder = new ConfigSchemaBuilder(Path.of("unused.ini"), "test");
		var category = builder.addCategory("client");
		var lowMemoryValue = category.addBoolean("lowMemory", lowMemory);
		var stages = category.addList("stages", List.of(IngredientSortStage.MOD_NAME),
			new ListSerializer<>(new EnumSerializer<>(IngredientSortStage.class)));
		IClientConfig config = proxy(IClientConfig.class, (p, m, a) -> switch (m.getName()) {
			case "lowMemorySlowSearchEnabled" -> lowMemoryValue;
			case "ingredientSorterStages" -> stages;
			default -> throw new AssertionError(m.getName());
		});
		var searchConfig = new IngredientFilterConfig(builder);
		List.of(searchConfig.modNameSearchMode(), searchConfig.tagSearchMode(), searchConfig.tooltipSearchMode(),
			searchConfig.colorSearchMode(), searchConfig.resourceLocationSearchMode(), searchConfig.creativeTabSearchMode())
			.forEach(value -> value.set(SearchMode.DISABLED));
		var baseManager = ItemStackIngredientTestFixtures.ingredientManager();
		IIngredientManager manager = proxy(IIngredientManager.class, (p, m, a) -> m.getName().equals("getIngredientAliases") ? List.of() : m.invoke(baseManager, a));
		IModIdHelper mods = proxy(IModIdHelper.class, (p, m, a) -> a[0]);
		var hidden = new HashSet<Item>();
		IIngredientVisibility visibility = proxy(IIngredientVisibility.class, (p, m, a) -> !hidden.contains(((mezz.jei.api.ingredients.ITypedIngredient<?>) a[0]).getItemStack().orElseThrow().getItem()));
		var text = new FilterTextSource();
		AtomicInteger comparisons = new AtomicInteger();
		List<IListElementInfo<?>> infos = new ArrayList<>();
		infos.add(ListElementInfo.create(item(Items.STONE), manager, mods));
		infos.add(ListElementInfo.create(item(Items.DIRT), manager, mods));
		var filter = new IngredientFilter(text, config, searchConfig, manager, values -> {
			values.sort(Comparator.comparing(info -> info.getResourceLocation().toString()));
			if (stages.getValue().getFirst() != IngredientSortStage.MOD_NAME) {
				java.util.Collections.reverse(values);
			}
			for (int i = 0; i < values.size(); i++) {
				values.get(i).getElement().setSortedIndex(i);
			}
			return (a, b) -> {
				comparisons.incrementAndGet();
				return Integer.compare(a.getSortedIndex(), b.getSortedIndex());
			};
		}, infos, mods, visibility, null, new ISearchStorageBuilderFactory() {
			@Override
			public <T> ISearchStorageBuilder<T> create() {
				return new SearchStorageBuilderAdapter<>(new GeneralizedSuffixTreeSearchStorage<>());
			}
		}, proxy(IClientToggleState.class, (p, m, a) -> null));

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
		filter.addIngredient(ListElementInfo.create(item(Items.APPLE), manager, mods));
		assertEquals(3, items(filter).size());
		assertEquals(Set.of(Items.DIRT, Items.STONE, Items.APPLE), Set.copyOf(items(filter)));
		assertTrue(comparisons.get() > 0);
		stages.set(List.of(IngredientSortStage.ALPHABETICAL));
		assertEquals(List.of(Items.STONE, Items.DIRT, Items.APPLE), items(filter));
		filter.rebuildItemFilter();
		assertEquals(List.of(Items.STONE, Items.DIRT, Items.APPLE), items(filter));
	}

	private static List<Item> items(IngredientFilter filter) {
		return filter.getElements().stream().map(value -> value.getTypedIngredient().getItemStack().orElseThrow().getItem()).toList();
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
