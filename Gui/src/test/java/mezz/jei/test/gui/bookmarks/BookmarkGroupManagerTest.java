package mezz.jei.test.gui.bookmarks;

import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainItemType;
import mezz.jei.gui.bookmarks.tree.RecipeTreeViewState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.List;
import java.util.LinkedList;

public class BookmarkGroupManagerTest {
	@Test
	public void treeSidebarStateIsPerGroupAndDoesNotAlterBookmarkGroupData() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int first = groups.createGroup("first");
		int second = groups.createGroup("second");
		var savedGroups = groups.getGroups();
		Assertions.assertTrue(groups.getTreeViewState(first).isEmpty());
		var state = new RecipeTreeViewState(1, 30, 50, true, false, true, false, "iron", 0, 3,
			new RecipeTreeViewState.Expansion(List.of(), -1));
		groups.cacheTreeViewState(first, state);
		Assertions.assertEquals(state, groups.getTreeViewState(first).orElseThrow());
		Assertions.assertTrue(groups.getTreeViewState(second).isEmpty());
		Assertions.assertEquals(savedGroups, groups.getGroups());
		groups.cacheTreeViewState(second, state);
		for (int i = 0; i < 14; i++) { groups.cacheTreeViewState(groups.createGroup("cached"), state); }
		Assertions.assertTrue(groups.getTreeViewState(first).isPresent());
		groups.cacheTreeViewState(groups.createGroup("newest"), state);
		Assertions.assertTrue(groups.getTreeViewState(first).isPresent());
		Assertions.assertTrue(groups.getTreeViewState(second).isEmpty());
	}

	@Test
	public void deletedOrReloadedGroupsDoNotInheritTreeSidebarState() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int group = groups.createGroup("first");
		var state = new RecipeTreeViewState(1, 0, 0, true, true, true, false, "", 0, 0,
			new RecipeTreeViewState.Expansion(List.of(), -1));
		groups.cacheTreeViewState(group, state);
		groups.removeGroup(group);
		Assertions.assertEquals(group, groups.createGroup("replacement"));
		Assertions.assertTrue(groups.getTreeViewState(group).isEmpty());
		groups.cacheTreeViewState(group, state);
		groups.clear();
		Assertions.assertEquals(group, groups.createGroup("new world"));
		Assertions.assertTrue(groups.getTreeViewState(group).isEmpty());
		groups.cacheTreeViewState(999, state);
		Assertions.assertTrue(groups.getTreeViewState(999).isEmpty());
	}

	@Test
	public void recipeInputsPreserveSourceIndicesAcrossOtherGroups() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");
		groups.setItemMetadata("result", BookmarkItemMetadata.defaultForGroup(groupId));
		groups.setItemMetadata("input", BookmarkItemMetadata.defaultForGroup(groupId));
		var inputs = groups.getGroupRecipeInputs(new LinkedList<>(List.of("before", "result", "between", "input")), groupId);
		Assertions.assertEquals(List.of(1, 3), inputs.stream().map(input -> input.index()).toList());
		Assertions.assertEquals(List.of(groups.getItemMetadata("result"), groups.getItemMetadata("input")), inputs.stream().map(input -> input.metadata()).toList());
	}

	@Test
	public void reusesSmallestAvailablePositiveGroupId() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();

		Assertions.assertEquals(1, groups.createGroup("One"));
		Assertions.assertEquals(2, groups.createGroup("Two"));
		Assertions.assertEquals(3, groups.createGroup("Three"));
		Assertions.assertTrue(groups.removeGroup(2));
		Assertions.assertEquals(2, groups.createGroup("Replacement"));
		Assertions.assertEquals(4, groups.createGroup("Four"));
	}

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
		int groupId = groups.createGroup("Machines");

		groups.addItem("iron", false);
		groups.addItem("gear", false);
		groups.addItem("plate", false);
		groups.moveItemToGroup("gear", groupId);
		groups.moveItemToGroup("plate", groupId);
		groups.toggleCollapsed(groupId);

		Assertions.assertEquals(List.of("iron", "gear", "plate"), groups.getVisibleItems(List.of("iron", "gear", "plate")));
		Assertions.assertEquals(groupId, groups.getGroupId("gear"));
	}

	@Test
	public void compactGroupConvertedToChainKeepsDefaultView() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");

		groups.setCraftingMode(groupId, true);

		BookmarkGroup group = groups.getGroup(groupId).orElseThrow();
		Assertions.assertTrue(group.craftingMode());
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, group.viewMode());
	}

	@Test
	public void newLineGroupConvertedToChainKeepsViewMode() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");
		groups.setViewMode(groupId, BookmarkViewMode.TODO_LIST);

		groups.setCraftingMode(groupId, true);

		BookmarkGroup group = groups.getGroup(groupId).orElseThrow();
		Assertions.assertTrue(group.craftingMode());
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, group.viewMode());
	}

	@Test
	public void chainConvertedToGroupKeepsViewMode() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");
		groups.setViewMode(groupId, BookmarkViewMode.TODO_LIST);
		groups.setCraftingMode(groupId, true);

		groups.setCraftingMode(groupId, false);

		BookmarkGroup group = groups.getGroup(groupId).orElseThrow();
		Assertions.assertFalse(group.craftingMode());
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, group.viewMode());
	}

	@Test
	public void viewModeAndCollapseToggleIndependently() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");

		groups.toggleViewMode(groupId);
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, groups.getGroup(groupId).orElseThrow().viewMode());

		groups.toggleViewMode(groupId);
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, groups.getGroup(groupId).orElseThrow().viewMode());

		groups.toggleViewMode(groupId);
		groups.toggleCollapsed(groupId);
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, groups.getGroup(groupId).orElseThrow().viewMode());
		Assertions.assertTrue(groups.getGroup(groupId).orElseThrow().collapsed());

		groups.toggleViewMode(groupId);
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, groups.getGroup(groupId).orElseThrow().viewMode());
		Assertions.assertTrue(groups.getGroup(groupId).orElseThrow().collapsed());

		groups.toggleCollapsed(groupId);
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, groups.getGroup(groupId).orElseThrow().viewMode());
		Assertions.assertFalse(groups.getGroup(groupId).orElseThrow().collapsed());

		groups.toggleCollapsed(groupId);
		groups.toggleCollapsed(groupId);
		Assertions.assertEquals(BookmarkViewMode.DEFAULT, groups.getGroup(groupId).orElseThrow().viewMode());
	}

	@Test
	public void removingGroupMovesItsItemsBackToDefaultGroup() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");

		groups.addItem("gear", false);
		groups.moveItemToGroup("gear", groupId);

		Assertions.assertTrue(groups.removeGroup(groupId));
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, groups.getGroupId("gear"));
		Assertions.assertEquals(List.of("gear"), groups.getVisibleItems(List.of("gear")));
	}

	@Test
	public void itemMetadataCarriesRecipeChainFieldsWhenMovedBetweenGroups() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");
		groups.addItem("gear", false);
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			groupId,
			BookmarkItemType.INGREDIENT,
			4,
			3,
			10_000,
			ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "iron_pickaxe"),
			Set.of(new BookmarkIngredientKey("minecraft:item_stack", "minecraft:iron_ingot"))
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
		int groupId = groups.createGroup("Machines");
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
	public void recipeChainDetailsRebuildLazilyAfterMarkingDirty() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");
		groups.setCraftingMode(groupId, true);
		groups.addItem("plate", false);
		groups.addItem("ingot", false);
		groups.setItemMetadata("plate", metadata(groupId, BookmarkItemType.RESULT, "test:plate", "plate", 1, 1));
		groups.setItemMetadata("ingot", metadata(groupId, BookmarkItemType.INGREDIENT, "test:plate", "ingot", 2, 1));

		groups.markRecipeChainDetailsDirty(List.of("plate", "ingot"));

		var details = groups.getRecipeChainDetails(groupId).orElseThrow();
		Assertions.assertEquals(Set.of(ResourceLocation.fromNamespaceAndPath("test", "plate")), details.outputRecipes());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
	}

	@Test
	public void nonCraftingGroupDoesNotKeepRecipeChainDetails() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");
		groups.addItem("plate", false);
		groups.setItemMetadata("plate", metadata(groupId, BookmarkItemType.RESULT, "test:plate", "plate", 1, 1));

		groups.refreshRecipeChainDetails(List.of("plate"));

		Assertions.assertTrue(groups.getRecipeChainDetails(groupId).isEmpty());
	}

	@Test
	public void nonCraftingGroupExposesRecipeInputsForBatchEncoding() {
		BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();
		int groupId = groups.createGroup("Machines");
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
		int groupId,
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
			Set.of(new BookmarkIngredientKey("test:item", ingredientUid))
		);
	}
}
