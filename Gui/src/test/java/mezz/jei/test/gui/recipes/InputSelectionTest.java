package mezz.jei.test.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.gui.recipes.InputSlotSelectionState;
import net.minecraft.client.renderer.Rect2i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class InputSelectionTest {
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
	public void changesAndClearsSelection() {
		IRecipeSlotDrawable slot = slot(List.of(typed("first"), typed("second"), typed("third")));
		IRecipeSlotDrawable other = slot(List.of(typed("first"), typed("second"), typed("third")));
		IRecipeLayoutDrawable<?> layout = layout(slot, other, slot);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());

		Assertions.assertTrue(state.select(layout, slot, typed("second"), false, true));
		Assertions.assertTrue(state.select(layout, slot, typed("third"), false, true));
		Assertions.assertEquals("third", state.resolve(slot, 1).orElseThrow().getIngredient());
		Assertions.assertEquals(Map.of(1, key("third")), state.selectedKeys());
		Assertions.assertEquals("first", other.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals(3, slot.getAllIngredients().count());
		Assertions.assertTrue(state.select(layout, slot, typed("third"), false, true));
		Assertions.assertTrue(state.selectedKeys().isEmpty());
		Assertions.assertEquals("first", slot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals(3, state.createTransferSlotsView(layout).getSlotViews().get(1).getAllIngredients().count());
	}

	@Test
	public void synchronizesSelection() {
		IRecipeSlotDrawable first = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable second = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable other = slot(List.of(typed("third")));
		IRecipeLayoutDrawable<?> layout = layout(first, first, second, other);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());

		Assertions.assertTrue(state.select(layout, first, typed("second"), true, true));
		Assertions.assertEquals(Map.of(0, key("second"), 1, key("second")), state.selectedKeys());
		Assertions.assertEquals("third", other.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals("second", state.resolve(second, 1).orElseThrow().getIngredient());
		Assertions.assertTrue(state.select(layout, first, typed("second"), true, true));
		Assertions.assertTrue(state.selectedKeys().isEmpty());
	}

	@Test
	public void scrollsWrappedSlot() {
		IRecipeSlotDrawable hovered = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable slot = slot(List.of(typed("first"), typed("second")));
		IRecipeLayoutDrawable<?> layout = layout(hovered, slot);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());

		boolean handled = state.scroll(layout, 4, 4, -1, false);

		Assertions.assertTrue(handled);
		Assertions.assertEquals("second", slot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals(1, state.selectedKeys().size());
		Assertions.assertEquals(List.of("second"), state.createTransferSlotsView(layout)
			.getSlotViews(RecipeIngredientRole.INPUT).getFirst().getAllIngredients()
			.map(ITypedIngredient::getIngredient).toList());
	}

	@Test
	public void synchronizesScroll() {
		IRecipeSlotDrawable hovered = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable first = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable second = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable other = slot(List.of(typed("third"), typed("fourth")));
		IRecipeLayoutDrawable<?> layout = layout(hovered, first, second, other);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());

		boolean handled = state.scroll(layout, 4, 4, -1, true);

		Assertions.assertTrue(handled);
		Assertions.assertEquals("second", first.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals("second", second.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals("third", other.getDisplayedIngredient().orElseThrow().getIngredient());
	}

	@Test
	public void keepsIdleStateCheap() {
		IRecipeSlotsView slots = () -> List.of(slot(List.of(typed("first"))));
		IRecipeLayoutDrawable<?> layout = proxy(IRecipeLayoutDrawable.class, (proxy, method, args) -> switch (method.getName()) {
			case "getRecipeSlotsView" -> slots;
			default -> throw new UnsupportedOperationException(method.getName());
		});
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());

		state.apply(proxy(IRecipeLayoutDrawable.class, (proxy, method, args) -> {
			throw new AssertionError("Idle state must not read the layout");
		}));
		Assertions.assertSame(slots, state.createTransferSlotsView(layout));
	}

	@Test
	public void prefersManualSelection() {
		IRecipeSlotDrawable first = slot(List.of(typed("first"), typed("second")));
		IRecipeSlotDrawable second = slot(List.of(typed("third"), typed("fourth")));
		IRecipeLayoutDrawable<?> layout = layout(first, first, second);
		InputSlotSelectionState state = new InputSlotSelectionState(ingredientManager());
		state.setSelectedKeys(Map.of(0, key("second")));
		Assertions.assertEquals("first", first.getDisplayedIngredient().orElseThrow().getIngredient());

		Map<Integer, BookmarkIngredientKey> selections = state.currentSelections(layout);

		Assertions.assertEquals(2, selections.size());
		Assertions.assertEquals("second", selections.get(0).ingredientUid());
		Assertions.assertEquals("third", selections.get(1).ingredientUid());
	}

	@Test
	public void scrollsFilteredCandidates() {
		List<ITypedIngredient<?>> candidates = List.of(typed("oak"), typed("supreme_crimson"), typed("supreme_blazing"));
		IRecipeSlotDrawable hovered = slot(candidates);
		IRecipeSlotDrawable slot = slot(candidates);
		IRecipeLayoutDrawable<?> layout = layout(hovered, slot);
		InputSlotSelectionState state = new InputSlotSelectionState(
			ingredientManager(),
			candidate -> ((String) candidate.getIngredient()).startsWith("supreme_")
		);
		state.applyCandidateFilter(layout);

		boolean handled = state.scroll(layout, 4, 4, -1, false);

		Assertions.assertTrue(handled);
		Assertions.assertEquals("supreme_blazing", slot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertEquals(
			List.of("supreme_crimson", "supreme_blazing"),
			state.filteredCandidates().get(0).stream()
				.map(ITypedIngredient::getIngredient)
				.toList()
		);
	}

	@Test
	public void keepsUnmatchedCandidates() {
		IRecipeSlotDrawable hovered = slot(List.of(typed("oak"), typed("birch")));
		IRecipeSlotDrawable slot = slot(List.of(typed("oak"), typed("birch")));
		IRecipeLayoutDrawable<?> layout = layout(hovered, slot);
		InputSlotSelectionState state = new InputSlotSelectionState(
			ingredientManager(),
			candidate -> ((String) candidate.getIngredient()).startsWith("supreme_")
		);
		state.applyCandidateFilter(layout);

		Assertions.assertTrue(state.scroll(layout, 4, 4, -1, false));
		Assertions.assertEquals("birch", slot.getDisplayedIngredient().orElseThrow().getIngredient());
		Assertions.assertTrue(state.filteredCandidates().isEmpty());
	}

	private static IRecipeLayoutDrawable<?> layout(IRecipeSlotDrawable hovered, IRecipeSlotDrawable... slots) {
		return proxy(IRecipeLayoutDrawable.class, (proxy, method, args) -> switch (method.getName()) {
			case "getSlotUnderMouse" -> Optional.of(new RecipeSlotUnderMouse(hovered, 0, 0));
			case "getRecipeSlotsView" -> (IRecipeSlotsView) () -> List.of(slots);
			case "isMouseOver" -> true;
			case "getRect", "getRectWithBorder" -> new Rect2i(0, 0, 16, 16);
			default -> throw new UnsupportedOperationException(method.getName());
		});
	}

	private static IRecipeSlotDrawable slot(List<ITypedIngredient<?>> candidates) {
		AtomicReference<ITypedIngredient<?>> displayed = new AtomicReference<>(candidates.getFirst());
		IIngredientConsumer consumer = ingredientConsumer(displayed);
		return proxy(IRecipeSlotDrawable.class, (proxy, method, args) -> switch (method.getName()) {
			case "getRole" -> RecipeIngredientRole.INPUT;
			case "getAllIngredients" -> candidates.stream();
			case "getDisplayedIngredients" -> candidates.stream();
			case "getAllIngredientsList" -> candidates;
			case "getDisplayedIngredient" -> Optional.of(displayed.get());
			case "clearDisplayOverrides" -> {
				displayed.set(candidates.getFirst());
				yield null;
			}
			case "createDisplayOverrides" -> consumer;
			default -> throw new UnsupportedOperationException(method.getName());
		});
	}

	private static IIngredientConsumer ingredientConsumer(AtomicReference<ITypedIngredient<?>> displayed) {
		return proxy(IIngredientConsumer.class, (proxy, method, args) -> {
			if (method.getName().equals("addTypedIngredient")) {
				displayed.set((ITypedIngredient<?>) args[0]);
				return proxy;
			}
			throw new UnsupportedOperationException(method.getName());
		});
	}

	@SuppressWarnings("unchecked")
	private static IIngredientManager ingredientManager() {
		IIngredientHelper<String> helper = proxy(IIngredientHelper.class, (proxy, method, args) -> switch (method.getName()) {
			case "getUniqueId" -> args[0];
			case "getIngredientType" -> TYPE;
			default -> throw new UnsupportedOperationException(method.getName());
		});
		return proxy(IIngredientManager.class, (proxy, method, args) -> {
			if (method.getName().equals("getIngredientHelper")) {
				return helper;
			}
			throw new UnsupportedOperationException(method.getName());
		});
	}

	private static ITypedIngredient<String> typed(String value) {
		return TypedIngredient.createUnvalidated(TYPE, value);
	}

	private static BookmarkIngredientKey key(String value) {
		return BookmarkIngredientKey.of(TYPE.getUid(), value);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(InputSelectionTest.class.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				return switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					case "toString" -> type.getSimpleName();
					default -> throw new UnsupportedOperationException(method.getName());
				};
			}
			return handler.invoke(proxy, method, args);
		});
	}
}
