package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.List;

public class BookmarkGroupManagerTest {
	@Test
	public void defaultGroupExistsAndCannotBeRemoved() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();

		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, groups.getGroupId("iron"));
		Assertions.assertEquals(List.of(new BookmarkGroup(BookmarkGroupManager.DEFAULT_GROUP_ID, "Bookmarks")), groups.getGroups());
		Assertions.assertEquals(BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID), groups.getItemMetadata("iron"));

		Assertions.assertFalse(groups.removeGroup(BookmarkGroupManager.DEFAULT_GROUP_ID));
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, groups.getGroupId("iron"));
	}

	@Test
	public void collapsedGroupKeepsEveryNonIngredientVisible() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");

		groups.addItem("iron", false);
		groups.addItem("gear", false);
		groups.addItem("plate", false);
		groups.moveItemToGroup("gear", groupId);
		groups.moveItemToGroup("plate", groupId);
		groups.setViewMode(groupId, BookmarkViewMode.COLLAPSED);

		Assertions.assertEquals(List.of("iron", "gear", "plate"), groups.getVisibleItems(List.of("iron", "gear", "plate")));
		Assertions.assertEquals(groupId, groups.getGroupId("gear"));
	}

	@Test
	public void compactGroupConvertedToChainKeepsDefaultView() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");

		groups.setCraftingMode(groupId, true);

		BookmarkGroup group = groups.getGroup(groupId).orElseThrow();
		Assertions.assertTrue(group.craftingMode());
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, group.viewMode());
	}

	@Test
	public void newLineGroupConvertedToChainKeepsViewMode() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");
		groups.setViewMode(groupId, BookmarkViewMode.TODO_LIST);

		groups.setCraftingMode(groupId, true);

		BookmarkGroup group = groups.getGroup(groupId).orElseThrow();
		Assertions.assertTrue(group.craftingMode());
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, group.viewMode());
	}

	@Test
	public void chainConvertedToGroupKeepsViewMode() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");
		groups.setViewMode(groupId, BookmarkViewMode.TODO_LIST);
		groups.setCraftingMode(groupId, true);

		groups.setCraftingMode(groupId, false);

		BookmarkGroup group = groups.getGroup(groupId).orElseThrow();
		Assertions.assertFalse(group.craftingMode());
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, group.viewMode());
	}

	@Test
	public void viewModeToggleCyclesAndCollapseRemembersExpandedMode() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");

		groups.toggleViewMode(groupId);
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, groups.getGroup(groupId).orElseThrow().viewMode());

		groups.toggleViewMode(groupId);
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, groups.getGroup(groupId).orElseThrow().viewMode());

		groups.toggleViewMode(groupId);
		groups.toggleCollapsed(groupId);
		Assertions.assertEquals(BookmarkViewMode.COLLAPSED, groups.getGroup(groupId).orElseThrow().viewMode());

		groups.toggleViewMode(groupId);
		Assertions.assertEquals(BookmarkViewMode.COLLAPSED, groups.getGroup(groupId).orElseThrow().viewMode());

		groups.toggleCollapsed(groupId);
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, groups.getGroup(groupId).orElseThrow().viewMode());

		groups.toggleCollapsed(groupId);
		groups.toggleCollapsed(groupId);
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, groups.getGroup(groupId).orElseThrow().viewMode());
	}

	@Test
	public void removingGroupMovesItsItemsBackToDefaultGroup() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");

		groups.addItem("gear", false);
		groups.moveItemToGroup("gear", groupId);

		Assertions.assertTrue(groups.removeGroup(groupId));
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, groups.getGroupId("gear"));
		Assertions.assertEquals(List.of("gear"), groups.getVisibleItems(List.of("gear")));
	}

	@Test
	public void itemMetadataCarriesRecipeChainFieldsWhenMovedBetweenGroups() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");
		groups.addItem("gear", false);
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			groupId,
			BookmarkItemType.INGREDIENT,
			4,
			3,
			10_000,
			ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "iron_pickaxe"),
			Set.of(new BookmarkIngredientKey("minecraft:item_stack", "minecraft:iron_ingot", null))
		);

		groups.setItemMetadata("gear", metadata);
		groups.moveItemToGroup("gear", BookmarkGroupManager.DEFAULT_GROUP_ID);

		Assertions.assertEquals(
			metadata.withGroupId(BookmarkGroupManager.DEFAULT_GROUP_ID),
			groups.getItemMetadata("gear")
		);
	}

	@Test
	public void craftingGroupRefreshesRecipeChainDetailsFromOrderedItems() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");
		groups.setCraftingMode(groupId, true);
		groups.addItem("plate", false);
		groups.addItem("ingot", false);
		groups.addItem("machine", false);
		groups.addItem("machine_plate", false);
		groups.setItemMetadata("plate", metadata(groupId, BookmarkItemType.RESULT, "test:plate", "plate", 1, 1));
		groups.setItemMetadata("ingot", metadata(groupId, BookmarkItemType.INGREDIENT, "test:plate", "ingot", 2, 1));
		groups.setItemMetadata("machine", metadata(groupId, BookmarkItemType.RESULT, "test:machine", "machine", 1, 1));
		groups.setItemMetadata("machine_plate", metadata(groupId, BookmarkItemType.INGREDIENT, "test:machine", "plate", 1, 1));

		groups.refreshRecipeChainDetails(List.of("plate", "ingot", "machine", "machine_plate"));

		var details = groups.getRecipeChainDetails(groupId).orElseThrow();
		Assertions.assertEquals(Set.of(ResourceLocation.fromNamespaceAndPath("test", "machine")), details.outputRecipes());
		Assertions.assertEquals(Set.of(ResourceLocation.fromNamespaceAndPath("test", "plate")), details.middleRecipes());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(3).type());
		Assertions.assertEquals(0, details.calculatedItems().get(3).requiredAmount());
	}

	@Test
	public void nonCraftingGroupDoesNotKeepRecipeChainDetails() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");
		groups.addItem("plate", false);
		groups.setItemMetadata("plate", metadata(groupId, BookmarkItemType.RESULT, "test:plate", "plate", 1, 1));

		groups.refreshRecipeChainDetails(List.of("plate"));

		Assertions.assertTrue(groups.getRecipeChainDetails(groupId).isEmpty());
	}

	@Test
	public void nonCraftingGroupExposesRecipeInputsForBatchEncoding() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		String groupId = groups.createGroup("Machines");
		groups.addItem("plate", false);
		groups.addItem("ingot", false);
		groups.setItemMetadata("plate", metadata(groupId, BookmarkItemType.RESULT, "test:plate", "plate", 1, 1));
		groups.setItemMetadata("ingot", metadata(groupId, BookmarkItemType.INGREDIENT, "test:plate", "ingot", 1, 1));

		Assertions.assertEquals(
			List.of(0, 1),
			groups.getGroupRecipeInputs(List.of("plate", "ingot"), groupId).stream()
				.map(input -> input.index())
				.toList()
		);
	}

	private static BookmarkItemMetadata metadata(
		String groupId,
		BookmarkItemType type,
		String recipeUid,
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
			ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
			ResourceLocation.parse(recipeUid),
			Set.of(new BookmarkIngredientKey("test:item", ingredientUid, null))
		);
	}
}
