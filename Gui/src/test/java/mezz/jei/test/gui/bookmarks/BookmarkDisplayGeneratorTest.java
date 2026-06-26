package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkDisplayGenerator;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
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
	public void generatorAddsSlotIndexesAndDisplayRecipeIds() {
		List<String> orderedItems = List.of("plate", "ingot", "machine", "machine_plate");
		Map<String, BookmarkItemMetadata> metadata = Map.of(
			"plate", metadata(BookmarkItemType.RESULT, PLATE_RECIPE, "plate", 1, 1),
			"ingot", metadata(BookmarkItemType.INGREDIENT, PLATE_RECIPE, "ingot", 2, 1),
			"machine", metadata(BookmarkItemType.RESULT, MACHINE_RECIPE, "machine", 1, 1),
			"machine_plate", metadata(BookmarkItemType.INGREDIENT, MACHINE_RECIPE, "plate", 1, 1)
		);
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, true, false, Set.of(MACHINE_RECIPE));
		RecipeChainDetails details = createDetails(orderedItems, metadata, Set.of(MACHINE_RECIPE));

		var slots = BookmarkDisplayGenerator.generate(
			orderedItems,
			metadata::get,
			Map.of(GROUP_ID, group),
			Map.of(GROUP_ID, details)
		);

		Assertions.assertEquals(List.of(0, 1, 2), slots.stream().map(slot -> slot.slotIndex()).toList());
		Assertions.assertEquals(List.of("ingot", "machine", "machine_plate"), slots.stream().map(slot -> slot.entry().item()).toList());
		Assertions.assertTrue(slots.get(0).shadow());
		Assertions.assertFalse(slots.get(1).shadow());
		Assertions.assertFalse(slots.get(2).shadow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(0).entry().displayRecipeUid().orElseThrow());
		Assertions.assertEquals(MACHINE_RECIPE, slots.get(1).entry().displayRecipeUid().orElseThrow());
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
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", false);

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
		BookmarkGroup group = new BookmarkGroup(GROUP_ID, "Machines", BookmarkViewMode.TODO_LIST, true, false, Set.of());
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
