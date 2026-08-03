package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkDisplayGenerator;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkSlotBorder;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BookmarkDisplayGeneratorTest {
	private static final String GROUP_ID = "group_1";
	private static final ResourceLocation PLATE_RECIPE = ResourceLocation.parse("test:plate");
	private static final ResourceLocation MACHINE_RECIPE = ResourceLocation.parse("test:machine");

	@Test
	public void collapsedRecipeFlattensClosureIntoAnchorAndShadows() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "machine_plate");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 2, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"machine_plate", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "plate", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(MACHINE_RECIPE));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(MACHINE_RECIPE));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of(0, 1), slots.stream().map(slot -> slot.slotIndex()).toList());
		Assertions.assertEquals(List.of("machine", "ingot"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertFalse(slots.get(0).shadow());
		Assertions.assertTrue(slots.get(1).shadow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(0).entry().displayRecipeUid().orElseThrow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(1).entry().displayRecipeUid().orElseThrow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(0).entry().collapsedBlockId());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(1).entry().collapsedBlockId());
		Assertions.assertEquals(2, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
	}

	@Test
	public void generatorStartsNewGroupBoundariesOnNextRowWhenColumnsAreKnown() {
		List<String> orderedItems = List.of("loose_1", "grouped_1", "grouped_2", "loose_2");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"loose_1", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_1", 1, 1),
			"grouped_1", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "grouped_1", 1, 1),
			"grouped_2", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "grouped_2", 1, 1),
			"loose_2", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_2", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines");

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(),
			3
		);

		Assertions.assertEquals(List.of(0, 3, 4, 6), slots.stream().map(slot -> slot.slotIndex()).toList());
		Assertions.assertEquals(List.of("loose_1", "grouped_1", "grouped_2", "loose_2"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void generatorWrapsTodoListRecipeBlockWithoutForcingNextPage() {
		List<String> orderedItems = List.of(
			"plate",
			"ingot",
			"screw",
			"machine",
			"machine_plate",
			"machine_gear",
			"machine_circuit"
		);
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"screw", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "screw", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"machine_plate", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "plate", 1, 1),
			"machine_gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1),
			"machine_circuit", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "circuit", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(
			List.of(0, 1, 2, 3, 4, 5, 7),
			slots.stream().map(slot -> slot.slotIndex()).toList()
		);
		Assertions.assertEquals(
			List.of("plate", "ingot", "screw", "machine", "machine_plate", "machine_gear", "machine_circuit"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
	}

	@Test
	public void resultOnlyCraftingGroupKeepsEveryCalculatedRecipeOutput() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "gear");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("plate", "machine"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void expandedCraftingGroupKeepsCatalystInputsVisibleWithoutChainProjection() {
		List<String> orderedItems = List.of("plate", "mold");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"mold", metadata(BookmarkItemType.CATALYST, PLATE_RECIPE, "mold", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("plate", "mold"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertEquals(BookmarkItemType.CATALYST, slots.get(1).entry().metadata().type());
		Assertions.assertTrue(slots.get(1).entry().recipeChainItem().isEmpty());
	}

	@Test
	public void compactCraftingGroupShowsAllMaterials() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "gear");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(
			List.of("plate", "ingot", "machine", "gear"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		Assertions.assertEquals(List.of(0, 1, 2, 3), slots.stream().map(slot -> slot.slotIndex()).toList());
	}

	@Test
	public void resultOnlyCraftingGroupHidesCatalystInputs() {
		List<String> orderedItems = List.of("plate", "mold");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"mold", metadata(BookmarkItemType.CATALYST, PLATE_RECIPE, "mold", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("plate"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void collapsedCraftingRecipeHidesItsCatalystInputs() {
		List<String> orderedItems = List.of("plate", "mold");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"mold", metadata(BookmarkItemType.CATALYST, PLATE_RECIPE, "mold", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(PLATE_RECIPE));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(PLATE_RECIPE));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("plate"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void collapsedTopRecipeFlattensWholeClosureLikeGtnh() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		ResourceLocation recipeC = ResourceLocation.parse("test:c");
		List<String> orderedItems = List.of("out_a", "in_b", "in_c", "in_b_result", "in_a", "in_b2", "in_c_result", "in_d", "in_e");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_c", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_c", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 2, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1),
			"in_b2", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_b2", 1, 1),
			"in_c_result", metadata(BookmarkItemType.RESULT, recipeC, "in_c", 1, 1),
			"in_d", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_d", 1, 1),
			"in_e", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_e", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(recipeA));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(
			List.of("out_a", "in_b_result", "in_a", "in_b2", "in_d", "in_e"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		Assertions.assertFalse(slots.get(0).shadow());
		Assertions.assertEquals(recipeA, slots.get(0).entry().collapsedBlockId());
		for (int i = 1; i < slots.size(); i++) {
			Assertions.assertTrue(slots.get(i).shadow());
			Assertions.assertEquals(recipeA, slots.get(i).entry().collapsedBlockId());
			Assertions.assertEquals(recipeA, slots.get(i).entry().displayRecipeUid().orElseThrow());
		}
		// B produces 2x in_b and A consumes 1, so the remainder shadow keeps shiftAmount 1.
		Assertions.assertEquals(1, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
		Assertions.assertEquals(RecipeChainItemType.REMAINDER, slots.get(1).entry().recipeChainItem().orElseThrow().type());
	}

	@Test
	public void collapsedRecipeAggregatesSharedIngredients() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		ResourceLocation recipeC = ResourceLocation.parse("test:c");
		List<String> orderedItems = List.of("out_a", "in_b", "in_c", "in_b_result", "shared_b", "b2", "in_c_result", "shared_c", "c2");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_c", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_c", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 1, 1),
			"shared_b", metadata(BookmarkItemType.INGREDIENT, recipeB, "shared", 1, 1),
			"b2", metadata(BookmarkItemType.INGREDIENT, recipeB, "b2", 1, 1),
			"in_c_result", metadata(BookmarkItemType.RESULT, recipeC, "in_c", 1, 1),
			"shared_c", metadata(BookmarkItemType.INGREDIENT, recipeC, "shared", 1, 1),
			"c2", metadata(BookmarkItemType.INGREDIENT, recipeC, "c2", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(recipeA));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(
			List.of("out_a", "shared_b", "b2", "c2"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		Assertions.assertEquals(2, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
	}

	@Test
	public void collapsedSingleRecipeShowsAnchorAndIngredientShadows() {
		List<String> orderedItems = List.of("plate", "ingot");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 2, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(PLATE_RECIPE));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(PLATE_RECIPE));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("plate", "ingot"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertFalse(slots.get(0).shadow());
		Assertions.assertTrue(slots.get(1).shadow());
		Assertions.assertEquals(2, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
	}

	@Test
	public void collapsedChainGroupDrawsBlueBorderAndTruncatesToSingleRow() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "gear", "screw", "circuit");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "gear", 1, 1),
			"screw", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "screw", 1, 1),
			"circuit", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "circuit", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.COLLAPSED, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		// Only results are visible and the collapsed row is truncated after the first line.
		Assertions.assertEquals(List.of("plate", "machine", "gear"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertEquals(List.of(0, 1, 2), slots.stream().map(slot -> slot.slotIndex()).toList());
		for (var slot : slots) {
			BookmarkSlotBorder border = slot.entry().border();
			Assertions.assertNotNull(border);
			Assertions.assertEquals(BookmarkSlotBorder.GROUP_CHAIN_COLOR, border.color());
		}
		BookmarkSlotBorder first = slots.get(0).entry().border();
		BookmarkSlotBorder middle = slots.get(1).entry().border();
		BookmarkSlotBorder last = slots.get(2).entry().border();
		Assertions.assertTrue(first.left());
		Assertions.assertFalse(first.right());
		Assertions.assertFalse(middle.left());
		Assertions.assertFalse(middle.right());
		Assertions.assertFalse(last.left());
		Assertions.assertTrue(last.right());
		Assertions.assertTrue(first.top());
		Assertions.assertTrue(first.bottom());
		Assertions.assertTrue(middle.top());
		Assertions.assertTrue(middle.bottom());
		Assertions.assertTrue(last.top());
		Assertions.assertTrue(last.bottom());
	}

	@Test
	public void collapsedRegularGroupDrawsGrayBorder() {
		List<String> orderedItems = List.of("loose_1", "loose_2", "loose_3");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"loose_1", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "loose_1", 1, 1),
			"loose_2", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "loose_2", 1, 1),
			"loose_3", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "loose_3", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.COLLAPSED, null, false, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(),
			3
		);

		Assertions.assertEquals(List.of("loose_1", "loose_2", "loose_3"), slots.stream().map(slot -> slot.entry().item()).toList());
		for (var slot : slots) {
			BookmarkSlotBorder border = slot.entry().border();
			Assertions.assertNotNull(border);
			Assertions.assertEquals(BookmarkSlotBorder.GROUP_NONE_COLOR, border.color());
		}
		Assertions.assertTrue(slots.get(0).entry().border().left());
		Assertions.assertTrue(slots.get(2).entry().border().right());
	}

	@Test
	public void collapsedRecipeBlockDrawsPurpleBorder() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		List<String> orderedItems = List.of("out_a", "in_b", "in_b_result", "in_a");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 1, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(recipeA));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("out_a", "in_a"), slots.stream().map(slot -> slot.entry().item()).toList());
		for (var slot : slots) {
			BookmarkSlotBorder border = slot.entry().border();
			Assertions.assertNotNull(border);
			Assertions.assertEquals(BookmarkSlotBorder.RECIPE_COLOR, border.color());
		}
		BookmarkSlotBorder first = slots.get(0).entry().border();
		BookmarkSlotBorder second = slots.get(1).entry().border();
		Assertions.assertTrue(first.left());
		Assertions.assertFalse(first.right());
		Assertions.assertFalse(second.left());
		Assertions.assertTrue(second.right());
	}

	@Test
	public void defaultChainGroupHasNoBorder() {
		List<String> orderedItems = List.of("plate", "ingot");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("plate"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertNull(slots.get(0).entry().border());
	}

	@Test
	public void collapsedBlockInResultOnlyModeShowsOnlyAnchorAndRemainders() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		ResourceLocation recipeC = ResourceLocation.parse("test:c");
		List<String> orderedItems = List.of("out_a", "in_b", "in_c", "in_b_result", "in_a", "in_b2", "in_c_result", "in_d", "in_e");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_c", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_c", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 2, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1),
			"in_b2", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_b2", 1, 1),
			"in_c_result", metadata(BookmarkItemType.RESULT, recipeC, "in_c", 1, 1),
			"in_d", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_d", 1, 1),
			"in_e", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_e", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, null, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(recipeA));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("out_a", "in_b_result"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void collapsedBlockEmitsEvenWhenClosureStartsWithIngredientInResultOnlyMode() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		List<String> orderedItems = List.of("in_b", "out_a", "in_b_result", "in_a");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 1, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, null, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(recipeA));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("out_a"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void collapsedBlockWrapsLikeSingleRecipe() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		ResourceLocation recipeC = ResourceLocation.parse("test:c");
		List<String> orderedItems = List.of("out_a", "in_b", "in_c", "in_b_result", "in_a", "in_b2", "in_c_result", "in_d", "in_e");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_c", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_c", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 2, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1),
			"in_b2", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_b2", 1, 1),
			"in_c_result", metadata(BookmarkItemType.RESULT, recipeC, "in_c", 1, 1),
			"in_d", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_d", 1, 1),
			"in_e", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_e", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(recipeA));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(List.of(0, 1, 2, 4, 5, 7), slots.stream().map(slot -> slot.slotIndex()).toList());
	}

	@Test
	public void catalystInsideCollapsedClosureIsHidden() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		List<String> orderedItems = List.of("out_a", "in_b", "in_b_result", "in_a", "cat_b");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 1, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1),
			"cat_b", metadata(BookmarkItemType.CATALYST, recipeB, "cat_b", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, null, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(recipeA));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of("out_a", "in_a"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void resultOnlyStandardGroupKeepsEveryRecipeOutput() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "gear");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.COLLAPSED, null, false, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of()
		);

		Assertions.assertEquals(List.of("plate", "machine"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void resultOnlyCraftingGroupWrapsOutputsSequentially() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "gear");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(List.of("plate", "machine"), slots.stream().map(slot -> slot.entry().item()).toList());
		// Result-only outputs wrap like normal bookmarks instead of forcing one recipe per row.
		Assertions.assertEquals(List.of(0, 1), slots.stream().map(slot -> slot.slotIndex()).toList());
	}

	@Test
	public void resultOnlyGroupStaysIsolatedOnItsOwnRows() {
		List<String> orderedItems = List.of("loose_1", "plate", "ingot", "machine", "gear", "loose_2");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"loose_1", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_1", 1, 1),
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1),
			"loose_2", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_2", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, null, true, Set.of());
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(
			List.of("loose_1", "plate", "machine", "loose_2"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		// loose_1 is on row 0, the result-only group occupies row 1 (indexes 3, 4),
		// and loose_2 starts on a fresh row so it never shares the result-only group's rows.
		Assertions.assertEquals(List.of(0, 3, 4, 6), slots.stream().map(slot -> slot.slotIndex()).toList());
	}

	@Test
	public void resultOnlyStandardGroupWrapsOutputsSequentially() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "gear");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.COLLAPSED, null, false, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(),
			3
		);

		Assertions.assertEquals(List.of("plate", "machine"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertEquals(List.of(0, 1), slots.stream().map(slot -> slot.slotIndex()).toList());
	}

	private static RecipeChainDetails createDetails(
		List<String> orderedItems,
		Map<String, BookmarkItemMetadata> metadata,
		Set<ResourceLocation> collapsedRecipes
	) {
		List<RecipeChainInput> inputs = orderedItems.stream()
			.map(item -> new RecipeChainInput(orderedItems.indexOf(item), metadata.get(item)))
			.toList();
		return RecipeChainMath.refresh(inputs, collapsedRecipes);
	}

	private static BookmarkItemMetadata metadata(
		BookmarkItemType type,
		ResourceLocation recipeUid,
		String ingredientUid,
		long factor,
		long multiplier
	) {
		return metadata(GROUP_ID, type, recipeUid, ingredientUid, factor, multiplier);
	}

	private static BookmarkItemMetadata metadata(
		String groupId,
		BookmarkItemType type,
		ResourceLocation recipeUid,
		String ingredientUid,
		long factor,
		long multiplier
	) {
		return new BookmarkItemMetadata(
			groupId,
			type,
			multiplier,
			factor,
			BookmarkItemMetadata.CHANCE_FULL,
			ResourceLocation.parse("minecraft:crafting"),
			recipeUid,
			Set.of(new BookmarkIngredientKey("test:item", ingredientUid, null))
		);
	}
}
