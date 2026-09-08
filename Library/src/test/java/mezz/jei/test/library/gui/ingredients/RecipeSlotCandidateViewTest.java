package mezz.jei.test.library.gui.ingredients;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.gui.IRecipeSlotCandidateView;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.library.gui.ingredients.ICycler;
import mezz.jei.library.gui.ingredients.RecipeSlot;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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

	private static ITypedIngredient<String> typed(String value) {
		return new ITypedIngredient<>() {
			@Override
			public ITypedIngredient<String> normalize(mezz.jei.api.ingredients.IIngredientHelper<String> helper) {
				return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
			}

			@Override
			public IIngredientType<String> getType() {
				return TYPE;
			}

			@Override
			public String getIngredient() {
				return value;
			}
		};
	}
}
