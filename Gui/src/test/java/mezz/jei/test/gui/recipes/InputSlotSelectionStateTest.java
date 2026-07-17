package mezz.jei.test.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.recipes.InputSlotSelectionState;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class InputSlotSelectionStateTest {
	private static final IIngredientType<String> TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}

		@Override
		public String getUid() {
			return "test:ingredient";
		}
	};

	@Test
	public void scrollsWrappedHoveredInputSlot() {
		IRecipeSlotDrawable hoveredSlot = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable layoutSlot = slot(List.of(typed("first"), typed("second")));
		IRecipeLayoutDrawable<?> layout = layout(hoveredSlot, layoutSlot);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());

		boolean handled = state.scroll(layout, 4, 4, -1, false);

		Assertions.assertTrue(handled);
		Assertions.assertEquals("second", layoutSlot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals(1, state.selectedKeys().size());
	}

	@Test
	public void controlScrollsEveryWrappedSlotInTheSameCandidateFamily() {
		IRecipeSlotDrawable hoveredSlot = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable firstFamilySlot = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable secondFamilySlot = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable otherSlot = slot(List.of(typed("third"), typed("fourth")));
		IRecipeLayoutDrawable<?> layout = layout(hoveredSlot, firstFamilySlot, secondFamilySlot, otherSlot);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());

		boolean handled = state.scroll(layout, 4, 4, -1, true);

		Assertions.assertTrue(handled);
		Assertions.assertEquals("second", firstFamilySlot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals("second", secondFamilySlot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals("third", otherSlot.getDisplayedIngredient().orElseThrow().getIngredient());
	}

	@Test
	public void transferViewRestrictsManuallySelectedInputToItsSelectedCandidate() throws ReflectiveOperationException {
		IRecipeSlotDrawable hoveredSlot = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable layoutSlot = slot(List.of(typed("first"), typed("second")));
		IRecipeLayoutDrawable<?> layout = layout(hoveredSlot, layoutSlot);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());
		Assertions.assertTrue(state.scroll(layout, 4, 4, -1, false));

		IRecipeSlotsView transferView = Assertions.assertDoesNotThrow(() -> {
			var method = InputSlotSelectionState.class.getMethod("createTransferSlotsView", IRecipeLayoutDrawable.class);
			return (IRecipeSlotsView) method.invoke(state, layout);
		});

		List<String> transferredCandidates = transferView.getSlotViews(RecipeIngredientRole.INPUT).getFirst()
			.getAllIngredients()
			.map(ITypedIngredient::getIngredient)
			.map(String.class::cast)
			.toList();
		Assertions.assertEquals(List.of("second"), transferredCandidates);
	}


	private static IRecipeLayoutDrawable<?> layout(IRecipeSlotDrawable hoveredSlot, IRecipeSlotDrawable... layoutSlots) {
		return proxy(IRecipeLayoutDrawable.class, (proxy, method, args) -> switch (method.getName()) {
			case "getSlotUnderMouse" -> Optional.of(new RecipeSlotUnderMouse(hoveredSlot, 0, 0));
			case "getRecipeSlotsView" -> (IRecipeSlotsView) () -> List.of(layoutSlots);
			case "isMouseOver" -> true;
			case "getRect", "getRectWithBorder" -> new Rect2i(0, 0, 16, 16);
			default -> defaultValue(method.getReturnType());
		});
	}

	private static IRecipeSlotDrawable slot(List<ITypedIngredient<?>> candidates) {
		AtomicReference<ITypedIngredient<?>> displayed = new AtomicReference<>(candidates.getFirst());
		AtomicReference<IIngredientConsumer> consumer = new AtomicReference<>();
		return proxy(IRecipeSlotDrawable.class, (proxy, method, args) -> switch (method.getName()) {
			case "getRole" -> RecipeIngredientRole.INPUT;
			case "getAllIngredients" -> candidates.stream();
			case "getAllIngredientsList" -> candidates;
			case "getDisplayedIngredient" -> Optional.of(displayed.get());
			case "clearDisplayOverrides" -> {
				displayed.set(candidates.getFirst());
				yield null;
			}
			case "createDisplayOverrides" -> {
				IIngredientConsumer value = consumer.updateAndGet(existing -> existing == null ? ingredientConsumer(displayed) : existing);
				yield value;
			}
			default -> defaultValue(method.getReturnType());
		});
	}

	private static IIngredientConsumer ingredientConsumer(AtomicReference<ITypedIngredient<?>> displayed) {
		return proxy(IIngredientConsumer.class, (proxy, method, args) -> {
			if (method.getName().equals("addTypedIngredient")) {
				displayed.set((ITypedIngredient<?>) args[0]);
				return proxy;
			}
			if (method.getName().equals("addIngredient")) {
				displayed.set(typed((String) args[1]));
				return proxy;
			}
			return proxy;
		});
	}

	private static IIngredientManager ingredientManager() {
		IIngredientHelper<String> helper = proxy(IIngredientHelper.class, (proxy, method, args) -> switch (method.getName()) {
			case "getUniqueId" -> args[0];
			case "getIngredientType" -> TYPE;
			default -> defaultValue(method.getReturnType());
		});
		return proxy(IIngredientManager.class, (proxy, method, args) -> {
			if (method.getName().equals("getIngredientHelper")) {
				return helper;
			}
			return defaultValue(method.getReturnType());
		});
	}

	private static ITypedIngredient<String> typed(String value) {
		return new ITypedIngredient<>() {
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


	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(InputSlotSelectionStateTest.class.getClassLoader(), new Class<?>[]{type}, handler);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class || type == short.class || type == byte.class || type == long.class || type == float.class || type == double.class) {
			return 0;
		}
		return '\0';
	}
}
