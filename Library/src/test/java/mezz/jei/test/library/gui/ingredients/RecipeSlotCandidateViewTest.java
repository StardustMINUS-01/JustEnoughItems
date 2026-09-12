package mezz.jei.test.library.gui.ingredients;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.gui.IRecipeSlotCandidateView;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.library.gui.ingredients.ICycler;
import mezz.jei.library.gui.ingredients.RecipeSlot;
import mezz.jei.library.gui.ingredients.RecipeSlotIngredients;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Proxy;

public class RecipeSlotCandidateViewTest {
	private static final IIngredientType<String> TYPE = () -> String.class;

	@Test
	public void filteredCandidateViewControlsRotationWithoutChangingAllIngredients() {
		List<ITypedIngredient<?>> all = List.of(typed("oak"), typed("supreme_crimson"), typed("supreme_blazing"));
		RecipeSlot slot = new RecipeSlot(
			RecipeIngredientRole.INPUT,
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

		var previousConfigs = mezz.jei.common.Internal.getOptionalJeiClientConfigs();
		var cycling = new mezz.jei.common.config.file.ConfigValue<>("test", "cycling", true, mezz.jei.common.config.file.serializers.BooleanSerializer.INSTANCE);
		var clientConfig = (mezz.jei.common.config.IClientConfig) java.lang.reflect.Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{mezz.jei.common.config.IClientConfig.class},
			(proxy, method, args) -> cycling
		);
		var configs = (mezz.jei.common.config.IJeiClientConfigs) java.lang.reflect.Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{mezz.jei.common.config.IJeiClientConfigs.class},
			(proxy, method, args) -> clientConfig
		);
		mezz.jei.common.Internal.setJeiClientConfigs(configs);
		try {
			Assertions.assertEquals("supreme_crimson", slot.getDisplayedIngredient().orElseThrow().getIngredient());
			Assertions.assertEquals(3, slot.getAllIngredients().count());
			cycling.set(false);
			Assertions.assertEquals("supreme_crimson", slot.getDisplayedIngredient().orElseThrow().getIngredient());
		} finally {
			mezz.jei.common.Internal.setJeiClientConfigs(previousConfigs.orElse(null));
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

		var previous = Internal.getOptionalJeiRuntime();
		var manager = (IIngredientManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> { throw new AssertionError(method.getName()); });
		var runtime = (IJeiRuntime) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IJeiRuntime.class},
			(proxy, method, args) -> manager);
		Internal.setRuntime(runtime);
		try {
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
		} finally {
			Internal.setRuntime(previous.orElse(null));
		}
	}

	private static ITypedIngredient<String> typed(String value) {
		return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(TYPE, value);
	}
}
