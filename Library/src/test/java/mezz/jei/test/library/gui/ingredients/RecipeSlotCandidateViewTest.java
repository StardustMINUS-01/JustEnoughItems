package mezz.jei.test.library.gui.ingredients;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.gui.IRecipeSlotCandidateView;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.library.gui.ingredients.ICycler;
import mezz.jei.library.gui.ingredients.RecipeSlot;
import mezz.jei.library.gui.ingredients.RecipeSlotIngredients;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.mezzdev.config.file.ConfigFileWatcherSettings;
import net.mezzdev.config.file.ConfigManager;
import net.mezzdev.config.schema.ConfigSchemaBuilder;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.lang.reflect.Proxy;
import java.nio.file.Path;

public class RecipeSlotCandidateViewTest {
	private static final IIngredientType<String> TYPE = () -> String.class;

	@Test
	public void filteredCandidateViewControlsRotationWithoutChangingAllIngredients(@TempDir Path directory) {
		List<ITypedIngredient<?>> all = List.of(typed("oak"), typed("supreme_crimson"), typed("supreme_blazing"));
		RecipeSlot slot = new RecipeSlot(
			RecipeIngredientRole.INPUT,
			new ImmutableRect2i(0, 0, 16, 16),
			new ImmutableRect2i(0, 0, 16, 16),
			new ICycler() {
				@Override
				public <T> java.util.Optional<T> getCycled(List<@Nullable T> list) {
					return list.stream().findFirst();
				}
			},
			new java.util.ArrayList<>(),
			all,
			all,
			null,
			null,
			null,
			null
		);

		((IRecipeSlotCandidateView) slot).setDisplayedCandidates(all.subList(1, 3));

		var previousConfigs = mezz.jei.common.Internal.getOptionalClientConfigs();
		var disabledWatcher = ConfigFileWatcherSettings.clientDefaults().withEnabled(false);
		var configManager = new ConfigManager("Recipe candidates test", disabledWatcher, disabledWatcher);
		var builder = new ConfigSchemaBuilder("jei", directory.resolve("client.ini"), "jei.config.client", configManager);
		var cycling = builder.addCategory("recipes").addBoolean("cycling", true).build();
		builder.build();
		var clientConfig = (mezz.jei.common.config.IClientConfig) java.lang.reflect.Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{mezz.jei.common.config.IClientConfig.class},
			(proxy, method, args) -> cycling
		);
		var configs = (mezz.jei.common.config.IClientConfigs) java.lang.reflect.Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{mezz.jei.common.config.IClientConfigs.class},
			(proxy, method, args) -> clientConfig
		);
		mezz.jei.common.Internal.setClientConfigs(configs);
		try {
			Assertions.assertEquals("supreme_crimson", slot.getDisplayedIngredient().orElseThrow().getIngredient());
			Assertions.assertEquals(3, slot.getAllIngredients().count());
			cycling.set(false);
			Assertions.assertEquals("supreme_crimson", slot.getDisplayedIngredient().orElseThrow().getIngredient());
		} finally {
			mezz.jei.common.Internal.setClientConfigs(previousConfigs.orElse(null));
		}
	}

	@Test
	public void respectsFocusAndOverrides() {
		List<ITypedIngredient<?>> all = List.of(typed("oak"), typed("birch"), typed("spruce"));
		var ingredients = new RecipeSlotIngredients(all, all.subList(1, 3), () -> {});
		ingredients.setSelectedCandidate(all.get(2));
		Assertions.assertEquals(all.subList(1, 3), ingredients.getCandidateIngredients().toList());
		Assertions.assertSame(all.get(2), ingredients.getFirstDisplayedIngredient().orElseThrow());
		ingredients.setDisplayedCandidates(List.of(all.get(2)));
		Assertions.assertEquals(List.of(all.get(2)), ingredients.getCandidateIngredients().toList());

		withVisibility(ingredient -> true, () -> {
			ingredients.createDisplayOverrides();
			Assertions.assertTrue(ingredients.getCandidateIngredients().findAny().isEmpty());
			Assertions.assertTrue(ingredients.getFirstDisplayedIngredient().isEmpty());
			ingredients.createDisplayOverrides().addTypedIngredients(all.subList(1, 3));
			Assertions.assertSame(all.get(2), ingredients.getFirstDisplayedIngredient().orElseThrow());
			Assertions.assertEquals(all.subList(1, 3), ingredients.getCandidateIngredients().toList());
			ingredients.clearDisplayOverrides();
			ingredients.createDisplayOverrides().addTypedIngredient(all.getFirst());
			Assertions.assertSame(all.getFirst(), ingredients.getFirstDisplayedIngredient().orElseThrow());
			Assertions.assertEquals(List.of(all.getFirst()), ingredients.getCandidateIngredients().toList());
			ingredients.clearDisplayOverrides();
			Assertions.assertSame(all.get(2), ingredients.getFirstDisplayedIngredient().orElseThrow());
			ingredients.setSelectedCandidate(null);
			ingredients.setDisplayedCandidates(null);
			Assertions.assertEquals(all.subList(1, 3), ingredients.getCandidateIngredients().toList());
			Assertions.assertSame(all.get(1), ingredients.getFirstDisplayedIngredient().orElseThrow());
		});
	}

	@Test
	public void completeCandidatesRemainSelectableBeyondTheCyclingWindow() {
		List<ITypedIngredient<?>> all = candidates(101);
		withVisibility(ingredient -> true, () -> {
			var ingredients = new RecipeSlotIngredients(all, null, () -> {});
			assertRotation(ingredients, all.subList(0, 100));
			Assertions.assertEquals(all, ingredients.getCandidateIngredients().toList());
			ingredients.setSelectedCandidate(all.getLast());
			Assertions.assertSame(all.getLast(), ingredients.getFirstDisplayedIngredient().orElseThrow());
			Assertions.assertEquals(all, ingredients.getCandidateIngredients().toList());
			ingredients.setSelectedCandidate(null);
			assertRotation(ingredients, all.subList(0, 100));
			var overrides = ingredients.createDisplayOverrides();
			overrides.addTypedIngredients(all);
			Assertions.assertEquals(all, ingredients.getCandidateIngredients().toList());
			assertRotation(ingredients, all.subList(0, 100));
			overrides.addTypedIngredient(typed("additional_override"));
			Assertions.assertEquals(102, ingredients.getCandidateIngredients().count());
			assertRotation(ingredients, all.subList(0, 100));
		});
	}

	@Test
	public void hiddenAndBlankEntriesNeverLeakFromRotationIntoCandidates() {
		List<ITypedIngredient<?>> all = candidates(102);
		var withBlank = new ArrayList<@Nullable ITypedIngredient<?>>(all);
		withBlank.addFirst(null);
		withVisibility(ingredient -> ingredient != all.getFirst(), () -> {
			var ingredients = new RecipeSlotIngredients(withBlank, null, () -> {});
			var rotation = new ArrayList<@Nullable ITypedIngredient<?>>();
			rotation.add(null);
			rotation.addAll(all.subList(1, 100));
			assertRotation(ingredients, rotation);
			Assertions.assertEquals(all.subList(1, 102), ingredients.getCandidateIngredients().toList());
			Assertions.assertEquals(all.subList(1, 102), ingredients.getDisplayedIngredients().toList());
		});
	}

	@Test
	public void allHiddenFallbackIsOnlyUsedForCyclingAndIsNotRescannedEachFrame() {
		List<ITypedIngredient<?>> all = candidates(101);
		AtomicInteger visibilityChecks = new AtomicInteger();
		withVisibility(ingredient -> {
			visibilityChecks.incrementAndGet();
			return false;
		}, () -> {
			var ingredients = new RecipeSlotIngredients(all, null, () -> {});
			Assertions.assertTrue(ingredients.getCandidateIngredients().findAny().isEmpty());
			Assertions.assertTrue(ingredients.getDisplayedIngredients().findAny().isEmpty());
			assertRotation(ingredients, all.subList(0, 100));
			int checks = visibilityChecks.get();
			for (int frame = 0; frame < 200; frame++) {
				assertRotation(ingredients, all.subList(0, 100));
			}
			Assertions.assertEquals(checks, visibilityChecks.get());
			Assertions.assertTrue(ingredients.getCandidateIngredients().findAny().isEmpty());
		});
	}

	@Test
	public void explicitCandidatesKeepTheirScopeWhileOverridesUseVisibility() {
		List<ITypedIngredient<?>> all = candidates(103);
		List<ITypedIngredient<?>> focused = all.subList(1, 103);
		List<ITypedIngredient<?>> filtered = all.subList(2, 103);
		withVisibility(ingredient -> ingredient == all.getLast(), () -> {
			var ingredients = new RecipeSlotIngredients(all, focused, () -> {});
			Assertions.assertEquals(focused, ingredients.getCandidateIngredients().toList());
			assertRotation(ingredients, focused.subList(0, 100));
			ingredients.setDisplayedCandidates(filtered);
			Assertions.assertEquals(filtered, ingredients.getCandidateIngredients().toList());
			assertRotation(ingredients, filtered.subList(0, 100));
			ingredients.setSelectedCandidate(all.getLast());
			assertRotation(ingredients, List.of(all.getLast()));

			var overrides = ingredients.createDisplayOverrides();
			Assertions.assertTrue(ingredients.getCandidateIngredients().findAny().isEmpty());
			Assertions.assertTrue(ingredients.getFirstDisplayedIngredient().isEmpty());
			overrides.addTypedIngredients(all.subList(0, 101));
			assertRotation(ingredients, all.subList(0, 100));
			Assertions.assertTrue(ingredients.getCandidateIngredients().findAny().isEmpty());
			overrides.addTypedIngredient(all.getLast());
			Assertions.assertEquals(List.of(all.getLast()), ingredients.getCandidateIngredients().toList());
			assertRotation(ingredients, List.of(all.getLast()));
			ingredients.setSelectedCandidate(null);
			assertRotation(ingredients, all.subList(0, 100));
			ingredients.clearDisplayOverrides();
			Assertions.assertEquals(filtered, ingredients.getCandidateIngredients().toList());
			assertRotation(ingredients, filtered.subList(0, 100));
			ingredients.setDisplayedCandidates(null);
			Assertions.assertEquals(focused, ingredients.getCandidateIngredients().toList());
			assertRotation(ingredients, focused.subList(0, 100));
			ingredients.setDisplayedCandidates(List.of());
			Assertions.assertTrue(ingredients.getCandidateIngredients().findAny().isEmpty());
			Assertions.assertTrue(ingredients.getFirstDisplayedIngredient().isEmpty());
		});
	}

	@Test
	public void selectedTailCandidateIsReusedUntilItsScopeChanges() {
		List<ITypedIngredient<?>> all = candidates(2048);
		AtomicInteger reads = new AtomicInteger();
		List<ITypedIngredient<?>> counted = new java.util.AbstractList<>() {
			@Override
			public ITypedIngredient<?> get(int index) {
				reads.incrementAndGet();
				return all.get(index);
			}

			@Override
			public int size() {
				return all.size();
			}
		};
		boolean[] visible = {true};
		withVisibility(ingredient -> visible[0], () -> {
			var ingredients = new RecipeSlotIngredients(counted, null, () -> {});
			ingredients.setSelectedCandidate(all.getLast());
			Assertions.assertSame(all.getLast(), ingredients.getSelectedCandidate().orElseThrow());
			int initialReads = reads.get();
			for (int tick = 0; tick < 200; tick++) {
				Assertions.assertSame(all.getLast(), ingredients.getSelectedCandidate().orElseThrow());
			}
			Assertions.assertEquals(initialReads, reads.get());
			visible[0] = false;
			Assertions.assertTrue(ingredients.getSelectedCandidate().isEmpty());
			ingredients.setDisplayedCandidates(List.of(all.getFirst()));
			Assertions.assertTrue(ingredients.getSelectedCandidate().isEmpty());
			ingredients.setDisplayedCandidates(List.of(all.getLast()));
			Assertions.assertSame(all.getLast(), ingredients.getSelectedCandidate().orElseThrow());
			var overrides = ingredients.createDisplayOverrides();
			Assertions.assertTrue(ingredients.getSelectedCandidate().isEmpty());
			overrides.addTypedIngredient(all.getLast());
			Assertions.assertTrue(ingredients.getSelectedCandidate().isEmpty());
			visible[0] = true;
			Assertions.assertSame(all.getLast(), ingredients.getSelectedCandidate().orElseThrow());
			ingredients.setSelectedCandidate(null);
			Assertions.assertTrue(ingredients.getSelectedCandidate().isEmpty());
		});
	}

	private static List<ITypedIngredient<?>> candidates(int count) {
		return IntStream.range(0, count).<ITypedIngredient<?>>mapToObj(i -> typed("candidate_" + i)).toList();
	}

	private static void assertRotation(RecipeSlotIngredients ingredients, List<@Nullable ITypedIngredient<?>> expected) {
		ingredients.getDisplayedIngredient(new ICycler() {
			@Override
			public <T> Optional<T> getCycled(List<@Nullable T> list) {
				Assertions.assertEquals(expected, list);
				return Optional.empty();
			}
		});
	}

	private static void withVisibility(Predicate<ITypedIngredient<?>> isVisible, Runnable test) {
		var visibility = (IIngredientVisibility) Proxy.newProxyInstance(IIngredientVisibility.class.getClassLoader(), new Class<?>[]{IIngredientVisibility.class},
			(proxy, method, args) -> {
				if (method.getName().equals("isIngredientVisible")) {
					Assertions.assertEquals(UidContext.Recipe, args[1]);
					return isVisible.test((ITypedIngredient<?>) args[0]);
				}
				throw new AssertionError(method.getName());
			});
		var helpers = (IJeiHelpers) Proxy.newProxyInstance(IJeiHelpers.class.getClassLoader(), new Class<?>[]{IJeiHelpers.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getIngredientVisibility")) {
					return visibility;
				}
				throw new AssertionError(method.getName());
			});
		var manager = (IIngredientManager) Proxy.newProxyInstance(IIngredientManager.class.getClassLoader(), new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> { throw new AssertionError(method.getName()); });
		var runtime = (IJeiRuntime) Proxy.newProxyInstance(IJeiRuntime.class.getClassLoader(), new Class<?>[]{IJeiRuntime.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getIngredientManager" -> manager;
				case "getJeiHelpers" -> helpers;
				default -> throw new AssertionError(method.getName());
			});
		var previous = Internal.getOptionalJeiRuntime();
		Internal.setRuntime(runtime);
		try {
			test.run();
		} finally {
			Internal.setRuntime(previous.orElse(null));
		}
	}

	private static ITypedIngredient<String> typed(String value) {
		return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(TYPE, value);
	}
}
