package mezz.jei.test.gui.favorites;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.favorites.FavoriteTreeBuilder;
import mezz.jei.gui.favorites.FavoriteTreeRecipeLayoutResolver;
import mezz.jei.gui.favorites.RecipeLayoutBuildCache;
import mezz.jei.gui.favorites.RecipePreferenceCandidateResolver;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeLayout;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.typed;

public class FavoriteTreeRecipeLayoutResolverTest {
	private static final ResourceLocation RECIPE_UID = ResourceLocation.fromNamespaceAndPath("test", "assembler");
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager();

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void resolveInputsExcludesGtmNonConsumableInputs() {
		ItemStack mold = new ItemStack(Items.SHEARS);
		TestRecipeLayout layout = layout(
			new GTRecipe(7, mold),
			List.of(item(Items.IRON_INGOT), typed(mold)),
			List.of(item(Items.GOLD_INGOT))
		);
		FavoriteTreeRecipeLayoutResolver resolver = new FavoriteTreeRecipeLayoutResolver(
			recipeManager(layout),
			focusFactory(),
			INGREDIENT_MANAGER
		);

		List<FavoriteTreeBuilder.ResolvedInput> inputs = resolver.resolve(new FocusedRecipe(
			layout.category().getRecipeType().getUid(),
			RECIPE_UID
		))
			.orElseThrow().inputs();

		Assertions.assertEquals(1, inputs.size());
		Assertions.assertEquals("minecraft:iron_ingot", inputs.get(0).displayedKey().ingredientUid());
	}

	@Test
	public void resolveInputsKeepOriginalSlotIndices() {
		TestRecipeLayout layout = layout(
			new Object(),
			java.util.Arrays.<ITypedIngredient<?>>asList(item(Items.IRON_INGOT), null, item(Items.GOLD_INGOT)),
			List.of(item(Items.DIAMOND))
		);
		FavoriteTreeRecipeLayoutResolver resolver = new FavoriteTreeRecipeLayoutResolver(
			recipeManager(layout),
			focusFactory(),
			INGREDIENT_MANAGER
		);

		List<FavoriteTreeBuilder.ResolvedInput> inputs = resolver.resolve(new FocusedRecipe(
			layout.category().getRecipeType().getUid(),
			RECIPE_UID
		))
			.orElseThrow().inputs();

		Assertions.assertEquals(2, inputs.size());
		Assertions.assertEquals(0, inputs.get(0).inputSlotIndex());
		Assertions.assertEquals("minecraft:iron_ingot", inputs.get(0).displayedKey().ingredientUid());
		Assertions.assertEquals(2, inputs.get(1).inputSlotIndex());
		Assertions.assertEquals("minecraft:gold_ingot", inputs.get(1).displayedKey().ingredientUid());
	}

	@Test
	public void resolveReusesLayoutFromBuildCache() {
		TestRecipeLayout cachedLayout = layout(
			new Object(),
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);
		TestRecipeLayout managerLayout = layout(
			new Object(),
			List.of(item(Items.IRON_INGOT)),
			List.of(item(Items.GOLD_INGOT))
		);
		FavoriteTreeRecipeLayoutResolver resolver = new FavoriteTreeRecipeLayoutResolver(
			recipeManager(managerLayout),
			focusFactory(),
			INGREDIENT_MANAGER
		);
		FocusedRecipe focusedRecipe = new FocusedRecipe(
			cachedLayout.category().getRecipeType().getUid(),
			RECIPE_UID
		);
		RecipeLayoutBuildCache layoutCache = new RecipeLayoutBuildCache();
		layoutCache.put(focusedRecipe, cachedLayout);

		FavoriteTreeBuilder.ResolvedRecipe resolved = resolver.resolve(focusedRecipe, layoutCache).orElseThrow();

		Assertions.assertSame(cachedLayout, resolved.layout().orElseThrow());
		Assertions.assertEquals("minecraft:iron_ingot", resolved.inputs().get(0).displayedKey().ingredientUid());
	}

