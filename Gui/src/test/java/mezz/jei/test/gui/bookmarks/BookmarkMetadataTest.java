package mezz.jei.test.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class BookmarkMetadataTest {
	private static final IIngredientType<ToolIngredient> TOOL_TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends ToolIngredient> getIngredientClass() {
			return ToolIngredient.class;
		}

		@Override
		public String getUid() {
			return "test:tool";
		}
	};
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final IIngredientManager MANAGER = createManager();
	private static final ResourceLocation MACHINE_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "machine");

	@Test
	public void calculatesChanceAmounts() {
		var ingredient = new BookmarkItemMetadata(BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT, 2, 3, 5_000, CRAFTING, MACHINE_RECIPE, Set.of(new BookmarkIngredientKey("test:item", "gear")));
		var result = new BookmarkItemMetadata(BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.RESULT, 2, 3, 5_000, CRAFTING, MACHINE_RECIPE, Set.of(new BookmarkIngredientKey("test:item", "machine")));

		Assertions.assertEquals(3, ingredient.amount());
		Assertions.assertEquals(3, result.amount());
		Assertions.assertEquals(2, ingredient.multiplierFromAmount(3));
		Assertions.assertTrue(ingredient.containsItems(ingredient));
		Assertions.assertTrue(ingredient.equalsRecipe(MACHINE_RECIPE, BookmarkGroupManager.DEFAULT_GROUP_ID));
	}

	@Test
	public void countsToolUses() {
		ToolIngredient wrench = new ToolIngredient("neutronium_wrench", 1, 0, 10_000, 2);
		BookmarkItemMetadata available = BookmarkItemMetadataFactory.createForCraftingAvailable(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			typed(wrench),
			1,
			MANAGER
		);

		BookmarkIngredientKey tool = key("neutronium_wrench");
		BookmarkItemMetadata required = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.INGREDIENT, 1, 5,
			BookmarkItemMetadata.CHANCE_FULL, CRAFTING, MACHINE_RECIPE, Set.of(tool), tool, 5_000
		);
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(key("machine"), 1)),
			input(1, required),
			input(2, available)
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(1).requiredAmount());
	}

	@Test
	public void preservesZeroCost() {
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForSyntheticRecipeInput(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			CRAFTING,
			MACHINE_RECIPE,
			BookmarkItemType.INGREDIENT,
			typed(new ToolIngredient("programmed_circuit_7", 1, 0, 0, 0)),
			MANAGER,
			0
		);

		Assertions.assertEquals(BookmarkItemType.INGREDIENT, metadata.type());
		Assertions.assertEquals(0, metadata.factor());
		Assertions.assertEquals(0, metadata.amount());
		Assertions.assertEquals(CRAFTING, metadata.recipeTypeUid());
		Assertions.assertEquals(MACHINE_RECIPE, metadata.recipeUid());
	}

	@Test
	public void preservesKeyIdentity() {
		ITypedIngredient<ToolIngredient> original = typed(new ToolIngredient("mold", 1, 0, 0, 0));

		BookmarkIngredientKey key = BookmarkItemMetadataFactory.createPermutationKey(original, MANAGER);
		BookmarkIngredientKey identity = BookmarkIngredientKey.of(key.ingredientTypeUid(), key.ingredientUid());

		Assertions.assertSame(original, key.typedIngredient());
		Assertions.assertEquals(identity, key);
		Assertions.assertEquals(identity.hashCode(), key.hashCode());
	}

	@Test
	public void replacesCandidates() {
		BookmarkItemMetadata catalyst = ingredient(key("mold"), 1).withType(BookmarkItemType.NONCONSUMABLE);

		BookmarkItemMetadata replaced = catalyst.withPermutations(Set.of(key("hydrated_mold")));

		Assertions.assertEquals(BookmarkItemType.NONCONSUMABLE, replaced.type());
		Assertions.assertEquals(Set.of(key("hydrated_mold")), replaced.permutations());
	}

	@Test
	public void ignoresMultiplier() {
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.NONCONSUMABLE,
			64,
			100,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			MACHINE_RECIPE,
			Set.of(key("ferric_chloride"))
		);

		Assertions.assertEquals(100, metadata.amount());
		Assertions.assertEquals(100, metadata.amount(9));
	}

	@Test
	public void exposesTypeRoles() {
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

		Assertions.assertTrue(BookmarkItemType.NONCONSUMABLE.isRecipeAssociated());
		Assertions.assertEquals(RecipeIngredientRole.INPUT, BookmarkItemType.NONCONSUMABLE.recipeRole());
		Assertions.assertTrue(BookmarkItemType.NONCONSUMABLE.isNonConsumable());
		Assertions.assertFalse(BookmarkItemType.NONCONSUMABLE.isGraphInput());
		Assertions.assertFalse(BookmarkItemType.NONCONSUMABLE.scalesWithMultiplier());
	}

	@Test
	public void createsNonconsumable() {
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForSyntheticRecipeInput(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			CRAFTING,
			MACHINE_RECIPE,
			BookmarkItemType.NONCONSUMABLE,
			typed(new ToolIngredient("mold", 3, 0, 0, 0)),
			MANAGER,
			3
		);

		Assertions.assertEquals(BookmarkItemType.NONCONSUMABLE, metadata.type());
		Assertions.assertEquals(3, metadata.amount(9));
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata result(BookmarkIngredientKey key, long multiplier) {
		return new BookmarkItemMetadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.RESULT, multiplier, 1, BookmarkItemMetadata.CHANCE_FULL, CRAFTING, MACHINE_RECIPE, Set.of(key));
	}

	private static BookmarkItemMetadata ingredient(BookmarkIngredientKey key, long factor) {
		return new BookmarkItemMetadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.INGREDIENT, 1, factor, BookmarkItemMetadata.CHANCE_FULL, CRAFTING, MACHINE_RECIPE, Set.of(key));
	}

	private static BookmarkIngredientKey key(String name) {
		return new BookmarkIngredientKey(TOOL_TYPE.getUid(), "test:" + name);
	}

	private static ITypedIngredient<ToolIngredient> typed(ToolIngredient ingredient) {
		return TypedIngredient.createUnvalidated(TOOL_TYPE, ingredient);
	}

	private static IIngredientManager createManager() {
		IIngredientHelper<ToolIngredient> helper = new IIngredientHelper<>() {
			@Override
			public IIngredientType<ToolIngredient> getIngredientType() {
				return TOOL_TYPE;
			}

			@Override
			public String getDisplayName(ToolIngredient ingredient) {
				return ingredient.name();
			}

			@Override
			public String getUniqueId(ToolIngredient ingredient, UidContext context) {
				return "test:" + ingredient.name();
			}

			@Override
			public ResourceLocation getResourceLocation(ToolIngredient ingredient) {
				return ResourceLocation.fromNamespaceAndPath("test", ingredient.name());
			}

			@Override
			public ToolIngredient copyIngredient(ToolIngredient ingredient) {
				return ingredient;
			}

			@Override
			public String getErrorInfo(ToolIngredient ingredient) {
				return String.valueOf(ingredient);
			}
		};

		return ItemStackIngredientTestFixtures.ingredientManager(TOOL_TYPE, helper);
	}

	private record ToolIngredient(String name, long amount, int damage, int maxDamage, int damagePerCraft) {
		public boolean hasCraftingRemainingItem() {
			return true;
		}

		public ToolIngredient getCraftingRemainingItem() {
			return new ToolIngredient(name, 1, damage + damagePerCraft, maxDamage, damagePerCraft);
		}

		public ToolItem getItem() {
			return new ToolItem(damagePerCraft);
		}

		public int getMaxDamage() {
			return maxDamage;
		}

		public int getDamageValue() {
			return damage;
		}
	}

	private record ToolItem(int damagePerCraft) {
		public ToolStats getToolStats() {
			return new ToolStats(damagePerCraft);
		}

		public boolean isElectric() {
			return false;
		}
	}

	private record ToolStats(int damagePerCraft) {
		public int getDamagePerCraftingAction(ToolIngredient stack) {
			return damagePerCraft;
		}
	}
}
