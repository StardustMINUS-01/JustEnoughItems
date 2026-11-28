package mezz.jei.test.gui.favorites;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.InputPhase;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.keys.IJeiKeyMappingWithExtraModifiers;
import net.minecraft.network.chat.Component;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeGridSource;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.overlay.bookmarks.BookmarkPanelLayout.PanelSlot;
import mezz.jei.gui.overlay.bookmarks.FavoriteRecipeSortDragState;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FavoriteGridSourceTest {
	private static final TestIngredientType TYPE = new TestIngredientType("test:ingredient");
	private static final TestIngredientType FLUID_TYPE = new TestIngredientType("fluid_stack");
	private static final FocusedRecipe RECIPE = recipe("gear");

	@Test
	public void createsGridLazily() {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipePanelState state = new FavoriteRecipePanelState();
		CountingInputsResolver resolver = new CountingInputsResolver();
		FavoriteRecipeGridSource source = source(store, state, resolver);

		List<IElement<?>> elements = source.getElements();

		Assertions.assertEquals(1, elements.size());
		Assertions.assertEquals(0, resolver.calls);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void hidesDefaultAmount(boolean fluid) {
		var store = store(fluid ? FLUID_TYPE : TYPE, fluid ? "water" : "gear", RECIPE);
		FavoriteRecipeGridSource source = source(store, new FavoriteRecipePanelState(), new CountingInputsResolver());

		FavoriteRecipeElement<?> element = (FavoriteRecipeElement<?>) source.getElements().getFirst();

		Assertions.assertTrue(element.getAmountText().isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"-1, 0", "17.5, 0", "18, 1", "35.5, 1", "45, 1", "63, 2", "99, 2"})
	void dropsRelativeToTheWholeFavoriteRecipe(double mouseY, int expectedIndex) {
		var plate = recipe("plate");
		var bolt = recipe("bolt");
		var store = store("gear", RECIPE);
		store.setFavorite(BookmarkIngredientKey.of(TYPE.getUid(), "plate"), plate, Map.of());
		store.setFavorite(BookmarkIngredientKey.of(TYPE.getUid(), "bolt"), bolt, Map.of());
		var state = new FavoriteRecipePanelState();
		var elements = source(store, state, new CountingInputsResolver()).getElements();
		var gearElement = (FavoriteRecipeElement<?>) elements.get(0);
		var sourceArea = new ImmutableRect2i(0, 90, 18, 18);
		var slots = List.<PanelSlot<FavoriteRecipeElement<?>>>of(
			new PanelSlot<>(gearElement, 1, new ImmutableRect2i(0, 18, 18, 18), false),
			new PanelSlot<>((FavoriteRecipeElement<?>) elements.get(1), 2, new ImmutableRect2i(0, 54, 18, 18), false),
			new PanelSlot<>((FavoriteRecipeElement<?>) elements.get(2), 3, sourceArea, false),
			new PanelSlot<>(gearElement, 1, new ImmutableRect2i(18, 0, 18, 18), false)
		);

		var drag = FavoriteRecipeSortDragState.recipe(bolt, sourceArea, 4, 94, 0, 0);
		drag.update(store, state, slots, 4, mouseY, 1);

		var order = store.entries().stream().map(FavoriteRecipeStore.Entry::recipe).toList();
		Assertions.assertEquals(expectedIndex, order.indexOf(bolt));
		Assertions.assertTrue(order.indexOf(RECIPE) < order.indexOf(plate));
	}

	@Test
	public void cachesRecipeRows() {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipePanelState state = recipeRows();
		CountingInputsResolver resolver = new CountingInputsResolver(
			typed("plate"),
			typed("bolt")
		);
		FavoriteRecipeGridSource source = source(store, state, resolver);

		List<IElement<?>> elements = source.getElements(4);

		Assertions.assertEquals(4, elements.size());
		Assertions.assertEquals(1, resolver.calls);
		source.getElements(4);
		Assertions.assertEquals(1, resolver.calls);
	}

	@Test
	public void refreshesDragVisibility() {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipePanelState state = recipeRows();
		CountingInputsResolver resolver = new CountingInputsResolver(
			typed("plate"),
			typed("bolt")
		);
		FavoriteRecipeGridSource source = source(store, state, resolver);
		source.addSourceListChangedListener(() -> {});

		Assertions.assertTrue(source.getElements(4).getFirst().isVisible());

		state.setSortDragHiddenRecipe(RECIPE);
		Assertions.assertFalse(source.getElements(4).getFirst().isVisible());

		state.clearSortDragHiddenElements();
		Assertions.assertTrue(source.getElements(4).getFirst().isVisible());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void bookmarkRemovalWaitsForCompletionAndHonorsCancellation(boolean complete) {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipeGridSource source = source(store, new FavoriteRecipePanelState(), new CountingInputsResolver());
		FavoriteRecipeElement<?> element = (FavoriteRecipeElement<?>) source.getElements().getFirst();
		InputConstants.Key mouse = InputConstants.Type.MOUSE.getOrCreate(3);
		UserInput press = new UserInput(mouse, 0, 0, 0, InputPhase.PRESS);

		var interaction = element.prepareAction(press, bookmarkKeys(mouse)).orElseThrow();

		Assertions.assertTrue(store.getManualEntry(RECIPE).isPresent());
		if (complete) {
			interaction.complete(new UserInput(mouse, 0, 0, 0, InputPhase.RELEASE));
		} else {
			interaction.cancel();
		}
		Assertions.assertEquals(!complete, store.getManualEntry(RECIPE).isPresent());
	}

	private static IInternalKeyMappings bookmarkKeys(InputConstants.Key key) {
		IJeiKeyMappingWithExtraModifiers bookmark = new IJeiKeyMappingWithExtraModifiers() {
			@Override
			public boolean isActiveAndMatches(InputConstants.Key input) { return key.equals(input); }
			@Override
			public boolean isActiveAndMatchesAllowingExtraModifiers(InputConstants.Key input) { return key.equals(input); }
			@Override
			public boolean isUnbound() { return false; }
			@Override
			public Component getTranslatedKeyMessage() { return Component.empty(); }
		};
		return (IInternalKeyMappings) Proxy.newProxyInstance(FavoriteGridSourceTest.class.getClassLoader(),
			new Class<?>[]{IInternalKeyMappings.class}, (proxy, method, args) -> {
				if (method.getName().equals("getBookmark")) {
					return bookmark;
				}
				throw new UnsupportedOperationException(method.getName());
			});
	}

	private static FavoriteRecipeGridSource source(
		FavoriteRecipeStore store,
		FavoriteRecipePanelState state,
		CountingInputsResolver resolver
	) {
		return new FavoriteRecipeGridSource(
			store,
			state,
			ingredientManager(),
			null,
			null,
			resolver
		);
	}

	private static FavoriteRecipeStore store(String targetUid, FocusedRecipe recipe) {
		return store(TYPE, targetUid, recipe);
	}

	private static FavoriteRecipeStore store(IIngredientType<?> ingredientType, String targetUid, FocusedRecipe recipe) {
		FavoriteRecipeStore store = new FavoriteRecipeStore();
		store.setFavorite(BookmarkIngredientKey.of(ingredientType.getUid(), targetUid), recipe, Map.of());
		return store;
	}

	private static FavoriteRecipePanelState recipeRows() {
		FavoriteRecipePanelState state = new FavoriteRecipePanelState();
		state.showFavoritePanel();
		state.cycleDisplayMode();
		return state;
	}

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(
			ResourceLocation.fromNamespaceAndPath("test", "recipe_type"),
			ResourceLocation.fromNamespaceAndPath("test", uid)
		);
	}

	private static ITypedIngredient<String> typed(String ingredient) {
		return TypedIngredient.createUnvalidated(TYPE, ingredient);
	}

	private static class CountingInputsResolver implements FavoriteRecipeGridSource.RecipeInputsResolver {
		private final List<ITypedIngredient<?>> inputs;
		private int calls;

		private CountingInputsResolver(ITypedIngredient<?>... inputs) {
			this.inputs = List.of(inputs);
		}

		@Override
		public FavoriteRecipeGridSource.ResolvedRecipeIngredients resolveIngredients(
			FocusedRecipe recipe,
			ITypedIngredient<?> target,
			Map<Integer, FavoriteRecipeStore.FavoriteSlotInput> inputs
		) {
			calls++;
			return new FavoriteRecipeGridSource.ResolvedRecipeIngredients(target, this.inputs);
		}
	}

	private record TestIngredientType(String uid) implements IIngredientType<String> {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}

		@Override
		public String getUid() {
			return uid;
		}
	}

	private static IIngredientManager ingredientManager() {
		TestIngredientHelper helper = new TestIngredientHelper();
		return (IIngredientManager) Proxy.newProxyInstance(
			FavoriteGridSourceTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getIngredientHelper" -> helper;
				case "getIngredientTypeForUid" -> ingredientType((String) args[0]);
				case "getTypedIngredientByUid" -> typedIngredient((IIngredientType<?>) args[0], (String) args[1]);
				case "createTypedIngredient" -> typedIngredient((IIngredientType<?>) args[0], (String) args[1]);
				case "normalizeTypedIngredient" -> args[0];
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static Optional<IIngredientType<?>> ingredientType(String uid) {
		if (TYPE.getUid().equals(uid)) {
			return Optional.of(TYPE);
		}
		if (FLUID_TYPE.getUid().equals(uid)) {
			return Optional.of(FLUID_TYPE);
		}
		return Optional.empty();
	}

	private static Optional<ITypedIngredient<String>> typedIngredient(IIngredientType<?> type, String uid) {
		if (type == TYPE) {
			return Optional.of(TypedIngredient.createUnvalidated(TYPE, uid));
		}
		if (type == FLUID_TYPE) {
			return Optional.of(TypedIngredient.createUnvalidated(FLUID_TYPE, uid));
		}
		return Optional.empty();
	}

	private static class TestIngredientHelper implements IIngredientHelper<String> {
		@Override
		public IIngredientType<String> getIngredientType() {
			return TYPE;
		}

		@Override
		public String getDisplayName(String ingredient) {
			return ingredient;
		}

		@Override
		public String getUniqueId(String ingredient, UidContext context) {
			return ingredient;
		}

		@Override
		public ResourceLocation getResourceLocation(String ingredient) {
			return ResourceLocation.fromNamespaceAndPath("test", ingredient);
		}

		@Override
		public String copyIngredient(String ingredient) {
			return ingredient;
		}

		@Override
		public String getErrorInfo(@Nullable String ingredient) {
			return String.valueOf(ingredient);
		}

		@Override
		public long getAmount(String ingredient) {
			return "water".equals(ingredient) ? 1000 : 1;
		}
	}

}
