package mezz.jei.test.gui.recipes.filtering;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.common.search.BakedSubstringIndexBuilder;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.filtering.RecipeSearchIngredient;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeCategory;

import java.util.List;
import java.util.Set;

public class RecipeSearchSessionTest {
	private static final ResourceLocation TYPE_UID = ResourceLocation.parse("test:macerating");
	private static final TestRecipeCategory CATEGORY = new TestRecipeCategory(RecipeType.create("test", "macerating", String.class), TYPE_UID);

	@Test
	public void matchesSearchAliases() throws Exception {
		RecipeSearchIngredient ingredient = new RecipeSearchIngredient(
			"至高木板",
			ResourceLocation.parse("test:supreme_planks"),
			"test",
			Set.of("c:planks")
		);
		RecipeSearchQuery query = RecipeSearchQuery.parse("i:zhigaomuban");
		@SuppressWarnings("unchecked")
		mezz.jei.api.recipe.category.IRecipeCategory<Object> category = proxy(mezz.jei.api.recipe.category.IRecipeCategory.class, (method, args) -> switch (method) {
			case "getTitle" -> net.minecraft.network.chat.Component.literal("至高木板");
			case "getRecipeType" -> CATEGORY.getRecipeType();
			case "getRegistryName" -> TYPE_UID;
			default -> throw new AssertionError(method);
		});
		var state = proxy(mezz.jei.gui.recipes.lookups.ILookupState.class, (method, args) -> switch (method) {
			case "getRecipeCategories" -> List.of(category);
			case "getFocusedRecipes" -> new mezz.jei.gui.recipes.lookups.StaticFocusedRecipes<>(category, List.of("planks"));
			default -> throw new AssertionError(method);
		});
		var session = new mezz.jei.gui.recipes.filtering.RecipeSearchSession(null, null, AliasSearchStorageBuilder::new);
		try {
			session.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:zhigaomuban"));
			var result = await(session);
			Assertions.assertEquals(List.of("planks"), recipes(result.recipes()));
			Assertions.assertFalse(result.matcher().contains("stone", "zhigaomuban"));
			Assertions.assertTrue(query.matchesInputCandidate(ingredient, result.matcher()));
			session.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("-r:zhigaomuban"));
			Assertions.assertTrue(await(session).recipes().isEmpty());
		} finally {
			session.clear();
		}
	}

	@Test
	public void sessionMatchesOnDemandAndDiscardsSupersededResults() throws Exception {
		var extractions = new java.util.concurrent.atomic.AtomicInteger();
		var sourceRecipes = new java.util.concurrent.atomic.AtomicReference<>(List.of("one", "two"));
		var slowExtraction = new java.util.concurrent.atomic.AtomicBoolean();
		Thread clientThread = Thread.currentThread();
		Set<Thread> workers = java.util.concurrent.ConcurrentHashMap.newKeySet();
		var failSearch = new java.util.concurrent.atomic.AtomicBoolean();
		var release = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch>();
		var entered = new java.util.concurrent.CountDownLatch(1);
		mezz.jei.api.ingredients.IIngredientSupplier ingredients = role -> List.of();
		var catalysts = proxy(mezz.jei.api.recipe.IRecipeCatalystLookup.class, (method, arguments) -> {
			if (method.equals("get"))
				return java.util.stream.Stream.empty();
			throw new UnsupportedOperationException(method);
		});
		var manager = proxy(mezz.jei.api.recipe.IRecipeManager.class, (method, arguments) -> {
			Assertions.assertNotSame(clientThread, Thread.currentThread());
			workers.add(Thread.currentThread());
			return switch (method) {
				case "getRecipeIngredients" -> {
					var latch = release.get();
					if (latch != null && arguments[1].equals("recipe0")) {
						entered.countDown();
						try {
							Assertions.assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new java.util.concurrent.CancellationException();
						}
					}
					if (failSearch.get())
						throw new IllegalStateException("Test search failure");
					extractions.incrementAndGet();
					if (slowExtraction.get())
						java.util.concurrent.locks.LockSupport.parkNanos(3_000_000L);
					yield ingredients;
				}
				case "createRecipeCatalystLookup" -> catalysts;
				default -> throw new UnsupportedOperationException(method);
			};
		});
		var state = proxy(mezz.jei.gui.recipes.lookups.ILookupState.class, (method, arguments) -> switch (method) {
			case "getRecipeCategories" -> List.of(CATEGORY);
			case "getFocusedRecipes" -> new mezz.jei.gui.recipes.lookups.StaticFocusedRecipes<>(CATEGORY, new java.util.ArrayList<Object>(sourceRecipes.get()));
			default -> throw new UnsupportedOperationException(method);
		});
		sourceRecipes.set(java.util.stream.IntStream.range(0, 96).mapToObj(i -> "recipe" + i).toList());
		slowExtraction.set(true);
		var direct = new mezz.jei.gui.recipes.filtering.RecipeSearchSession(manager, null, AliasSearchStorageBuilder::new);
		try {
			int before = extractions.get();
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:macerating"));
			Assertions.assertEquals(sourceRecipes.get(), recipes(await(direct).recipes()));
			Assertions.assertEquals(before, extractions.get(), "recipe-only queries must not extract ingredients or preferences");
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("i:missing | r:macerating"));
			int publications = 0;
			int lastTick = 0;
			int count = 0;
			for (int tick = 1; tick < 1000 && direct.isSearching(); tick++) {
				var result = direct.tick(RecipePreferenceRules.EMPTY);
				if (result.isPresent()) {
					List<String> found = recipes(result.get().recipes());
					Assertions.assertEquals(sourceRecipes.get().subList(0, found.size()), found);
					Assertions.assertTrue(found.size() >= count);
					count = found.size();
					if (direct.isSearching()) {
						Assertions.assertTrue(tick - lastTick >= 10);
						publications++;
					}
					lastTick = tick;
				}
				Thread.sleep(1);
			}
			Assertions.assertFalse(direct.isSearching());
			Assertions.assertTrue(publications > 0);
			Assertions.assertEquals(96, count);
			Assertions.assertEquals(before + 96, extractions.get(), "direct matching must not rescan after publishing");
			if (Runtime.getRuntime().availableProcessors() >= 3)
				Assertions.assertTrue(workers.size() > 1, "multiple background workers must scan the batches");
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("i:missing | r:macerating"));
			direct.tick(RecipePreferenceRules.EMPTY);
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:missing"));
			Assertions.assertTrue(await(direct).recipes().isEmpty());
			failSearch.set(true);
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("i:missing"));
			Assertions.assertThrows(IllegalStateException.class, () -> await(direct));
			Assertions.assertFalse(direct.isSearching());
			failSearch.set(false);
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:macerating"));
			Assertions.assertEquals(sourceRecipes.get(), recipes(await(direct).recipes()));
			direct.request(state, RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("r:macerating"));
			Assertions.assertEquals(sourceRecipes.get(), recipes(await(direct).recipes()));
			direct.request(state, RecipeFilterMode.NOT_PREFERRED, RecipeSearchQuery.parse("r:macerating"));
			Assertions.assertTrue(await(direct).recipes().isEmpty());
			Assertions.assertTrue(direct.needsPreferenceRefresh(new RecipePreferenceRules(List.of())));
			direct.request(state, RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("r:macerating"));
			Assertions.assertFalse(direct.needsPreferenceRefresh(new RecipePreferenceRules(List.of())));
			await(direct);

			TestRecipeCategory unknown = new TestRecipeCategory(RecipeType.create("test", "unknown", String.class), null);
			var laterRecipes = new java.util.concurrent.atomic.AtomicReference<List<Object>>(List.of("one"));
			var multipleCategories = proxy(mezz.jei.gui.recipes.lookups.ILookupState.class, (method, arguments) -> {
				Assertions.assertSame(clientThread, Thread.currentThread());
				return switch (method) {
					case "getRecipeCategories" -> List.of(CATEGORY, unknown);
					case "getFocusedRecipes" -> new mezz.jei.gui.recipes.lookups.StaticFocusedRecipes<>((TestRecipeCategory) arguments[0], arguments[0] == unknown ? laterRecipes.get() : List.of("one"));
					default -> throw new AssertionError(method);
				};
			});
			direct.request(multipleCategories, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:test"));
			Assertions.assertEquals(List.of(CATEGORY, unknown), await(direct).recipes().stream().map(IFocusedRecipes::getRecipeCategory).toList());
			direct.request(multipleCategories, RecipeFilterMode.NOT_PREFERRED, RecipeSearchQuery.parse("r:test"));
			Assertions.assertEquals(List.of(unknown), await(direct).recipes().stream().map(IFocusedRecipes::getRecipeCategory).toList());

			release.set(new java.util.concurrent.CountDownLatch(1));
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("i:missing"));
			direct.tick(RecipePreferenceRules.EMPTY);
			try {
				Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				direct.clear();
				Assertions.assertFalse(direct.isSearching());
			} finally {
				release.get().countDown();
				release.set(null);
			}
			direct.request(state, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:macerating"));
			Assertions.assertEquals(sourceRecipes.get(), recipes(await(direct).recipes()));
			laterRecipes.set(List.of("recipe0", "tail"));
			release.set(new java.util.concurrent.CountDownLatch(1));
			List<IFocusedRecipes<?>> partial = List.of();
			try {
				direct.request(multipleCategories, RecipeFilterMode.ALL, RecipeSearchQuery.parse("i:missing | r:test"));
				long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
				while (partial.isEmpty() && System.nanoTime() < deadline) {
					var update = direct.tick(RecipePreferenceRules.EMPTY);
					if (update.isPresent())
						partial = update.get().recipes();
					Thread.sleep(1);
				}
				Assertions.assertEquals(List.of("one"), recipes(partial));
				Assertions.assertTrue(direct.isSearching());
			} finally {
				release.get().countDown();
				release.set(null);
			}
			Assertions.assertEquals(List.of("one", "recipe0", "tail"), recipes(await(direct).recipes()));
			Assertions.assertEquals(List.of("one"), recipes(partial), "later categories must not mutate previously published results");
			try {
				mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.toggle(unknown.getRecipeType().getUid(), false);
				direct.request(multipleCategories, RecipeFilterMode.PREFERRED, RecipeSearchQuery.parse("r:test"));
				Assertions.assertEquals(List.of(CATEGORY, unknown), await(direct).recipes().stream().map(IFocusedRecipes::getRecipeCategory).toList());
				mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.toggle(unknown.getRecipeType().getUid(), true);
				direct.request(multipleCategories, RecipeFilterMode.DEFAULT, RecipeSearchQuery.parse("r:test"));
				Assertions.assertEquals(List.of(CATEGORY), await(direct).recipes().stream().map(IFocusedRecipes::getRecipeCategory).toList());
				direct.request(multipleCategories, RecipeFilterMode.DISABLED, RecipeSearchQuery.parse("r:test"));
				Assertions.assertEquals(List.of(unknown), await(direct).recipes().stream().map(IFocusedRecipes::getRecipeCategory).toList());
				direct.request(multipleCategories, RecipeFilterMode.ALL, RecipeSearchQuery.parse("r:test"));
				Assertions.assertEquals(List.of(CATEGORY, unknown), await(direct).recipes().stream().map(IFocusedRecipes::getRecipeCategory).toList());
			} finally {
				mezz.jei.gui.recipes.filtering.RecipeCategoryPreferences.clear();
			}
		} finally {
			direct.clear();
		}
	}

	private static mezz.jei.gui.recipes.filtering.RecipeSearchSession.Result await(mezz.jei.gui.recipes.filtering.RecipeSearchSession session) throws Exception {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			var result = session.tick(RecipePreferenceRules.EMPTY);
			if (result.isPresent() && !session.isSearching())
				return result.get();
			Thread.sleep(1);
		}
		throw new AssertionError("Search did not complete");
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
		return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, arguments) -> handler.apply(method.getName(), arguments));
	}

	private static List<String> recipes(List<IFocusedRecipes<?>> projected) {
		return projected.stream()
			.flatMap(focused -> focused.getRecipes().stream())
			.map(String.class::cast)
			.toList();
	}

	private static final class AliasSearchStorageBuilder<T> extends BakedSubstringIndexBuilder<T> {
		@Override
		public void put(String key, T value) {
			super.put(key, value);
			if (key.contains("至高木板")) {
				super.put("zhigaomuban", value);
			}
		}
	}

}
