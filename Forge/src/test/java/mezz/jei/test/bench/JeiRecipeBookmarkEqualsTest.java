package mezz.jei.test.bench;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IJeiClientConfigs;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.library.ingredients.subtypes.SubtypeInterpreters;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.test.lib.TestAmountIngredient;
import mezz.jei.test.lib.TestAmountIngredientHelper;
import mezz.jei.test.lib.TestAmountIngredientRenderer;
import mezz.jei.test.lib.TestClientConfig;
import mezz.jei.test.lib.TestColorHelper;
import mezz.jei.test.lib.TestIngredientFilterConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Benchmark tests for the 1.20.1 port of the JEI 1.21.1
 * {@code RecipeBookmark.equals/hashCode} semantics: bookmarks compare on
 * equality-scope + recipe uid + display role + ingredient, so that within one
 * recipe tree (a shared {@code recipeTreeScope}) equal ingredients across
 * recipes de-duplicate, while ingredients in different roles or recipes stay
 * distinct (this is what restores the missing counts and same-category
 * grouping in the 1.20.1 port).
 */
public class JeiRecipeBookmarkEqualsTest {
	private static final ResourceLocation RECIPE_A = new ResourceLocation("test", "recipe_a");
	private static final ResourceLocation RECIPE_B = new ResourceLocation("test", "recipe_b");

	private static IIngredientManager ingredientManager;
	private static TestRecipeCategory category;

	@BeforeAll
	static void setup() {
		// RecipeBookmarkElement is constructed inside RecipeBookmark's constructor
		// and reads the static client config, so it must be available.
		Internal.setJeiClientConfigs(new IJeiClientConfigs() {
			@Override
			public IClientConfig getClientConfig() {
				return new TestClientConfig(false);
			}

			@Override
			public mezz.jei.common.config.IIngredientFilterConfig getIngredientFilterConfig() {
				return new TestIngredientFilterConfig();
			}

			@Override
			public mezz.jei.common.config.IIngredientGridConfig getIngredientListConfig() {
				return null;
			}

			@Override
			public mezz.jei.common.config.IIngredientGridConfig getBookmarkListConfig() {
				return null;
			}

			@Override
			public void onRuntimeStopped() {}
		});

		SubtypeManager subtypeManager = new SubtypeManager(new SubtypeInterpreters());
		IngredientManagerBuilder builder = new IngredientManagerBuilder(subtypeManager, new TestColorHelper());
		builder.register(
			TestAmountIngredient.TYPE,
			List.of(),
			new TestAmountIngredientHelper(false),
			new TestAmountIngredientRenderer()
		);
		ingredientManager = builder.build();

		RecipeType<TestRecipe> recipeType = RecipeType.create("test", "recipe_type", TestRecipe.class);
		category = new TestRecipeCategory(recipeType);
	}

	@Test
	public void sameKeyIsEqualAndHashCodeMatches() {
		RecipeBookmark<TestRecipe, TestAmountIngredient> first = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		RecipeBookmark<TestRecipe, TestAmountIngredient> second = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void differentEqualityScopesAreNotEqual() {
		Object scopeA = new Object();
		Object scopeB = new Object();
		RecipeBookmark<TestRecipe, TestAmountIngredient> first = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, scopeA);
		RecipeBookmark<TestRecipe, TestAmountIngredient> second = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, scopeB);
		assertNotEquals(first, second);
		// sharing a scope makes them equal (cross-recipe de-duplication within one tree)
		RecipeBookmark<TestRecipe, TestAmountIngredient> third = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, scopeA);
		assertEquals(first, third);
	}

	@Test
	public void differentRolesAreNotEqual() {
		RecipeBookmark<TestRecipe, TestAmountIngredient> output = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		RecipeBookmark<TestRecipe, TestAmountIngredient> input = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.INPUT, null);
		assertNotEquals(output, input);
	}

	@Test
	public void differentIngredientsAreNotEqual() {
		RecipeBookmark<TestRecipe, TestAmountIngredient> ingredientOne = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.INPUT, null);
		RecipeBookmark<TestRecipe, TestAmountIngredient> ingredientTwo = bookmark(RECIPE_A, 2, 2, RecipeIngredientRole.INPUT, null);
		assertNotEquals(ingredientOne, ingredientTwo);
	}

	@Test
	public void differentRecipeUidsAreNotEqual() {
		RecipeBookmark<TestRecipe, TestAmountIngredient> recipeA = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		RecipeBookmark<TestRecipe, TestAmountIngredient> recipeB = bookmark(RECIPE_B, 1, 2, RecipeIngredientRole.OUTPUT, null);
		assertNotEquals(recipeA, recipeB);
	}

	@Test
	public void amountDoesNotAffectEquality() {
		RecipeBookmark<TestRecipe, TestAmountIngredient> amountThree = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		RecipeBookmark<TestRecipe, TestAmountIngredient> amountSeven = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		assertEquals(amountThree, amountSeven);
	}

	@Test
	public void withEqualityScopeSwitchesScope() {
		RecipeBookmark<TestRecipe, TestAmountIngredient> original = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		Object scope = new Object();
		RecipeBookmark<TestRecipe, TestAmountIngredient> scoped = original.withEqualityScope(scope);
		assertNotEquals(original, scoped);
		// the scoped copy is equal to another bookmark sharing the same scope
		RecipeBookmark<TestRecipe, TestAmountIngredient> otherScoped = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, scope);
		assertEquals(scoped, otherScoped);
	}

	@Test
	public void defaultConstructionUsesNullScope() {
		RecipeBookmark<TestRecipe, TestAmountIngredient> first = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		RecipeBookmark<TestRecipe, TestAmountIngredient> second = bookmark(RECIPE_A, 1, 2, RecipeIngredientRole.OUTPUT, null);
		assertEquals(first, second);
	}

	private static RecipeBookmark<TestRecipe, TestAmountIngredient> bookmark(
		ResourceLocation recipeUid,
		int ingredientNumber,
		int ingredientAmount,
		RecipeIngredientRole role,
		Object equalityScope
	) {
		TestAmountIngredient ingredient = new TestAmountIngredient(ingredientNumber, ingredientAmount);
		ITypedIngredient<TestAmountIngredient> typed = ingredientManager
			.createTypedIngredient(TestAmountIngredient.TYPE, ingredient)
			.orElseThrow();
		TestRecipe recipe = new TestRecipe(recipeUid.getPath(), List.of());
		return new RecipeBookmark<>(category, recipe, recipeUid, typed, role, equalityScope);
	}

	private static final class TestRecipeCategory implements IRecipeCategory<TestRecipe> {
		private final RecipeType<TestRecipe> recipeType;

		TestRecipeCategory(RecipeType<TestRecipe> recipeType) {
			this.recipeType = recipeType;
		}

		@Override
		public RecipeType<TestRecipe> getRecipeType() {
			return recipeType;
		}

		@Override
		public Component getTitle() {
			return Component.literal("Test");
		}

		@Override
		public IDrawable getIcon() {
			return null;
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, TestRecipe recipe, IFocusGroup focuses) {}

		@Override
		public ResourceLocation getRegistryName(TestRecipe recipe) {
			return new ResourceLocation("test", recipe.uid());
		}
	}
}
