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
	private static final ResourceLocation CRAFTING = new ResourceLocation("minecraft", "crafting");
	private static final ResourceLocation PLATE_RECIPE = new ResourceLocation("test", "plate");
	private static final ResourceLocation MACHINE_RECIPE = new ResourceLocation("test", "machine");
	private static final ResourceLocation WET_PROCESSOR = new ResourceLocation("test", "wetware_processor");
	private static final ResourceLocation WET_ASSEMBLY = new ResourceLocation("test", "wetware_processor_assembly");
	private static final ResourceLocation WET_COMPUTER = new ResourceLocation("test", "wetware_processor_computer");
	private static final ResourceLocation WET_MAINFRAME = new ResourceLocation("test", "wetware_processor_mainframe");

	@Test
	public void wetwareStyleChainCollapsesMiddleRecipesWithLiveItemStackKeys() {
		// mclo.gs/3jufJAL 复现: KJS GTCEu 湿件处理器链。1.20.1 平台类型 uid 是
		// "item_stack"(1.21.1 为 "minecraft:item_stack"), 输出 Count:8b / 输入 Count:1b
		// 快照不同。迁移 1.21.1 计算逻辑并适配后, 只有顶层配方是输出, 中间配方全部折叠。
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(WET_PROCESSOR, itemKey8("gtceu:wetware_processor"), 8, 1)),
			input(1, ingredient(WET_PROCESSOR, itemKey1("gtceu:crystal_processor_mainframe"), 8)),
			input(2, result(WET_ASSEMBLY, itemKey8("gtceu:wetware_processor_assembly"), 8, 1)),
			input(3, ingredient(WET_ASSEMBLY, itemKey1("gtceu:wetware_processor"), 8)),
			input(4, result(WET_COMPUTER, itemKey8("gtceu:wetware_processor_computer"), 8, 1)),
			input(5, ingredient(WET_COMPUTER, itemKey1("gtceu:wetware_processor_assembly"), 8)),
			input(6, result(WET_MAINFRAME, itemKey8("gtceu:wetware_processor_mainframe"), 8, 1)),
			input(7, ingredient(WET_MAINFRAME, itemKey1("gtceu:wetware_processor_computer"), 8))
		), Set.of());

		Assertions.assertEquals(Set.of(WET_MAINFRAME), details.outputRecipes());
		Assertions.assertEquals(Set.of(WET_PROCESSOR, WET_ASSEMBLY, WET_COMPUTER), details.middleRecipes());
		Assertions.assertEquals(8, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(3).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(5).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(7).requiredAmount());
		Assertions.assertTrue(details.missedItems().containsKey(itemKey1("gtceu:crystal_processor_mainframe")));
		Assertions.assertFalse(details.missedItems().containsKey(itemKey1("gtceu:wetware_processor")));
	}

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
		ResourceLocation filledBucketRecipe = new ResourceLocation("test", "filled_bucket");

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
		ResourceLocation firstRecipe = new ResourceLocation("test", "first");
		ResourceLocation secondRecipe = new ResourceLocation("test", "second");

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
		ResourceLocation crushingRecipe = new ResourceLocation("test", "crushing");

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
		ResourceLocation crushingRecipe = new ResourceLocation("test", "crushing");
		ResourceLocation alloyRecipe = new ResourceLocation("test", "alloy");

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
			ResourceLocation recipeUid = new ResourceLocation("test", "independent_" + index);
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
		ResourceLocation soupRecipe = new ResourceLocation("test", "soup");

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
		ResourceLocation machineRecipe = new ResourceLocation("test", "wrench_machine");

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
		ResourceLocation machineRecipe = new ResourceLocation("test", "empty_wrench_machine");

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
		ResourceLocation machineRecipe = new ResourceLocation("test", "two_wrench_machine");

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
		ResourceLocation machineRecipe = new ResourceLocation("test", "many_wrench_machine");

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
		ResourceLocation machineRecipe = new ResourceLocation("test", "broken_wrench_machine");

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

	@Test
	public void collapsedRecipeBlockFlattensClosureWithRemainder() {
		ResourceLocation recipeA = new ResourceLocation("test", "a");
		ResourceLocation recipeB = new ResourceLocation("test", "b");
		ResourceLocation recipeC = new ResourceLocation("test", "c");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 1)),
			input(1, ingredient(recipeA, key("in_b"), 1)),
			input(2, ingredient(recipeA, key("in_c"), 1)),
			input(3, result(recipeB, key("in_b"), 2, 1)),
			input(4, ingredient(recipeB, key("in_a"), 1)),
			input(5, ingredient(recipeB, key("in_b2"), 1)),
			input(6, result(recipeC, key("in_c"), 1, 1)),
			input(7, ingredient(recipeC, key("in_d"), 1)),
			input(8, ingredient(recipeC, key("in_e"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(List.of("out_a", "in_b", "in_a", "in_b2", "in_d", "in_e"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertFalse(block.items().get(1).anchor());
		Assertions.assertEquals(RecipeChainItemType.REMAINDER, block.items().get(1).chainItem().type());
		Assertions.assertEquals(1, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(1, block.items().get(2).chainItem().shiftAmount());
	}

	@Test
	public void collapsedRecipeBlockAggregatesSharedIngredients() {
		ResourceLocation recipeA = new ResourceLocation("test", "a");
		ResourceLocation recipeB = new ResourceLocation("test", "b");
		ResourceLocation recipeC = new ResourceLocation("test", "c");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 1)),
			input(1, ingredient(recipeA, key("in_b"), 1)),
			input(2, ingredient(recipeA, key("in_c"), 1)),
			input(3, result(recipeB, key("in_b"), 1, 1)),
			input(4, ingredient(recipeB, key("shared"), 1)),
			input(5, ingredient(recipeB, key("b2"), 1)),
			input(6, result(recipeC, key("in_c"), 1, 1)),
			input(7, ingredient(recipeC, key("shared"), 1)),
			input(8, ingredient(recipeC, key("c2"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(List.of("out_a", "shared", "b2", "c2"), itemKeys);
		Assertions.assertEquals(2, block.items().get(1).chainItem().shiftAmount());
	}

	@Test
	public void collapsedRecipeBlockKeepsAnchorWhenFullyConsumed() {
		ResourceLocation recipeA = new ResourceLocation("test", "a");
		ResourceLocation recipeB = new ResourceLocation("test", "b");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 1)),
			input(1, ingredient(recipeA, key("in_b"), 1)),
			input(2, result(recipeB, key("in_b"), 1, 1)),
			input(3, ingredient(recipeB, key("in_a"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(List.of("out_a", "in_a"), itemKeys);
	}

	@Test
	public void collapsedRecipeBlockShowsTopLevelSupplyAsZeroShiftShadow() {
		ResourceLocation recipeA = new ResourceLocation("test", "a");
		ResourceLocation recipeB = new ResourceLocation("test", "b");
		ResourceLocation recipeC = new ResourceLocation("test", "c");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 1)),
			input(1, ingredient(recipeA, key("in_b"), 1)),
			input(2, result(recipeB, key("in_b"), 1, 1)),
			input(3, ingredient(recipeB, key("in_a"), 1)),
			input(4, result(recipeC, key("in_a"), 2, 2)),
			input(5, ingredient(recipeC, key("in_x"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(List.of("out_a", "in_a"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, block.items().get(1).chainItem().type());
		Assertions.assertEquals(0, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(1, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertTrue(details.collapsedBlocks().get(recipeA).items().stream()
			.noneMatch(item -> item.metadata().permutations().iterator().next().ingredientUid().equals("in_x")));
	}

	@Test
	public void collapsedRecipeBlockOmitsRootInputFullySuppliedByClosure() {
		ResourceLocation recipeA = new ResourceLocation("test", "a");
		ResourceLocation recipeB = new ResourceLocation("test", "b");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 1)),
			input(1, ingredient(recipeA, key("in_b"), 3)),
			input(2, result(recipeB, key("in_b"), 1, 1)),
			input(3, ingredient(recipeB, key("in_a"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(List.of("out_a", "in_a"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(3, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertTrue(block.items().stream()
			.noneMatch(item -> item.metadata().permutations().iterator().next().ingredientUid().equals("in_b")));
	}

	@Test
	public void collapsedRecipeBlockZeroesRootSupplyWhenRootMultiplierIsZero() {
		ResourceLocation recipeA = new ResourceLocation("test", "a");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 0)),
			input(1, ingredient(recipeA, key("in_b"), 3))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(List.of("out_a", "in_b"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(0, block.items().get(0).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().calculatedMultiplier());
	}

	@Test
	public void collapsedRecipeBlockUsesWorkingMultiplierForMiddleRoot() {
		ResourceLocation recipeA = new ResourceLocation("test", "a");
		ResourceLocation recipeB = new ResourceLocation("test", "b");
		ResourceLocation recipeC = new ResourceLocation("test", "c");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeC, key("out_c"), 1, 1)),
			input(1, ingredient(recipeC, key("in_a"), 1)),
			input(2, result(recipeA, key("in_a"), 1, 0)),
			input(3, ingredient(recipeA, key("in_b"), 3)),
			input(4, result(recipeB, key("in_b"), 1, 1)),
			input(5, ingredient(recipeB, key("in_x"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(List.of("in_a", "in_x"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(1, block.items().get(0).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realAmount());
		Assertions.assertEquals(3, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(3, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(1).chainItem().realAmount());
		Assertions.assertTrue(block.items().stream()
			.noneMatch(item -> item.metadata().permutations().iterator().next().ingredientUid().equals("in_b")));
	}

	@Test
	public void collapsedRecipeBlockFlattensInscriberChainLikeGtnh() {
		ResourceLocation r64 = new ResourceLocation("minecraft", "crafting");
		ResourceLocation rCp = new ResourceLocation("ae2", "inscriber/calculation_processor");
		ResourceLocation rCpp = new ResourceLocation("ae2", "inscriber/calculation_processor_print");
		ResourceLocation rSp = new ResourceLocation("ae2", "inscriber/silicon_print");
		ResourceLocation rCpress = new ResourceLocation("ae2", "inscriber/calculation_processor_press");
		ResourceLocation rSpress = new ResourceLocation("ae2", "inscriber/silicon_press");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(r64, key("cell_component_64k"), 1, 1)),
			input(1, ingredient(r64, key("glowstone_dust"), 4)),
			input(2, ingredient(r64, key("calculation_processor"), 1)),
			input(3, ingredient(r64, key("cell_component_16k"), 3)),
			input(4, ingredient(r64, key("quartz_glass"), 1)),
			input(5, result(rCp, key("calculation_processor"), 1, 0)),
			input(6, ingredient(rCp, key("printed_calculation_processor"), 1).withMultiplier(0)),
			input(7, ingredient(rCp, key("redstone"), 1).withMultiplier(0)),
			input(8, ingredient(rCp, key("printed_silicon"), 1).withMultiplier(0)),
			input(9, result(rCpp, key("printed_calculation_processor"), 1, 1)),
			input(10, ingredient(rCpp, key("calculation_processor_press"), 1)),
			input(11, ingredient(rCpp, key("certus_quartz_crystal"), 1)),
			input(12, result(rSp, key("printed_silicon"), 1, 1)),
			input(13, ingredient(rSp, key("silicon_press"), 1)),
			input(14, ingredient(rSp, key("silicon"), 1)),
			input(15, result(rCpress, key("calculation_processor_press"), 1, 1)),
			input(16, ingredient(rCpress, key("calculation_processor_press"), 1)),
			input(17, ingredient(rCpress, key("iron_block"), 1)),
			input(18, result(rSpress, key("silicon_press"), 1, 1)),
			input(19, ingredient(rSpress, key("silicon_press"), 1)),
			input(20, ingredient(rSpress, key("iron_block"), 1))
		), Set.of(rCp));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(rCp);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(
			List.of(
				"calculation_processor",
				"redstone",
				"certus_quartz_crystal",
				"silicon",
				"calculation_processor_press",
				"iron_block",
				"silicon_press"
			),
			itemKeys
		);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(0, block.items().get(0).chainItem().shiftAmount());
		Assertions.assertEquals(1, block.items().get(0).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realAmount());
		Assertions.assertEquals(1, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertEquals(2, block.items().get(5).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(5).chainItem().realMultiplier());
		Assertions.assertTrue(block.items().stream()
			.noneMatch(item -> {
				String uid = item.metadata().permutations().iterator().next().ingredientUid();
				return uid.equals("printed_calculation_processor") || uid.equals("printed_silicon");
			}));
	}

	@Test
	public void collapsedRecipeBlockUsesBookmarkMultiplierForRealProjection() {
		ResourceLocation r64 = new ResourceLocation("minecraft", "crafting");
		ResourceLocation rCp = new ResourceLocation("ae2", "inscriber/calculation_processor");
		ResourceLocation rCpp = new ResourceLocation("ae2", "inscriber/calculation_processor_print");
		ResourceLocation rSp = new ResourceLocation("ae2", "inscriber/silicon_print");
		ResourceLocation rCpress = new ResourceLocation("ae2", "inscriber/calculation_processor_press");
		ResourceLocation rSpress = new ResourceLocation("ae2", "inscriber/silicon_press");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(r64, key("cell_component_64k"), 1, 1)),
			input(1, ingredient(r64, key("glowstone_dust"), 4)),
			input(2, ingredient(r64, key("calculation_processor"), 1)),
			input(3, ingredient(r64, key("cell_component_16k"), 3)),
			input(4, ingredient(r64, key("quartz_glass"), 1)),
			input(5, result(rCp, key("calculation_processor"), 1, 1)),
			input(6, ingredient(rCp, key("printed_calculation_processor"), 1).withMultiplier(1)),
			input(7, ingredient(rCp, key("redstone"), 1).withMultiplier(1)),
			input(8, ingredient(rCp, key("printed_silicon"), 1).withMultiplier(1)),
			input(9, result(rCpp, key("printed_calculation_processor"), 1, 1)),
			input(10, ingredient(rCpp, key("calculation_processor_press"), 1)),
			input(11, ingredient(rCpp, key("certus_quartz_crystal"), 1)),
			input(12, result(rSp, key("printed_silicon"), 1, 1)),
			input(13, ingredient(rSp, key("silicon_press"), 1)),
			input(14, ingredient(rSp, key("silicon"), 1)),
			input(15, result(rCpress, key("calculation_processor_press"), 1, 1)),
			input(16, ingredient(rCpress, key("calculation_processor_press"), 1)),
			input(17, ingredient(rCpress, key("iron_block"), 1)),
			input(18, result(rSpress, key("silicon_press"), 1, 1)),
			input(19, ingredient(rSpress, key("silicon_press"), 1)),
			input(20, ingredient(rSpress, key("iron_block"), 1))
		), Set.of(rCp));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(rCp);
		Assertions.assertNotNull(block);
		List<String> itemKeys = block.items().stream()
			.map(item -> item.metadata().permutations().iterator().next().ingredientUid())
			.toList();
		Assertions.assertEquals(
			List.of(
				"calculation_processor",
				"redstone",
				"certus_quartz_crystal",
				"silicon",
				"calculation_processor_press",
				"iron_block",
				"silicon_press"
			),
			itemKeys
		);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(1, block.items().get(0).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realAmount());
		Assertions.assertEquals(1, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertEquals(1, block.items().get(1).chainItem().realMultiplier());
		Assertions.assertEquals(1, block.items().get(1).chainItem().realAmount());
		Assertions.assertEquals(2, block.items().get(5).chainItem().calculatedAmount());
		Assertions.assertEquals(2, block.items().get(5).chainItem().realMultiplier());
		Assertions.assertEquals(2, block.items().get(5).chainItem().realAmount());
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

	private static BookmarkIngredientKey itemKey1(String uid) {
		return new BookmarkIngredientKey("item_stack", uid, "{Count:1b,id:\"" + uid + "\"}");
	}

	private static BookmarkIngredientKey itemKey8(String uid) {
		return new BookmarkIngredientKey("item_stack", uid, "{Count:8b,id:\"" + uid + "\"}");
	}
}
