package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RecipeChainMathTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final ResourceLocation PLATE_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "plate");
	private static final ResourceLocation MACHINE_RECIPE = ResourceLocation.fromNamespaceAndPath("test", "machine");

	@Test
	public void masterRootUnifiesOutputRecipesForAutocrafting() {
		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(PLATE_RECIPE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE_RECIPE, key("ingot"), 2)),
			input(2, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE_RECIPE, key("gear"), 3))
		), Set.of());

		ResourceLocation rootRecipe = math.createMasterRoot();
		RecipeChainDetails details = math.refreshDetails();

		Assertions.assertTrue(math.hasMasterRoot());
		Assertions.assertEquals(RecipeChainMath.ROOT_RECIPE_UID, rootRecipe);
		Assertions.assertEquals(Set.of(RecipeChainMath.ROOT_RECIPE_UID), details.outputRecipes());
		Assertions.assertTrue(details.middleRecipes().contains(PLATE_RECIPE));
		Assertions.assertTrue(details.middleRecipes().contains(MACHINE_RECIPE));
		Assertions.assertEquals(2, details.missedItems().get(key("ingot")));
		Assertions.assertEquals(3, details.missedItems().get(key("gear")));
	}

	@Test
	public void masterRootDoesNotRecycleTargetResultContainerItem() {
		BookmarkIngredientKey emptyBucket = key("bucket");
		ResourceLocation filledBucketRecipe = ResourceLocation.fromNamespaceAndPath("test", "filled_bucket");

		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(filledBucketRecipe, key("filled_bucket"), emptyBucket, 1, 1)),
			input(1, ingredient(filledBucketRecipe, key("water"), 1))
		), Set.of());

		math.createMasterRoot();
		RecipeChainDetails details = math.refreshDetails();

		Assertions.assertFalse(details.containerItems().containsKey(emptyBucket));
		Assertions.assertEquals(1, details.missedItems().get(key("water")));
	}

	@Test
	public void loopingRecipesStopAtTheBackEdgeAndRecordMissingIngredient() {
		ResourceLocation firstRecipe = ResourceLocation.fromNamespaceAndPath("test", "first");
		ResourceLocation secondRecipe = ResourceLocation.fromNamespaceAndPath("test", "second");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(firstRecipe, key("first_item"), 1, 1)),
			input(1, ingredient(firstRecipe, key("second_item"), 1)),
			input(2, result(secondRecipe, key("second_item"), 1, 1)),
			input(3, ingredient(secondRecipe, key("first_item"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(firstRecipe), details.outputRecipes());
		Assertions.assertEquals(Set.of(secondRecipe), details.middleRecipes());
		Assertions.assertEquals(1, details.missedItems().get(key("first_item")));
	}

	@Test
	public void groupRecipeResultCanSatisfyAnotherRecipeIngredient() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE_RECIPE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE_RECIPE, key("ingot"), 2)),
			input(2, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE_RECIPE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE_RECIPE), details.outputRecipes());
		Assertions.assertEquals(Set.of(PLATE_RECIPE), details.middleRecipes());
		Assertions.assertTrue(details.recipeRelations().isEmpty());
		Assertions.assertEquals(RecipeChainItemType.RESULT, details.calculatedItems().get(2).type());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
		Assertions.assertEquals(2, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(3).type());
		Assertions.assertEquals(0, details.calculatedItems().get(3).requiredAmount());
	}

	@Test
	public void extraIntermediateOutputBecomesRemainder() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE_RECIPE, key("plate"), 2, 1)),
			input(1, ingredient(PLATE_RECIPE, key("ingot"), 1)),
			input(2, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE_RECIPE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(RecipeChainItemType.REMAINDER, details.calculatedItems().get(0).type());
		Assertions.assertEquals(1, details.calculatedItems().get(0).providedAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
	}

	@Test
	public void catalystInputDoesNotBecomeAConsumedMaterialRequirement() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE_RECIPE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE_RECIPE, key("mold"), 1).withType(BookmarkItemType.CATALYST))
		), Set.of());

		Assertions.assertFalse(details.missedItems().containsKey(key("mold")));
		Assertions.assertFalse(details.calculatedItems().containsKey(1));
	}

	@Test
	public void extraOutputFromTargetRecipeIsRemainderNotAnotherTarget() {
		ResourceLocation crushingRecipe = ResourceLocation.fromNamespaceAndPath("test", "crushing");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(crushingRecipe, key("dust"), 1, 1)),
			input(1, result(crushingRecipe, key("tiny_dust"), 1, 1)),
			input(2, ingredient(crushingRecipe, key("ore"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(crushingRecipe), details.outputRecipes());
		Assertions.assertEquals(RecipeChainItemType.RESULT, details.calculatedItems().get(0).type());
		Assertions.assertEquals(RecipeChainItemType.REMAINDER, details.calculatedItems().get(1).type());
		Assertions.assertEquals(1, details.calculatedItems().get(1).providedAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(2).requiredAmount());
	}

	@Test
	public void extraOutputCanSatisfyLaterRecipeIngredientWithoutBecomingTarget() {
		ResourceLocation crushingRecipe = ResourceLocation.fromNamespaceAndPath("test", "crushing");
		ResourceLocation alloyRecipe = ResourceLocation.fromNamespaceAndPath("test", "alloy");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(crushingRecipe, key("dust"), 1, 1)),
			input(1, result(crushingRecipe, key("tiny_dust"), 1, 1)),
			input(2, ingredient(crushingRecipe, key("ore"), 1)),
			input(3, result(alloyRecipe, key("alloy"), 1, 1)),
			input(4, ingredient(alloyRecipe, key("tiny_dust"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(alloyRecipe), details.outputRecipes());
		Assertions.assertEquals(Set.of(crushingRecipe), details.middleRecipes());
		Assertions.assertEquals(RecipeChainItemType.REMAINDER, details.calculatedItems().get(0).type());
		Assertions.assertEquals(RecipeChainItemType.REMAINDER, details.calculatedItems().get(1).type());
		Assertions.assertEquals(0, details.calculatedItems().get(1).providedAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(4).requiredAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(2).requiredAmount());
	}

	@Test
	public void intermediateRecipeCanKeepIndependentRequestedOutput() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE_RECIPE, key("plate"), 1, 2)),
			input(1, ingredient(PLATE_RECIPE, key("ingot"), 1)),
			input(2, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE_RECIPE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE_RECIPE, PLATE_RECIPE), details.outputRecipes());
		Assertions.assertEquals(RecipeChainItemType.RESULT, details.calculatedItems().get(0).type());
		Assertions.assertEquals(1, details.calculatedItems().get(0).providedAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(0).realMultiplier());
		Assertions.assertEquals(2, details.calculatedItems().get(0).calculatedMultiplier());
		Assertions.assertEquals(2, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(RecipeChainItemType.RESULT, details.calculatedItems().get(2).type());
		Assertions.assertEquals(1, details.calculatedItems().get(2).realMultiplier());
	}

	@Test
	public void itemWithoutProviderStaysMissingIngredient() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(MACHINE_RECIPE, key("machine"), 1, 2)),
			input(1, ingredient(MACHINE_RECIPE, key("gear"), 3))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE_RECIPE), details.outputRecipes());
		Assertions.assertEquals(6, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
		Assertions.assertEquals(2, details.calculatedItems().get(0).calculatedMultiplier());
	}

	@Test
	public void independentRecipesRemainIndependentOutputs() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		Set<ResourceLocation> recipeUids = new LinkedHashSet<>();
		for (int index = 0; index < 96; index++) {
			ResourceLocation recipeUid = ResourceLocation.fromNamespaceAndPath("test", "independent_" + index);
			recipeUids.add(recipeUid);
			inputs.add(input(index * 2, result(recipeUid, key("result_" + index), 1, 1)));
			inputs.add(input(index * 2 + 1, ingredient(recipeUid, key("input_" + index), 1)));
		}

		RecipeChainDetails details = RecipeChainMath.refresh(inputs, Set.of());

		Assertions.assertEquals(recipeUids, details.outputRecipes());
		Assertions.assertEquals(Set.of(), details.middleRecipes());
	}

	@Test
	public void missedItemsAggregateMissingIngredientAmountsByIngredientKey() {
		BookmarkIngredientKey gear = key("gear");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(MACHINE_RECIPE, key("machine"), 1, 2)),
			input(1, ingredient(MACHINE_RECIPE, gear, 3))
		), Set.of());

		Assertions.assertEquals(6, details.missedItems().get(gear));
	}

	@Test
	public void containerItemReturnedByIngredientIsTrackedAsRemainder() {
		BookmarkIngredientKey bucket = key("bucket");
		ResourceLocation soupRecipe = ResourceLocation.fromNamespaceAndPath("test", "soup");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(soupRecipe, key("soup"), 1, 1)),
			input(1, ingredient(soupRecipe, key("water_bucket"), bucket, 1))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(1, details.containerItems().get(bucket));
	}

	@Test
	public void reusableContainerItemCanSatisfyMultipleCraftingSteps() {
		BookmarkIngredientKey wrench = key("wrench");
		ResourceLocation machineRecipe = ResourceLocation.fromNamespaceAndPath("test", "wrench_machine");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(machineRecipe, key("machine"), 1, 3)),
			input(1, durableIngredient(machineRecipe, wrench, wrench, 1, 5))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(1, details.containerItems().get(wrench));
	}

	@Test
	public void zeroUseContainerItemDoesNotCreateReturnedTool() {
		BookmarkIngredientKey wrench = key("wrench");
		ResourceLocation machineRecipe = ResourceLocation.fromNamespaceAndPath("test", "empty_wrench_machine");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(machineRecipe, key("machine"), 1, 1)),
			input(1, durableIngredient(machineRecipe, wrench, wrench, 1, 0))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertFalse(details.containerItems().containsKey(wrench));
	}

	@Test
	public void reusableContainerItemRemainderSatisfiesLaterIngredientInSameRecipe() {
		BookmarkIngredientKey wrench = key("wrench");
		ResourceLocation machineRecipe = ResourceLocation.fromNamespaceAndPath("test", "two_wrench_machine");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(machineRecipe, key("machine"), 1, 1)),
			input(1, durableIngredient(machineRecipe, wrench, wrench, 3, 5)),
			input(2, durableIngredient(machineRecipe, wrench, wrench, 2, 5))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertFalse(details.containerItems().containsKey(wrench));
	}

	@Test
	public void reusableContainerItemRequiresAnotherFreshItemAfterUsesRunOut() {
		BookmarkIngredientKey wrench = key("wrench");
		ResourceLocation machineRecipe = ResourceLocation.fromNamespaceAndPath("test", "many_wrench_machine");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(machineRecipe, key("machine"), 1, 6)),
			input(1, durableIngredient(machineRecipe, wrench, wrench, 1, 5))
		), Set.of());

		Assertions.assertEquals(2, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(1, details.containerItems().get(wrench));
	}

	@Test
	public void reusableContainerItemAddsBrokenRemainderWhenUsesRunOut() {
		BookmarkIngredientKey wrench = key("wrench");
		BookmarkIngredientKey brokenWrench = key("broken_wrench");
		ResourceLocation machineRecipe = ResourceLocation.fromNamespaceAndPath("test", "broken_wrench_machine");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(machineRecipe, key("machine"), 1, 2)),
			input(1, durableIngredient(machineRecipe, wrench, wrench, brokenWrench, 1, 2))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertFalse(details.containerItems().containsKey(wrench));
		Assertions.assertEquals(1, details.containerItems().get(brokenWrench));
	}

	@Test
	public void initialItemsCanSatisfyRecipeIngredients() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, item(key("plate"), 1, 1)),
			input(1, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(2, ingredient(MACHINE_RECIPE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE_RECIPE), details.outputRecipes());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(0).type());
		Assertions.assertEquals(1, details.calculatedItems().get(0).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertEquals(Set.of(0), details.initialItems());
	}

	@Test
	public void initialItemWithSubtypeCanSatisfyBaseItemIngredientForCrafting() {
		BookmarkIngredientKey baseMachine = new BookmarkIngredientKey("minecraft:item_stack", "mekanism:ultimate_injecting_factory", null);
		BookmarkIngredientKey chargedMachine = new BookmarkIngredientKey(
			"minecraft:item_stack",
			"mekanism:ultimate_injecting_factory:{mekData:{EnergyContainers:[{Container:0,stored:\"1000\"}]}}",
			null
		);

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, item(chargedMachine, 1, 1)),
			input(1, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(2, ingredient(MACHINE_RECIPE, baseMachine, 1))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(0).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertTrue(details.missedItems().isEmpty());
	}

	@Test
	public void initialItemWithDifferentSubtypeCanSatisfySameItemIngredientForCrafting() {
		BookmarkIngredientKey emptyMachine = new BookmarkIngredientKey(
			"minecraft:item_stack",
			"mekanism:elite_injecting_factory:empty:empty:empty",
			null
		);
		BookmarkIngredientKey chargedMachine = new BookmarkIngredientKey(
			"minecraft:item_stack",
			"mekanism:elite_injecting_factory:mekanism:oxygen:stored_energy",
			null
		);

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, item(chargedMachine, 1, 1)),
			input(1, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(2, ingredient(MACHINE_RECIPE, emptyMachine, 1))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(0).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertTrue(details.missedItems().isEmpty());
	}

	@Test
	public void initialItemWithDifferentSubtypeDoesNotSatisfySameItemIngredientOutsideRelaxedNamespaces() {
		BookmarkIngredientKey emptyMachine = new BookmarkIngredientKey(
			"minecraft:item_stack",
			"minecraft:furnace:empty",
			null
		);
		BookmarkIngredientKey chargedMachine = new BookmarkIngredientKey(
			"minecraft:item_stack",
			"minecraft:furnace:charged",
			null
		);

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, item(chargedMachine, 1, 1)),
			input(1, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(2, ingredient(MACHINE_RECIPE, emptyMachine, 1))
		), Set.of());

		Assertions.assertEquals(0, details.calculatedItems().get(0).requiredAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertEquals(1, details.missedItems().get(emptyMachine));
	}

	@Test
	public void collapsedRecipeKeepsRelatedRecipeIds() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE_RECIPE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE_RECIPE, key("ingot"), 2)),
			input(2, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE_RECIPE, key("plate"), 1))
		), Set.of(MACHINE_RECIPE));

		Assertions.assertEquals(Set.of(MACHINE_RECIPE, PLATE_RECIPE), details.recipeRelations().get(MACHINE_RECIPE));
	}

	@Test
	public void collapsedRecipeMapsProjectedItemsToTheDisplayedRecipe() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE_RECIPE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE_RECIPE, key("ingot"), 2)),
			input(2, result(MACHINE_RECIPE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE_RECIPE, key("plate"), 1))
		), Set.of(MACHINE_RECIPE));

		Assertions.assertEquals(MACHINE_RECIPE, details.itemToRecipe().get(1));
		Assertions.assertEquals(MACHINE_RECIPE, details.itemToRecipe().get(2));
	}

	@Test
	public void metadataOwnsAmountAndMultiplierMath() {
		BookmarkItemMetadata chanceIngredient = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT,
			2,
			3,
			5_000,
			CRAFTING,
			MACHINE_RECIPE,
			Set.of(key("gear"))
		);
		BookmarkItemMetadata chanceResult = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.RESULT,
			2,
			3,
			5_000,
			CRAFTING,
			MACHINE_RECIPE,
			Set.of(key("machine"))
		);

		Assertions.assertEquals(3, chanceIngredient.amount());
		Assertions.assertEquals(3, chanceResult.amount());
		Assertions.assertEquals(2, chanceIngredient.multiplierFromAmount(3));
		Assertions.assertTrue(chanceIngredient.containsItems(chanceIngredient));
		Assertions.assertTrue(chanceIngredient.equalsRecipe(MACHINE_RECIPE, BookmarkGroupManager.DEFAULT_GROUP_ID));
	}

	private static RecipeChainInput input(int index, BookmarkItemMetadata metadata) {
		return new RecipeChainInput(index, metadata);
	}

	private static BookmarkItemMetadata item(BookmarkIngredientKey key, long factor, long multiplier) {
		return metadata(null, BookmarkItemType.ITEM, key, factor, multiplier);
	}

	private static BookmarkItemMetadata result(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor, long multiplier) {
		return metadata(recipeUid, BookmarkItemType.RESULT, key, factor, multiplier);
	}

	private static BookmarkItemMetadata result(ResourceLocation recipeUid, BookmarkIngredientKey key, BookmarkIngredientKey containerItem, long factor, long multiplier) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.RESULT,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key),
			containerItem
		);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeUid, BookmarkIngredientKey key, long factor) {
		return metadata(recipeUid, BookmarkItemType.INGREDIENT, key, factor, 1);
	}

	private static BookmarkItemMetadata ingredient(ResourceLocation recipeUid, BookmarkIngredientKey key, BookmarkIngredientKey containerItem, long factor) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT,
			1,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key),
			containerItem
		);
	}

	private static BookmarkItemMetadata durableIngredient(
		ResourceLocation recipeUid,
		BookmarkIngredientKey key,
		BookmarkIngredientKey containerItem,
		long factor,
		long containerItemCraftingUses
	) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT,
			1,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key),
			containerItem,
			containerItemCraftingUses
		);
	}

	private static BookmarkItemMetadata durableIngredient(
		ResourceLocation recipeUid,
		BookmarkIngredientKey key,
		BookmarkIngredientKey containerItem,
		BookmarkIngredientKey brokenContainerItem,
		long factor,
		long containerItemCraftingUses
	) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT,
			1,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key),
			containerItem,
			containerItemCraftingUses,
			brokenContainerItem
		);
	}

	private static BookmarkItemMetadata metadata(ResourceLocation recipeUid, BookmarkItemType type, BookmarkIngredientKey key, long factor, long multiplier) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			CRAFTING,
			recipeUid,
			Set.of(key)
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid, null);
	}
}
