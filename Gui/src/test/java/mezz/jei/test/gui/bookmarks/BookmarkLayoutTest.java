package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkDisplayGenerator;
import mezz.jei.gui.bookmarks.BookmarkDisplaySlot;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class BookmarkLayoutTest {
	private static final int GROUP_ID = 1;
	private static final ResourceLocation ROOT_RECIPE = ResourceLocation.parse("test:a");
	private static final ResourceLocation PLATE_RECIPE = ResourceLocation.parse("test:plate");
	private static final ResourceLocation MACHINE_RECIPE = ResourceLocation.parse("test:machine");

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void handlesSingleColumn(boolean folded) {
		var items = List.of("plate", "ingot", "screw");
		var metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"screw", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "screw", 1, 1));
		var collapsed = folded ? Set.of(PLATE_RECIPE) : Set.<ResourceLocation>of();
		var group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, collapsed);
		var details = createDetails(items, metadata, collapsed);
		var slots = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> BookmarkDisplayGenerator.generate(items, metadata::get, Map.of(GROUP_ID, group), Map.of(GROUP_ID, details), 1));
		Assertions.assertEquals(List.of(0, 1, 2), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
		Assertions.assertEquals(items, slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void indentsWithinRows() {
		var items = List.of("plate", "ingot", "screw", "wire", "circuit");
		Map<String, BookmarkItemMetadata> metadata = new HashMap<>();
		for (String item : items) {
			metadata.put(item, metadata(item.equals("plate") ? BookmarkItemType.RESULT : BookmarkItemType.INGREDIENT, PLATE_RECIPE, item, 1, 1));
		}
		var group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of());
		var details = createDetails(items, metadata, Set.of());
		var slots = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), () -> BookmarkDisplayGenerator.generate(items, metadata::get, Map.of(GROUP_ID, group), Map.of(GROUP_ID, details), 3, List.of(1, 1, 3)));
		Assertions.assertEquals(List.of(0, 1, 3, 4, 5), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
	}

	@Test
	public void preservesShadowSources() {
		List<String> items = new LinkedList<>(List.of("plate", "ingot", "machine", "machine_plate"));
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 2, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"machine_plate", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "plate", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(MACHINE_RECIPE));
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(List.of(0, 1), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
		Assertions.assertEquals(List.of("machine", "ingot"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertEquals(List.of(2, 1), slots.stream().map(slot -> slot.entry().sourceIndex()).toList());
		Assertions.assertFalse(slots.get(0).shadow());
		Assertions.assertTrue(slots.get(1).shadow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(0).entry().displayRecipeUid().orElseThrow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(1).entry().displayRecipeUid().orElseThrow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(0).entry().collapsedBlockId());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(1).entry().collapsedBlockId());
		Assertions.assertEquals(2, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
	}

	@Test
	public void startsGroupRows() {
		List<String> items = List.of("loose_1", "grouped_1", "grouped_2", "loose_2");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"loose_1", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_1", 1, 1),
			"grouped_1", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "grouped_1", 1, 1),
			"grouped_2", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "grouped_2", 1, 1),
			"loose_2", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_2", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines");

		var slots = BookmarkDisplayGenerator.generate(
			items,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(),
			3
		);

		Assertions.assertEquals(List.of(0, 3, 4, 6), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
		Assertions.assertEquals(List.of("loose_1", "grouped_1", "grouped_2", "loose_2"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void wrapsRecipeInputs() {
		List<String> items = List.of(
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
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of());
		RecipeChainDetails details = createDetails(items, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			items,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(
			List.of(0, 1, 2, 3, 4, 5, 7),
			slots.stream().map(BookmarkDisplaySlot::slotIndex).toList()
		);
		Assertions.assertEquals(
			List.of("plate", "ingot", "screw", "machine", "machine_plate", "machine_gear", "machine_circuit"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
	}

	@ParameterizedTest
	@CsvSource({"true, 0", "true, 3", "false, 0", "false, 3"})
	public void showsOutputs(boolean chain, int columns) {
		List<String> items = List.of("plate", "ingot", "machine", "gear");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, !chain, chain, Set.of());
		RecipeChainDetails details = createDetails(items, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			items,
			metadata::get,
			Map.of(GROUP_ID, group),
			chain ? Map.of(GROUP_ID, details) : Map.of(),
			columns
		);

		Assertions.assertEquals(List.of("plate", "machine"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertEquals(List.of(0, 1), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
	}

	@ParameterizedTest
	@CsvSource({"TODO_LIST, false", "DEFAULT, false", "TODO_LIST, true"})
	public void showsCatalysts(BookmarkViewMode mode, boolean folded) {
		List<String> items = List.of("plate", "mold");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"mold", metadata(BookmarkItemType.NONCONSUMABLE, PLATE_RECIPE, "mold", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", mode, false, true, folded ? Set.of(PLATE_RECIPE) : Set.of());
		var slots = generate(items, metadata, group);

		boolean visible = mode == BookmarkViewMode.TODO_LIST && !folded;
		Assertions.assertEquals(visible ? List.of("plate", "mold") : List.of("plate"),
			slots.stream().map(slot -> slot.entry().item()).toList());
		if (visible) {
			Assertions.assertEquals(BookmarkItemType.NONCONSUMABLE, slots.get(1).entry().metadata().type());
			Assertions.assertTrue(slots.get(1).entry().recipeChainItem().isEmpty());
		}
	}
	@Test
	public void showsAllMaterials() {
		List<String> items = List.of("plate", "ingot", "machine", "gear");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of());
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(
			List.of("plate", "ingot", "machine", "gear"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		Assertions.assertEquals(List.of(0, 1, 2, 3), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
	}

	@Test
	public void flattensDependencies() {
		var fixture = createBranch();
		var items = fixture.items();
		var metadata = fixture.metadata();
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(ROOT_RECIPE));
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(
			List.of("out_a", "in_b_result", "in_a", "in_b2", "in_d", "in_e"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		Assertions.assertFalse(slots.get(0).shadow());
		Assertions.assertEquals(ROOT_RECIPE, slots.get(0).entry().collapsedBlockId());
		for (int i = 1; i < slots.size(); i++) {
			Assertions.assertTrue(slots.get(i).shadow());
			Assertions.assertEquals(ROOT_RECIPE, slots.get(i).entry().collapsedBlockId());
			Assertions.assertEquals(ROOT_RECIPE, slots.get(i).entry().displayRecipeUid().orElseThrow());
		}
		Assertions.assertEquals(1, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
		Assertions.assertEquals(RecipeChainItemType.REMAINDER, slots.get(1).entry().recipeChainItem().orElseThrow().type());
	}

	@Test
	public void mergesSharedInputs() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		ResourceLocation recipeC = ResourceLocation.parse("test:c");
		List<String> items = List.of("out_a", "in_b", "in_c", "in_b_result", "shared_b", "b2", "in_c_result", "shared_c", "c2");
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
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(recipeA));
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(
			List.of("out_a", "shared_b", "b2", "c2"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		Assertions.assertEquals(2, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
	}

	@Test
	public void showsInputShadows() {
		List<String> items = List.of("plate", "ingot");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 2, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(PLATE_RECIPE));
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(List.of("plate", "ingot"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertFalse(slots.get(0).shadow());
		Assertions.assertTrue(slots.get(1).shadow());
		Assertions.assertEquals(2, slots.get(1).entry().recipeChainItem().orElseThrow().shiftAmount());
	}

	@Test
	public void bordersCollapsedChain() {
		List<String> items = List.of("plate", "ingot", "machine", "gear", "screw", "circuit");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "gear", 1, 1),
			"screw", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "screw", 1, 1),
			"circuit", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "circuit", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, true, true, Set.of());
		RecipeChainDetails details = createDetails(items, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			items,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(List.of("plate", "machine", "gear"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertEquals(List.of(0, 1, 2), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
		for (var slot : slots) {
			BookmarkSlotBorder border = slot.entry().border();
			Assertions.assertNotNull(border);
			Assertions.assertEquals(BookmarkSlotBorder.GROUP_CHAIN_COLOR, border.color());
			Assertions.assertTrue(border.top());
			Assertions.assertTrue(border.bottom());
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
	}

	@Test
	public void bordersRegularGroup() {
		List<String> items = List.of("loose_1", "loose_2", "loose_3");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"loose_1", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "loose_1", 1, 1),
			"loose_2", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "loose_2", 1, 1),
			"loose_3", metadata(GROUP_ID, BookmarkItemType.ITEM, null, "loose_3", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, true, false, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			items,
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
	public void bordersRecipeBlock() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		List<String> items = List.of("out_a", "in_b", "in_b_result", "in_a");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 1, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(recipeA));
		var slots = generate(items, metadata, group);

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
	public void omitsDefaultBorder() {
		List<String> items = List.of("plate", "ingot");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, false, true, Set.of());
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(List.of("plate"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertNull(slots.get(0).entry().border());
	}

	@Test
	public void followsRowOutline() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		List<String> items = new ArrayList<>(List.of("out_a"));
		Map<String, BookmarkItemMetadata> metadata = new HashMap<>();
		metadata.put("out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1));
		for (int i = 1; i <= 10; i++) {
			String item = "in_" + i;
			items.add(item);
			metadata.put(item, metadata(BookmarkItemType.INGREDIENT, recipeA, item, 1, 1));
		}
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(recipeA));
		RecipeChainDetails details = createDetails(items, metadata, Set.of(recipeA));
		List<Integer> rowWidths = List.of(2, 2, 3, 3, 3);

		var slots = BookmarkDisplayGenerator.generate(
			items,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3,
			rowWidths
		);

		// Preserve the original variable-width fixture, including slots beyond the supplied rows.
		Assertions.assertEquals(List.of(0, 1, 3, 5, 6, 8, 9, 11, 12, 13, 14),
			slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
		Set<Integer> left = Set.of(0, 3, 5, 8, 11);
		Set<Integer> right = Set.of(1, 3, 6, 9, 12, 14);
		Set<Integer> top = Set.of(0, 1, 6, 13);
		Set<Integer> bottom = Set.of(0, 11, 12, 13, 14);
		for (var slot : slots) {
			int index = slot.slotIndex();
			var border = slot.entry().border();
			Assertions.assertNotNull(border);
			Assertions.assertEquals(left.contains(index), border.left(), "left of slot " + index);
			Assertions.assertEquals(right.contains(index), border.right(), "right of slot " + index);
			Assertions.assertEquals(top.contains(index), border.top(), "top of slot " + index);
			Assertions.assertEquals(bottom.contains(index), border.bottom(), "bottom of slot " + index);
		}
	}
	@Test
	public void preservesRemainders() {
		var fixture = createBranch();
		var items = fixture.items();
		var metadata = fixture.metadata();
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, false, true, Set.of(ROOT_RECIPE));
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(List.of("out_a", "in_b_result"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void handlesInputFirst() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		List<String> items = List.of("in_b", "out_a", "in_b_result", "in_a");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 1, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, false, true, Set.of(recipeA));
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(List.of("out_a"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void wrapsCollapsedBlock() {
		var fixture = createBranch();
		var items = fixture.items();
		var metadata = fixture.metadata();
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(ROOT_RECIPE));
		RecipeChainDetails details = createDetails(items, metadata, Set.of(ROOT_RECIPE));

		var slots = BookmarkDisplayGenerator.generate(
			items,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(List.of(0, 1, 2, 4, 5, 7), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
	}

	@Test
	public void hidesNestedCatalysts() {
		ResourceLocation recipeA = ResourceLocation.parse("test:a");
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		List<String> items = List.of("out_a", "in_b", "in_b_result", "in_a", "cat_b");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, recipeA, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, recipeA, "in_b", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 1, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1),
			"cat_b", metadata(BookmarkItemType.NONCONSUMABLE, recipeB, "cat_b", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of(recipeA));
		var slots = generate(items, metadata, group);

		Assertions.assertEquals(List.of("out_a", "in_a"), slots.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	public void isolatesGroupRows() {
		List<String> items = List.of("loose_1", "plate", "ingot", "machine", "gear", "loose_2");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"loose_1", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_1", 1, 1),
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 1, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"gear", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "gear", 1, 1),
			"loose_2", metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.ITEM, null, "loose_2", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.DEFAULT, false, true, Set.of());
		RecipeChainDetails details = createDetails(items, metadata, Set.of());

		var slots = BookmarkDisplayGenerator.generate(
			items,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details),
			3
		);

		Assertions.assertEquals(
			List.of("loose_1", "plate", "machine", "loose_2"),
			slots.stream().map(slot -> slot.entry().item()).toList()
		);
		Assertions.assertEquals(List.of(0, 3, 4, 6), slots.stream().map(BookmarkDisplaySlot::slotIndex).toList());
	}

	private record Branch(List<String> items, Map<String, BookmarkItemMetadata> metadata) {
	}

	private static Branch createBranch() {
		ResourceLocation recipeB = ResourceLocation.parse("test:b");
		ResourceLocation recipeC = ResourceLocation.parse("test:c");
		List<String> items = List.of("out_a", "in_b", "in_c", "in_b_result", "in_a", "in_b2", "in_c_result", "in_d", "in_e");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"out_a", metadata(BookmarkItemType.RESULT, ROOT_RECIPE, "out_a", 1, 1),
			"in_b", metadata(BookmarkItemType.INGREDIENT, ROOT_RECIPE, "in_b", 1, 1),
			"in_c", metadata(BookmarkItemType.INGREDIENT, ROOT_RECIPE, "in_c", 1, 1),
			"in_b_result", metadata(BookmarkItemType.RESULT, recipeB, "in_b", 2, 1),
			"in_a", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_a", 1, 1),
			"in_b2", metadata(BookmarkItemType.INGREDIENT, recipeB, "in_b2", 1, 1),
			"in_c_result", metadata(BookmarkItemType.RESULT, recipeC, "in_c", 1, 1),
			"in_d", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_d", 1, 1),
			"in_e", metadata(BookmarkItemType.INGREDIENT, recipeC, "in_e", 1, 1)
		);
		return new Branch(items, metadata);
	}

	private static List<BookmarkDisplaySlot<String>> generate(
		List<String> items, Map<String, BookmarkItemMetadata> metadata, BookmarkGroup group
	) {
		var details = createDetails(items, metadata, group.collapsedRecipeIds());
		return BookmarkDisplayGenerator.generate(items, metadata::get, Map.of(GROUP_ID, group), Map.of(GROUP_ID, details));
	}

	private static RecipeChainDetails createDetails(
		List<String> items,
		Map<String, BookmarkItemMetadata> metadata,
		Set<ResourceLocation> collapsedRecipes
	) {
		List<RecipeChainInput> inputs = new ArrayList<>(items.size());
		for (String item : items) {
			inputs.add(new RecipeChainInput(inputs.size(), metadata.get(item)));
		}
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
		int groupId,
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
			Set.of(new BookmarkIngredientKey("test:item", ingredientUid))
		);
	}
}