	@Test
	public void candidateScanPopulatesLayoutBuildCache() {
		TestRecipeLayout layout = layout(new Object(), List.of(), List.of());
		IRecipeManager recipeManager = recipeManager(layout);
		RecipePreferenceCandidateResolver resolver = RecipePreferenceCandidateResolver.create(
			recipeManager,
			focusFactory(),
			INGREDIENT_MANAGER,
			() -> RecipePreferenceRules.EMPTY
		);
		FocusedRecipe focusedRecipe = new FocusedRecipe(
			layout.category().getRecipeType().getUid(),
			RECIPE_UID
		);
		BookmarkIngredientKey outputKey = BookmarkItemMetadataFactory.createPermutationKey(
			item(Items.GOLD_INGOT),
			INGREDIENT_MANAGER
		);
		RecipeLayoutBuildCache layoutCache = new RecipeLayoutBuildCache();

		resolver.getCandidates(outputKey, item(Items.GOLD_INGOT), layoutCache);

		Assertions.assertSame(layout, layoutCache.get(focusedRecipe).orElseThrow());
	}

	private static IRecipeManager recipeManager(TestRecipeLayout layout) {
		IRecipeLookup<Object> lookup = new IRecipeLookup<>() {
			@Override
			public IRecipeLookup<Object> limitFocus(java.util.Collection<? extends mezz.jei.api.recipe.IFocus<?>> focuses) {
				return this;
			}

			@Override
			public IRecipeLookup<Object> includeHidden() {
				return this;
			}

			@Override
			public Stream<Object> get() {
				return Stream.of(layout.recipe());
			}
		};
		return (IRecipeManager) Proxy.newProxyInstance(
			FavoriteTreeRecipeLayoutResolverTest.class.getClassLoader(),
			new Class<?>[]{IRecipeManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeType" -> Optional.of(layout.category().getRecipeType());
				case "getRecipeCategory" -> layout.category();
				case "createRecipeLookup" -> lookup;
				case "createRecipeCategoryLookup" -> recipeCategoryLookup(layout);
				case "createRecipeLayoutDrawable" -> Optional.of(layout);
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IRecipeCategoriesLookup recipeCategoryLookup(TestRecipeLayout layout) {
		return new IRecipeCategoriesLookup() {
			@Override
			public IRecipeCategoriesLookup limitTypes(Collection<RecipeType<?>> recipeTypes) {
				return this;
			}

			@Override
			public IRecipeCategoriesLookup limitFocus(Collection<? extends IFocus<?>> focuses) {
				return this;
			}

			@Override
			public IRecipeCategoriesLookup includeHidden() {
				return this;
			}

			@Override
			public Stream<IRecipeCategory<?>> get() {
				return Stream.of(layout.category());
			}
		};
	}

	private static IFocusFactory focusFactory() {
		return (IFocusFactory) Proxy.newProxyInstance(
			FavoriteTreeRecipeLayoutResolverTest.class.getClassLoader(),
			new Class<?>[]{IFocusFactory.class},
			(proxy, method, args) -> {
				if ("getEmptyFocusGroup".equals(method.getName())) {
					return emptyFocusGroup();
				}
				if ("createFocus".equals(method.getName())) {
					return dummyFocus();
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static IFocus<?> dummyFocus() {
		return (IFocus<?>) Proxy.newProxyInstance(
			FavoriteTreeRecipeLayoutResolverTest.class.getClassLoader(),
			new Class<?>[]{IFocus.class},
			(proxy, method, args) -> {
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IFocusGroup emptyFocusGroup() {
		return new IFocusGroup() {
			@Override
			public boolean isEmpty() {
				return true;
			}

			@Override
			public List<IFocus<?>> getAllFocuses() {
				return List.of();
			}

			@Override
			public Stream<IFocus<?>> getFocuses(RecipeIngredientRole role) {
				return Stream.empty();
			}

			@Override
			public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType) {
				return Stream.empty();
			}

			@Override
			public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType, RecipeIngredientRole role) {
				return Stream.empty();
			}
		};
	}

	private static TestRecipeLayout layout(
		Object recipe,
		List<@Nullable ITypedIngredient<?>> inputs,
		List<@Nullable ITypedIngredient<?>> outputs
	) {
		return RecipeLayoutTestFixtures.singleIngredientLayout(
			RecipeType.create("gtceu", "assembler", Object.class),
			recipe,
			RECIPE_UID,
			inputs,
			outputs
		);
	}
}
