package mezz.jei.test.gui.favorites;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientManager.IIngredientListener;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeGridSource;
import mezz.jei.gui.favorites.FavoriteRecipeElement;
import mezz.jei.gui.favorites.FavoriteRecipePanelState;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.Codec;

import java.util.Collection;
import java.util.List;
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
	}

	@Test
	public void recipeRowsReuseCachedElementsForTheSameColumnCount() {
		FavoriteRecipeStore store = store("gear", RECIPE);
		FavoriteRecipePanelState panelState = recipeRows();
		CountingRecipeInputsResolver resolver = new CountingRecipeInputsResolver(
			typed("plate"),
			typed("bolt")
		);
		FavoriteRecipeGridSource source = source(store, panelState, resolver);

		source.getElements(4);
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
			new TestIngredientManager(),
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
		store.setFavorite(BookmarkIngredientKey.of(ingredientType.getUid(), targetUid), recipe);
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
		return new TestTypedIngredient(TYPE, ingredient);
	}

	private static class CountingRecipeInputsResolver implements FavoriteRecipeGridSource.RecipeInputsResolver {
		private final List<ITypedIngredient<?>> inputs;
		private int calls;

		private CountingRecipeInputsResolver(ITypedIngredient<?>... inputs) {
			this.inputs = List.of(inputs);
		}

		@Override
		public FavoriteRecipeGridSource.ResolvedRecipeIngredients resolveIngredients(FocusedRecipe recipe, ITypedIngredient<?> target) {
			calls++;
			return new FavoriteRecipeGridSource.ResolvedRecipeIngredients(target, inputs);
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

	private static class TestIngredientManager implements IIngredientManager {
		private final TestIngredientHelper helper = new TestIngredientHelper();

		@Override
		public <V> Collection<V> getAllIngredients(IIngredientType<V> ingredientType) {
			return List.of();
		}

		@Override
		public <V> Collection<ITypedIngredient<V>> getAllTypedIngredients(IIngredientType<V> ingredientType) {
			return List.of();
		}

		@Override
		public <V> IIngredientHelper<V> getIngredientHelper(V ingredient) {
			return castHelper();
		}

		@Override
		public <V> IIngredientHelper<V> getIngredientHelper(IIngredientType<V> ingredientType) {
			return castHelper();
		}

		@Override
		public <V> IIngredientRenderer<V> getIngredientRenderer(V ingredient) {
			throw unsupported();
		}

		@Override
		public <V> IIngredientRenderer<V> getIngredientRenderer(IIngredientType<V> ingredientType) {
			throw unsupported();
		}

		@Override
		public <V> Codec<V> getIngredientCodec(IIngredientType<V> ingredientType) {
			throw unsupported();
		}

		@Override
		public Collection<IIngredientType<?>> getRegisteredIngredientTypes() {
			return List.of(TYPE, FLUID_TYPE);
		}

		@Override
		public Optional<IIngredientType<?>> getIngredientTypeForUid(String ingredientTypeUid) {
			if (TYPE.getUid().equals(ingredientTypeUid)) {
				return Optional.of(TYPE);
			}
			if (FLUID_TYPE.getUid().equals(ingredientTypeUid)) {
				return Optional.of(FLUID_TYPE);
			}
			return Optional.empty();
		}

		@Override
		public <V> void addIngredientsAtRuntime(IIngredientType<V> ingredientType, Collection<V> ingredients) {
			throw unsupported();
		}

		@Override
		public <V> void removeIngredientsAtRuntime(IIngredientType<V> ingredientType, Collection<V> ingredients) {
			throw unsupported();
		}

		@Override
		public @Nullable <V> IIngredientType<V> getIngredientType(V ingredient) {
			if (ingredient instanceof String) {
				@SuppressWarnings("unchecked")
				IIngredientType<V> cast = (IIngredientType<V>) TYPE;
				return cast;
			}
			return null;
		}

		@Override
		public <V> Optional<IIngredientType<V>> getIngredientTypeChecked(V ingredient) {
			return Optional.ofNullable(getIngredientType(ingredient));
		}

		@Override
		public <V> Optional<IIngredientType<V>> getIngredientTypeChecked(Class<? extends V> ingredientClass) {
			if (ingredientClass == String.class) {
				@SuppressWarnings("unchecked")
				IIngredientType<V> cast = (IIngredientType<V>) TYPE;
				return Optional.of(cast);
			}
			return Optional.empty();
		}

		@Override
		public <B, I> Optional<IIngredientTypeWithSubtypes<B, I>> getIngredientTypeWithSubtypesFromBase(B baseIngredient) {
			return Optional.empty();
		}

		@Override
		public <V> Optional<ITypedIngredient<V>> createTypedIngredient(IIngredientType<V> ingredientType, V ingredient, boolean normalize) {
			if (ingredient instanceof String string) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<V> cast = (ITypedIngredient<V>) typed(string);
				return Optional.of(cast);
			}
			return Optional.empty();
		}

		@Override
		public <V> ITypedIngredient<V> normalizeTypedIngredient(ITypedIngredient<V> typedIngredient) {
			return typedIngredient;
		}

		@Override
		public IClickableIngredientFactory getClickableIngredientFactory() {
			throw unsupported();
		}

		@Override
		public <V> Optional<IClickableIngredient<V>> createClickableIngredient(IIngredientType<V> ingredientType, V ingredient, Rect2i area, boolean normalize) {
			throw unsupported();
		}

		@Override
		public <V> Optional<V> getIngredientByUid(IIngredientType<V> ingredientType, String ingredientUuid) {
			if (ingredientType == TYPE || ingredientType == FLUID_TYPE) {
				@SuppressWarnings("unchecked")
				V cast = (V) ingredientUuid;
				return Optional.of(cast);
			}
			return Optional.empty();
		}

		@Override
		public <V> Optional<ITypedIngredient<V>> getTypedIngredientByUid(IIngredientType<V> ingredientType, String ingredientUuid) {
			if (ingredientType == TYPE || ingredientType == FLUID_TYPE) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<V> cast = (ITypedIngredient<V>) new TestTypedIngredient(ingredientType, ingredientUuid);
				return Optional.of(cast);
			}
			return Optional.empty();
		}

		@Override
		public Collection<String> getIngredientAliases(ITypedIngredient<?> ingredient) {
			return List.of();
		}

		@Override
		public void registerIngredientListener(IIngredientListener listener) {
		}

		@SuppressWarnings("unchecked")
		private <V> IIngredientHelper<V> castHelper() {
			return (IIngredientHelper<V>) helper;
		}
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

	private static UnsupportedOperationException unsupported() {
		return new UnsupportedOperationException("not needed for this test");
	}
}
