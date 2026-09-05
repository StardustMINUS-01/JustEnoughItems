package mezz.jei.test.gui.favorites;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeGridSource;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FavoriteRecipeGridSourceTest {
	private static final TestIngredientType TYPE = new TestIngredientType();
	private static final TestFluidIngredientType FLUID_TYPE = new TestFluidIngredientType();
	private static final FocusedRecipe RECIPE = recipe("gear");

	@Test
	public void gridResultsDoNotResolveRecipeLayoutsWhenCreatingElements() {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipePanelState panelState = new FavoriteRecipePanelState();
		CountingRecipeInputsResolver resolver = new CountingRecipeInputsResolver();
		FavoriteRecipeGridSource source = source(store, panelState, resolver);

		List<IElement<?>> elements = source.getElements();

		Assertions.assertEquals(1, elements.size());
		Assertions.assertEquals(0, resolver.calls);
	}

	@Test
	public void defaultGridFavoriteTargetsDoNotShowItemAmountOne() {
		FavoriteRecipeGridSource source = source(store("gear", RECIPE), new FavoriteRecipePanelState(), new CountingRecipeInputsResolver());

		FavoriteRecipeElement<?> element = (FavoriteRecipeElement<?>) source.getElements().getFirst();

		Assertions.assertTrue(element.getAmountText().isEmpty());
	}

	@Test
	public void defaultGridFavoriteTargetsDoNotShowFluidAmountOneBucket() {
		FavoriteRecipeGridSource source = source(store(FLUID_TYPE, "water", RECIPE), new FavoriteRecipePanelState(), new CountingRecipeInputsResolver());

		FavoriteRecipeElement<?> element = (FavoriteRecipeElement<?>) source.getElements().getFirst();

		Assertions.assertTrue(element.getAmountText().isEmpty());
	}

	@Test
	public void recipeRowsResolveIngredientsOncePerFavoriteWhenCreatingElements() {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipePanelState panelState = recipeRows();
		CountingRecipeInputsResolver resolver = new CountingRecipeInputsResolver(
			typed("plate"),
			typed("bolt")
		);
		FavoriteRecipeGridSource source = source(store, panelState, resolver);

		List<IElement<?>> elements = source.getElements(4);

		Assertions.assertEquals(4, elements.size());
		Assertions.assertEquals(1, resolver.calls);
		source.getElements(4);
		Assertions.assertEquals(1, resolver.calls);
	}

	@Test
	public void recipeRowsRefreshWhenSortDragHiddenStateChanges() {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipePanelState panelState = recipeRows();
		CountingRecipeInputsResolver resolver = new CountingRecipeInputsResolver(
			typed("plate"),
			typed("bolt")
		);
		FavoriteRecipeGridSource source = source(store, panelState, resolver);
		source.addSourceListChangedListener(() -> {});

		Assertions.assertTrue(source.getElements(4).getFirst().isVisible());

		panelState.setSortDragHiddenRecipe(RECIPE);
		Assertions.assertFalse(source.getElements(4).getFirst().isVisible());

		panelState.clearSortDragHiddenElements();
		Assertions.assertTrue(source.getElements(4).getFirst().isVisible());
	}

	private static FavoriteRecipeGridSource source(
		FavoriteRecipeStore store,
		FavoriteRecipePanelState panelState,
		CountingRecipeInputsResolver resolver
	) {
		return new FavoriteRecipeGridSource(
			store,
			panelState,
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
		FavoriteRecipePanelState panelState = new FavoriteRecipePanelState();
		panelState.showFavoritePanel();
		panelState.cycleDisplayMode();
		return panelState;
	}

	private static FocusedRecipe recipe(String uid) {
		return new FocusedRecipe(
			ResourceLocation.fromNamespaceAndPath("test", "recipe_type"),
			ResourceLocation.fromNamespaceAndPath("test", uid)
		);
	}

	private static TestTypedIngredient<String> typed(String ingredient) {
		return new TestTypedIngredient<>(TYPE, ingredient);
	}

	private static class CountingRecipeInputsResolver implements FavoriteRecipeGridSource.RecipeInputsResolver {
		private final List<ITypedIngredient<?>> inputs;
		private int calls;

		private CountingRecipeInputsResolver(ITypedIngredient<?>... inputs) {
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

	private record TestIngredientType() implements IIngredientType<String> {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}

		@Override
		public String getUid() {
			return "test:ingredient";
		}
	}

	private record TestFluidIngredientType() implements IIngredientType<String> {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}

		@Override
		public String getUid() {
			return "fluid_stack";
		}
	}

	private record TestTypedIngredient<V>(IIngredientType<V> type, V ingredient) implements ITypedIngredient<V> {
		@Override
		public IIngredientType<V> getType() {
			return type;
		}

		@Override
		public V getIngredient() {
			return ingredient;
		}
	}

	private static IIngredientManager ingredientManager() {
		TestIngredientHelper helper = new TestIngredientHelper();
		return (IIngredientManager) Proxy.newProxyInstance(
			FavoriteRecipeGridSourceTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getIngredientHelper" -> helper;
				case "getIngredientTypeForUid" -> ingredientType((String) args[0]);
				case "getIngredientByUid" -> ingredient((IIngredientType<?>) args[0], (String) args[1]);
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

	private static Optional<?> ingredient(IIngredientType<?> type, String uid) {
		return type == TYPE || type == FLUID_TYPE ? Optional.of(uid) : Optional.empty();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Optional<?> typedIngredient(IIngredientType<?> type, String uid) {
		return type == TYPE || type == FLUID_TYPE ?
			Optional.of(new TestTypedIngredient(type, uid)) :
			Optional.empty();
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
