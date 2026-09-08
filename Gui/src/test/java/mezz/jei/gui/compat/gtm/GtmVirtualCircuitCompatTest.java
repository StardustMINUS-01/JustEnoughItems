package mezz.jei.gui.compat.gtm;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class GtmVirtualCircuitCompatTest {
	@BeforeAll
	static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void resolvesCircuitAndFluidNonConsumablesThroughOnePath() {
		var circuit = IntCircuitBehaviour.stack(7);
		var recipe = new GTRecipe(7, circuit, "water:100");
		var projection = GtmVirtualCircuitCompat.projectVirtualInputs(
			recipe,
			ingredientManager(),
			fluid -> Optional.of(typed("test:fluid", fluid))
		);

		Assertions.assertEquals(2, projection.inputs().size());
		Assertions.assertEquals(1, projection.programmedCircuits().size());
		Assertions.assertEquals(1, projection.catalysts().size());
		Assertions.assertTrue(projection.programmedCircuits().stream().anyMatch(input -> GtmVirtualCircuitCompat.isProgrammedCircuit(input.ingredient())));
		Assertions.assertTrue(projection.catalysts().stream().anyMatch(input -> "water:100".equals(input.ingredient().getIngredient())));
	}

	private static IIngredientManager ingredientManager() {
		return (IIngredientManager) java.lang.reflect.Proxy.newProxyInstance(
			GtmVirtualCircuitCompatTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if ("createTypedIngredient".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return Optional.of(typed(VanillaTypes.ITEM_STACK.getUid(), (ItemStack) args[1]));
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static <T> ITypedIngredient<T> typed(String uid, T ingredient) {
		return new ITypedIngredient<>() {
			@Override
			public ITypedIngredient<T> normalize(mezz.jei.api.ingredients.IIngredientHelper<T> helper) {
				return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
			}

			@Override
			public IIngredientType<T> getType() {
				return new IIngredientType<>() {
					@Override
					@SuppressWarnings("unchecked")
					public Class<? extends T> getIngredientClass() {
						return (Class<? extends T>) ingredient.getClass();
					}

					@Override
					public String getUid() {
						return uid;
					}
				};
			}

			@Override
			public T getIngredient() {
				return ingredient;
			}
		};
	}
}
