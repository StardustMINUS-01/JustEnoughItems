package mezz.jei.test.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

public class BookmarkItemMetadataFactoryTest {
	private static final IIngredientType<TestToolIngredient> TOOL_TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends TestToolIngredient> getIngredientClass() {
			return TestToolIngredient.class;
		}

		@Override
		public String getUid() {
			return "test:tool";
		}
	};
	private static final ResourceLocation CRAFTING = new ResourceLocation("minecraft", "crafting");
	private static final ResourceLocation MACHINE_RECIPE = new ResourceLocation("test", "machine");

	@Test
	public void craftingAvailableMetadataKeepsReusableToolUses() {
		IIngredientManager ingredientManager = ingredientManager();
		TestToolIngredient wrench = new TestToolIngredient("neutronium_wrench", 1, 0, 10_000, 2);
		BookmarkItemMetadata inventoryMetadata = BookmarkItemMetadataFactory.createForCraftingAvailable(
			0,
			typed(wrench),
			1,
			ingredientManager
		);

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(key("machine"), 1)),
			input(1, ingredient(key("neutronium_wrench"), 5)),
			input(2, inventoryMetadata)
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(1).requiredAmount());
	}

	@Test
	public void syntheticVirtualCircuitRecipeInputHasZeroCost() {
		IIngredientManager ingredientManager = ingredientManager();
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForSyntheticRecipeInput(
			0,
			CRAFTING,
			MACHINE_RECIPE,
			BookmarkItemType.INGREDIENT,
			typed(new TestToolIngredient("programmed_circuit_7", 1, 0, 0, 0)),
			ingredientManager,
			0
		);

		Assertions.assertEquals(BookmarkItemType.INGREDIENT, metadata.type());
		Assertions.assertEquals(0, metadata.factor());
		Assertions.assertEquals(0, metadata.amount());
		Assertions.assertEquals(CRAFTING, metadata.recipeTypeUid());
		Assertions.assertEquals(MACHINE_RECIPE, metadata.recipeUid());
	}

	@Test
	public void replacingPermutationsPreservesCatalystType() {
		BookmarkItemMetadata catalyst = ingredient(key("mold"), 1).withType(BookmarkItemType.CATALYST);

		BookmarkItemMetadata replaced = catalyst.withPermutations(Set.of(key("hydrated_mold")));

		Assertions.assertEquals(BookmarkItemType.CATALYST, replaced.type());
		Assertions.assertEquals(Set.of(key("hydrated_mold")), replaced.permutations());
	}

	@Test
	public void catalystTypeIgnoresEveryMultiplier() {
		BookmarkItemType catalystType = BookmarkItemType.valueOf("CATALYST");
		BookmarkItemMetadata catalyst = new BookmarkItemMetadata(
			0,
			catalystType,
			64,
			100,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			MACHINE_RECIPE,
			Set.of(key("ferric_chloride"))
		);

		Assertions.assertEquals(100, catalyst.amount());
		Assertions.assertEquals(100, catalyst.amount(9));
	}

	@Test
	public void bookmarkItemTypesExposeTheirRecipeAndGraphCapabilities() {
		Assertions.assertFalse(BookmarkItemType.ITEM.isRecipeAssociated());
		Assertions.assertNull(BookmarkItemType.ITEM.recipeRole());
		Assertions.assertFalse(BookmarkItemType.ITEM.isGraphInput());
		Assertions.assertFalse(BookmarkItemType.ITEM.isGraphOutput());

		Assertions.assertTrue(BookmarkItemType.RESULT.isRecipeAssociated());
		Assertions.assertEquals(RecipeIngredientRole.OUTPUT, BookmarkItemType.RESULT.recipeRole());
		Assertions.assertTrue(BookmarkItemType.RESULT.isGraphOutput());

		Assertions.assertTrue(BookmarkItemType.INGREDIENT.isRecipeAssociated());
		Assertions.assertEquals(RecipeIngredientRole.INPUT, BookmarkItemType.INGREDIENT.recipeRole());
		Assertions.assertTrue(BookmarkItemType.INGREDIENT.isGraphInput());

		Assertions.assertTrue(BookmarkItemType.CATALYST.isRecipeAssociated());
		Assertions.assertEquals(RecipeIngredientRole.INPUT, BookmarkItemType.CATALYST.recipeRole());
		Assertions.assertTrue(BookmarkItemType.CATALYST.isCatalyst());
		Assertions.assertFalse(BookmarkItemType.CATALYST.isGraphInput());
		Assertions.assertFalse(BookmarkItemType.CATALYST.scalesWithMultiplier());
	}

	@Test
	public void syntheticCatalystIsCreatedAsACatalyst() {
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForSyntheticRecipeInput(
			0,
			CRAFTING,
			MACHINE_RECIPE,
			BookmarkItemType.CATALYST,
			typed(new TestToolIngredient("mold", 3, 0, 0, 0)),
			ingredientManager(),
			3
		);

		Assertions.assertEquals(BookmarkItemType.CATALYST, metadata.type());
		Assertions.assertEquals(3, metadata.amount(9));
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata result(BookmarkIngredientKey key, long multiplier) {
		return new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.RESULT, multiplier, 1, BookmarkItemMetadata.CHANCE_FULL, CRAFTING, MACHINE_RECIPE, Set.of(key));
	}

	private static BookmarkItemMetadata ingredient(BookmarkIngredientKey key, long factor) {
		return new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT, 1, factor, BookmarkItemMetadata.CHANCE_FULL, CRAFTING, MACHINE_RECIPE, Set.of(key), key, 5_000);
	}

	private static BookmarkIngredientKey key(String name) {
		return new BookmarkIngredientKey(TOOL_TYPE.getUid(), "test:" + name, null);
	}

	private static ITypedIngredient<TestToolIngredient> typed(TestToolIngredient ingredient) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<TestToolIngredient> getType() {
				return TOOL_TYPE;
			}

			@Override
			public TestToolIngredient getIngredient() {
				return ingredient;
			}
		};
	}

	private static IIngredientManager ingredientManager() {
		IIngredientHelper<TestToolIngredient> helper = new IIngredientHelper<>() {
			@Override
			public IIngredientType<TestToolIngredient> getIngredientType() {
				return TOOL_TYPE;
			}

			@Override
			public String getDisplayName(TestToolIngredient ingredient) {
				return ingredient.name();
			}

			@Override
			public String getUniqueId(TestToolIngredient ingredient, UidContext context) {
				return "test:" + ingredient.name();
			}

			@Override
			public ResourceLocation getResourceLocation(TestToolIngredient ingredient) {
				return new ResourceLocation("test", ingredient.name());
			}

			@Override
			public TestToolIngredient copyIngredient(TestToolIngredient ingredient) {
				return ingredient;
			}

			@Override
			public String getErrorInfo(TestToolIngredient ingredient) {
				return String.valueOf(ingredient);
			}
		};

		return (IIngredientManager) Proxy.newProxyInstance(
			IIngredientManager.class.getClassLoader(),
			new Class[]{IIngredientManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getIngredientHelper" -> helper;
				case "normalizeTypedIngredient" -> args[0];
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private record TestToolIngredient(String name, long amount, int damage, int maxDamage, int damagePerCraft) {
		public boolean hasCraftingRemainingItem() {
			return true;
		}

		public TestToolIngredient getCraftingRemainingItem() {
			return new TestToolIngredient(name, 1, damage + damagePerCraft, maxDamage, damagePerCraft);
		}

		public TestToolItem getItem() {
			return new TestToolItem(damagePerCraft);
		}

		public int getMaxDamage() {
			return maxDamage;
		}

		public int getDamageValue() {
			return damage;
		}
	}

	private record TestToolItem(int damagePerCraft) {
		public TestToolStats getToolStats() {
			return new TestToolStats(damagePerCraft);
		}

		public boolean isElectric() {
			return false;
		}
	}

	private record TestToolStats(int damagePerCraft) {
		public int getDamagePerCraftingAction(TestToolIngredient stack) {
			return damagePerCraft;
		}
	}
}
