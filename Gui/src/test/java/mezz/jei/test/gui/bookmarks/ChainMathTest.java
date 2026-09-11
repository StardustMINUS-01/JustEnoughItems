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
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.input;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.item;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.key;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.result;
import static mezz.jei.test.gui.fixtures.RecipeChainTestFixtures.ingredient;

public class ChainMathTest {
	private static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
	private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath("test", "plate");
	private static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath("test", "machine");

	@Test
	public void unifiesTargets() {
		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, result(PLATE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE, key("ingot"), 2)),
			input(2, result(MACHINE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE, key("gear"), 3))
		), Set.of());

		ResourceLocation rootRecipe = math.createMasterRoot();
		RecipeChainDetails details = math.refreshDetails();

		Assertions.assertTrue(math.hasMasterRoot());
		Assertions.assertEquals(RecipeChainMath.ROOT_RECIPE_UID, rootRecipe);
		Assertions.assertEquals(Set.of(RecipeChainMath.ROOT_RECIPE_UID), details.outputRecipes());
		Assertions.assertTrue(details.middleRecipes().contains(PLATE));
		Assertions.assertTrue(details.middleRecipes().contains(MACHINE));
		Assertions.assertEquals(2, details.missedItems().get(key("ingot")));
		Assertions.assertEquals(3, details.missedItems().get(key("gear")));
	}

	@Test
	public void keepsTargetContainers() {
		BookmarkIngredientKey emptyBucket = key("bucket");
		ResourceLocation filledBucketRecipe = ResourceLocation.fromNamespaceAndPath("test", "filled_bucket");

		RecipeChainMath math = RecipeChainMath.of(List.of(
			input(0, containerResult(filledBucketRecipe, key("filled_bucket"), emptyBucket, 1, 1)),
			input(1, ingredient(filledBucketRecipe, key("water"), 1))
		), Set.of());

		math.createMasterRoot();
		RecipeChainDetails details = math.refreshDetails();

		Assertions.assertFalse(details.containerItems().containsKey(emptyBucket));
		Assertions.assertEquals(1, details.missedItems().get(key("water")));
	}

	@Test
	public void recordsCycleDemand() {
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
	public void linksRecipeDemand() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE, key("ingot"), 2)),
			input(2, result(MACHINE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE), details.outputRecipes());
		Assertions.assertEquals(Set.of(PLATE), details.middleRecipes());
		Assertions.assertTrue(details.recipeRelations().isEmpty());
		Assertions.assertEquals(RecipeChainItemType.RESULT, details.calculatedItems().get(2).type());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
		Assertions.assertEquals(2, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(3).type());
		Assertions.assertEquals(0, details.calculatedItems().get(3).requiredAmount());
	}

	@Test
	public void keepsIntermediateRemainder() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE, key("plate"), 2, 1)),
			input(1, ingredient(PLATE, key("ingot"), 1)),
			input(2, result(MACHINE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(RecipeChainItemType.REMAINDER, details.calculatedItems().get(0).type());
		Assertions.assertEquals(1, details.calculatedItems().get(0).providedAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
	}

	@Test
	public void excludesCatalystDemand() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE, key("mold"), 1).withType(BookmarkItemType.NONCONSUMABLE))
		), Set.of());

		Assertions.assertFalse(details.missedItems().containsKey(key("mold")));
		Assertions.assertFalse(details.calculatedItems().containsKey(1));
	}

	@Test
	public void classifiesByproducts() {
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
	public void suppliesByproducts() {
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
	public void keepsIntermediateTargets() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE, key("plate"), 1, 2)),
			input(1, ingredient(PLATE, key("ingot"), 1)),
			input(2, result(MACHINE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE, PLATE), details.outputRecipes());
		Assertions.assertEquals(RecipeChainItemType.RESULT, details.calculatedItems().get(0).type());
		Assertions.assertEquals(1, details.calculatedItems().get(0).providedAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(0).realMultiplier());
		Assertions.assertEquals(2, details.calculatedItems().get(0).calculatedMultiplier());
		Assertions.assertEquals(2, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(RecipeChainItemType.RESULT, details.calculatedItems().get(2).type());
		Assertions.assertEquals(1, details.calculatedItems().get(2).realMultiplier());
	}

	@Test
	public void countsMissingDemand() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(MACHINE, key("machine"), 1, 2)),
			input(1, ingredient(MACHINE, key("gear"), 3))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE), details.outputRecipes());
		Assertions.assertEquals(6, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(6, details.missedItems().get(key("gear")));
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
		Assertions.assertEquals(2, details.calculatedItems().get(0).calculatedMultiplier());
	}

	@Test
	public void keepsIndependentTargets() {
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
	public void returnsContainers() {
		BookmarkIngredientKey bucket = key("bucket");
		ResourceLocation soupRecipe = ResourceLocation.fromNamespaceAndPath("test", "soup");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(soupRecipe, key("soup"), 1, 1)),
			input(1, containerIngredient(soupRecipe, key("water_bucket"), bucket, 1))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(1, details.containerItems().get(bucket));
	}

	@ParameterizedTest
	@CsvSource({"3, 1", "6, 2"})
	public void reusesTools(long crafts, long required) {
		BookmarkIngredientKey wrench = key("wrench");
		ResourceLocation machineRecipe = ResourceLocation.fromNamespaceAndPath("test", "wrench_machine");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(machineRecipe, key("machine"), 1, crafts)),
			input(1, durableIngredient(machineRecipe, wrench, wrench, 1, 5))
		), Set.of());

		Assertions.assertEquals(required, details.calculatedItems().get(1).requiredAmount());
		Assertions.assertEquals(1, details.containerItems().get(wrench));
	}

	@Test
	public void omitsExhaustedTools() {
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
	public void sharesToolsAcrossSlots() {
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
	public void returnsBrokenTools() {
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
	public void usesInitialStock() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, item(key("plate"), 1)),
			input(1, result(MACHINE, key("machine"), 1, 1)),
			input(2, ingredient(MACHINE, key("plate"), 1))
		), Set.of());

		Assertions.assertEquals(Set.of(MACHINE), details.outputRecipes());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(0).type());
		Assertions.assertEquals(1, details.calculatedItems().get(0).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertEquals(Set.of(0), details.initialItems());
	}

	@ParameterizedTest
	@CsvSource(value = {
		"mekanism:ultimate_injecting_factory|mekanism:ultimate_injecting_factory:{mekData:{EnergyContainers:[{Container:0,stored:\"1000\"}]}}",
		"mekanism:elite_injecting_factory:empty:empty:empty|mekanism:elite_injecting_factory:mekanism:oxygen:stored_energy"
	}, delimiter = '|')
	public void matchesRelaxedSubtypes(String required, String stored) {
		var needed = new BookmarkIngredientKey("minecraft:item_stack", required);
		var available = new BookmarkIngredientKey("minecraft:item_stack", stored);
		var details = RecipeChainMath.refresh(List.of(
			input(0, item(available, 1)),
			input(1, result(MACHINE, key("machine"), 1, 1)),
			input(2, ingredient(MACHINE, needed, 1))
		), Set.of());

		Assertions.assertEquals(1, details.calculatedItems().get(0).requiredAmount());
		Assertions.assertEquals(0, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertTrue(details.missedItems().isEmpty());
	}

	@Test
	public void rejectsDifferentSubtypes() {
		BookmarkIngredientKey emptyMachine = new BookmarkIngredientKey(
			"minecraft:item_stack",
			"minecraft:furnace:empty"
		);
		BookmarkIngredientKey chargedMachine = new BookmarkIngredientKey(
			"minecraft:item_stack",
			"minecraft:furnace:charged"
		);

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, item(chargedMachine, 1)),
			input(1, result(MACHINE, key("machine"), 1, 1)),
			input(2, ingredient(MACHINE, emptyMachine, 1))
		), Set.of());

		Assertions.assertEquals(0, details.calculatedItems().get(0).requiredAmount());
		Assertions.assertEquals(1, details.calculatedItems().get(2).requiredAmount());
		Assertions.assertEquals(1, details.missedItems().get(emptyMachine));
	}

	@Test
	public void mapsCollapsedRecipes() {
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(PLATE, key("plate"), 1, 1)),
			input(1, ingredient(PLATE, key("ingot"), 2)),
			input(2, result(MACHINE, key("machine"), 1, 1)),
			input(3, ingredient(MACHINE, key("plate"), 1))
		), Set.of(MACHINE));

		Assertions.assertEquals(Set.of(MACHINE, PLATE), details.recipeRelations().get(MACHINE));
		Assertions.assertEquals(MACHINE, details.itemToRecipe().get(1));
		Assertions.assertEquals(MACHINE, details.itemToRecipe().get(2));
	}

	@Test
	public void flattensWithRemainder() {
		ResourceLocation recipeA = ResourceLocation.fromNamespaceAndPath("test", "a");
		ResourceLocation recipeB = ResourceLocation.fromNamespaceAndPath("test", "b");
		ResourceLocation recipeC = ResourceLocation.fromNamespaceAndPath("test", "c");
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
		List<String> itemKeys = itemIds(block);
		Assertions.assertEquals(List.of("out_a", "in_b", "in_a", "in_b2", "in_d", "in_e"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertFalse(block.items().get(1).anchor());
		Assertions.assertEquals(RecipeChainItemType.REMAINDER, block.items().get(1).chainItem().type());
		Assertions.assertEquals(1, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(1, block.items().get(2).chainItem().shiftAmount());
	}

	@Test
	public void mergesCollapsedInputs() {
		ResourceLocation recipeA = ResourceLocation.fromNamespaceAndPath("test", "a");
		ResourceLocation recipeB = ResourceLocation.fromNamespaceAndPath("test", "b");
		ResourceLocation recipeC = ResourceLocation.fromNamespaceAndPath("test", "c");
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
		List<String> itemKeys = itemIds(block);
		Assertions.assertEquals(List.of("out_a", "shared", "b2", "c2"), itemKeys);
		Assertions.assertEquals(2, block.items().get(1).chainItem().shiftAmount());
	}

	@Test
	public void keepsCollapsedOutput() {
		ResourceLocation recipeA = ResourceLocation.fromNamespaceAndPath("test", "a");
		ResourceLocation recipeB = ResourceLocation.fromNamespaceAndPath("test", "b");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 1)),
			input(1, ingredient(recipeA, key("in_b"), 1)),
			input(2, result(recipeB, key("in_b"), 1, 1)),
			input(3, ingredient(recipeB, key("in_a"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = itemIds(block);
		Assertions.assertEquals(List.of("out_a", "in_a"), itemKeys);
	}

	@Test
	public void showsSupplyShadows() {
		ResourceLocation recipeA = ResourceLocation.fromNamespaceAndPath("test", "a");
		ResourceLocation recipeB = ResourceLocation.fromNamespaceAndPath("test", "b");
		ResourceLocation recipeC = ResourceLocation.fromNamespaceAndPath("test", "c");
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
		List<String> itemKeys = itemIds(block);
		Assertions.assertEquals(List.of("out_a", "in_a"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, block.items().get(1).chainItem().type());
		Assertions.assertEquals(0, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(1, block.items().get(1).chainItem().calculatedAmount());
	}

	@Test
	public void omitsInternalInputs() {
		ResourceLocation recipeA = ResourceLocation.fromNamespaceAndPath("test", "a");
		ResourceLocation recipeB = ResourceLocation.fromNamespaceAndPath("test", "b");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 1)),
			input(1, ingredient(recipeA, key("in_b"), 3)),
			input(2, result(recipeB, key("in_b"), 1, 1)),
			input(3, ingredient(recipeB, key("in_a"), 1))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = itemIds(block);
		Assertions.assertEquals(List.of("out_a", "in_a"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(3, block.items().get(1).chainItem().shiftAmount());
	}

	@Test
	public void handlesZeroTargets() {
		ResourceLocation recipeA = ResourceLocation.fromNamespaceAndPath("test", "a");
		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(recipeA, key("out_a"), 1, 0)),
			input(1, ingredient(recipeA, key("in_b"), 3))
		), Set.of(recipeA));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(recipeA);
		Assertions.assertNotNull(block);
		List<String> itemKeys = itemIds(block);
		Assertions.assertEquals(List.of("out_a", "in_b"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(0, block.items().get(0).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().calculatedMultiplier());
	}

	@Test
	public void projectsWorkingDemand() {
		ResourceLocation recipeA = ResourceLocation.fromNamespaceAndPath("test", "a");
		ResourceLocation recipeB = ResourceLocation.fromNamespaceAndPath("test", "b");
		ResourceLocation recipeC = ResourceLocation.fromNamespaceAndPath("test", "c");
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
		List<String> itemKeys = itemIds(block);
		Assertions.assertEquals(List.of("in_a", "in_x"), itemKeys);
		Assertions.assertTrue(block.items().get(0).anchor());
		Assertions.assertEquals(1, block.items().get(0).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realAmount());
		Assertions.assertEquals(3, block.items().get(1).chainItem().shiftAmount());
		Assertions.assertEquals(3, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(1).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(1).chainItem().realAmount());
	}

	@ParameterizedTest
	@ValueSource(longs = {0, 1})
	public void projectsInscriberChain(long multiplier) {
		ResourceLocation cell = ResourceLocation.fromNamespaceAndPath("minecraft", "crafting");
		ResourceLocation processor = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber/calculation_processor");
		ResourceLocation printedProcessor = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber/calculation_processor_print");
		ResourceLocation printedSilicon = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber/silicon_print");
		ResourceLocation processorPress = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber/calculation_processor_press");
		ResourceLocation siliconPress = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber/silicon_press");

		RecipeChainDetails details = RecipeChainMath.refresh(List.of(
			input(0, result(cell, key("cell_component_64k"), 1, 1)),
			input(1, ingredient(cell, key("glowstone_dust"), 4)),
			input(2, ingredient(cell, key("calculation_processor"), 1)),
			input(3, ingredient(cell, key("cell_component_16k"), 3)),
			input(4, ingredient(cell, key("quartz_glass"), 1)),
			input(5, result(processor, key("calculation_processor"), 1, multiplier)),
			input(6, ingredient(processor, key("printed_calculation_processor"), 1).withMultiplier(multiplier)),
			input(7, ingredient(processor, key("redstone"), 1).withMultiplier(multiplier)),
			input(8, ingredient(processor, key("printed_silicon"), 1).withMultiplier(multiplier)),
			input(9, result(printedProcessor, key("printed_calculation_processor"), 1, 1)),
			input(10, ingredient(printedProcessor, key("calculation_processor_press"), 1)),
			input(11, ingredient(printedProcessor, key("certus_quartz_crystal"), 1)),
			input(12, result(printedSilicon, key("printed_silicon"), 1, 1)),
			input(13, ingredient(printedSilicon, key("silicon_press"), 1)),
			input(14, ingredient(printedSilicon, key("silicon"), 1)),
			input(15, result(processorPress, key("calculation_processor_press"), 1, 1)),
			input(16, ingredient(processorPress, key("calculation_processor_press"), 1)),
			input(17, ingredient(processorPress, key("iron_block"), 1)),
			input(18, result(siliconPress, key("silicon_press"), 1, 1)),
			input(19, ingredient(siliconPress, key("silicon_press"), 1)),
			input(20, ingredient(siliconPress, key("iron_block"), 1))
		), Set.of(processor));

		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(processor);
		Assertions.assertNotNull(block);
		List<String> itemKeys = itemIds(block);
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
		if (multiplier == 0) {
			Assertions.assertEquals(0, block.items().get(0).chainItem().shiftAmount());
		}
		Assertions.assertEquals(1, block.items().get(0).chainItem().calculatedAmount());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realMultiplier());
		Assertions.assertEquals(0, block.items().get(0).chainItem().realAmount());
		Assertions.assertEquals(1, block.items().get(1).chainItem().calculatedAmount());
		Assertions.assertEquals(multiplier, block.items().get(1).chainItem().realMultiplier());
		Assertions.assertEquals(multiplier, block.items().get(1).chainItem().realAmount());
		Assertions.assertEquals(2, block.items().get(5).chainItem().calculatedAmount());
		Assertions.assertEquals(2 * multiplier, block.items().get(5).chainItem().realMultiplier());
		Assertions.assertEquals(2 * multiplier, block.items().get(5).chainItem().realAmount());
	}

	private static List<String> itemIds(RecipeChainDetails.CollapsedBlock block) {
		return block.items().stream().map(item -> item.metadata().permutations().iterator().next().ingredientUid()).toList();
	}

	private static BookmarkItemMetadata containerResult(ResourceLocation recipe, BookmarkIngredientKey key, BookmarkIngredientKey container, long factor, long multiplier) {
		return new BookmarkItemMetadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.RESULT,
			multiplier, factor, BookmarkItemMetadata.CHANCE_FULL, CRAFTING, recipe, Set.of(key), container);
	}

	private static BookmarkItemMetadata containerIngredient(ResourceLocation recipe, BookmarkIngredientKey key, BookmarkIngredientKey container, long factor) {
		return new BookmarkItemMetadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.INGREDIENT,
			1, factor, BookmarkItemMetadata.CHANCE_FULL, CRAFTING, recipe, Set.of(key), container);
	}

	private static BookmarkItemMetadata durableIngredient(ResourceLocation recipe, BookmarkIngredientKey key,
		BookmarkIngredientKey container, long factor, long uses) {
		return durableIngredient(recipe, key, container, null, factor, uses);
	}

	private static BookmarkItemMetadata durableIngredient(ResourceLocation recipe, BookmarkIngredientKey key,
		BookmarkIngredientKey container, @Nullable BookmarkIngredientKey broken, long factor, long uses) {
		return new BookmarkItemMetadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.INGREDIENT,
			1, factor, BookmarkItemMetadata.CHANCE_FULL, CRAFTING, recipe, Set.of(key), container, uses, broken);
	}
}
