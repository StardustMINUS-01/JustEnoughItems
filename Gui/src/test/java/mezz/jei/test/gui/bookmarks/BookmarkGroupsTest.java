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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.List;
import java.util.LinkedList;

public class BookmarkGroupsTest {
	private static final RecipeTreeViewState TREE_STATE = new RecipeTreeViewState(
		1, 30, 50, true, false, true, false, "iron", 0, 3,
		new RecipeTreeViewState.Expansion(List.of(), -1)
	);
	private final BookmarkGroupManager<String> groups = new BookmarkGroupManager<>();

	@Test
	public void cachesTreeViews() {
		int first = groups.createGroup("first");
		int second = groups.createGroup("second");
		var savedGroups = groups.getGroups();
		Assertions.assertTrue(groups.getTreeViewState(first).isEmpty());
		groups.cacheTreeViewState(first, TREE_STATE);
		Assertions.assertEquals(TREE_STATE, groups.getTreeViewState(first).orElseThrow());
		Assertions.assertTrue(groups.getTreeViewState(second).isEmpty());
		Assertions.assertEquals(savedGroups, groups.getGroups());
		groups.cacheTreeViewState(second, TREE_STATE);
		for (int i = 0; i < 14; i++) {
			groups.cacheTreeViewState(groups.createGroup("cached"), TREE_STATE);
		}
		Assertions.assertTrue(groups.getTreeViewState(first).isPresent());
		groups.cacheTreeViewState(groups.createGroup("newest"), TREE_STATE);
		Assertions.assertTrue(groups.getTreeViewState(first).isPresent());
		Assertions.assertTrue(groups.getTreeViewState(second).isEmpty());
	}

	@Test
	public void clearsTreeViews() {
		int group = groups.createGroup("first");
		groups.cacheTreeViewState(group, TREE_STATE);
		groups.removeGroup(group);
		Assertions.assertEquals(group, groups.createGroup("replacement"));
		Assertions.assertTrue(groups.getTreeViewState(group).isEmpty());
		groups.cacheTreeViewState(group, TREE_STATE);
		groups.clear();
		Assertions.assertEquals(group, groups.createGroup("new world"));
		Assertions.assertTrue(groups.getTreeViewState(group).isEmpty());
		groups.cacheTreeViewState(999, TREE_STATE);
		Assertions.assertTrue(groups.getTreeViewState(999).isEmpty());
	}

	@Test
	public void preservesSourceIndices() {
		int id = groups.createGroup("Machines");
		groups.setItemMetadata("result", BookmarkItemMetadata.defaultForGroup(id));
		groups.setItemMetadata("input", BookmarkItemMetadata.defaultForGroup(id));
		var inputs = groups.getGroupRecipeInputs(new LinkedList<>(List.of("before", "result", "between", "input")), id);
		Assertions.assertEquals(List.of(1, 3), inputs.stream().map(input -> input.index()).toList());
		Assertions.assertEquals(List.of(groups.getItemMetadata("result"), groups.getItemMetadata("input")), inputs.stream().map(input -> input.metadata()).toList());
	}

	@Test
	public void reusesGroupIds() {

		Assertions.assertEquals(1, groups.createGroup("One"));
		Assertions.assertEquals(2, groups.createGroup("Two"));
		Assertions.assertEquals(3, groups.createGroup("Three"));
		Assertions.assertTrue(groups.removeGroup(2));
		Assertions.assertEquals(2, groups.createGroup("Replacement"));
		Assertions.assertEquals(4, groups.createGroup("Four"));
	}

	@Test
	public void protectsDefaultGroup() {

		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, groups.getGroupId("iron"));
		Assertions.assertEquals(List.of(new BookmarkGroup(BookmarkGroupManager.DEFAULT_GROUP_ID, "Bookmarks")), groups.getGroups());
		Assertions.assertEquals(BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID), groups.getItemMetadata("iron"));

		Assertions.assertFalse(groups.removeGroup(BookmarkGroupManager.DEFAULT_GROUP_ID));
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, groups.getGroupId("iron"));
	}

	@Test
	public void keepsCollapsedItems() {
		int id = groups.createGroup("Machines");

		groups.addItem("iron", false);
		groups.addItem("gear", false);
		groups.addItem("plate", false);
		groups.moveItemToGroup("gear", id);
		groups.moveItemToGroup("plate", id);
		groups.toggleCollapsed(id);

		Assertions.assertEquals(List.of("iron", "gear", "plate"), groups.getVisibleItems(List.of("iron", "gear", "plate")));
		Assertions.assertEquals(id, groups.getGroupId("gear"));
	}

	@ParameterizedTest
	@CsvSource({"DEFAULT, true", "TODO_LIST, true", "TODO_LIST, false"})
	public void preservesViewMode(BookmarkViewMode mode, boolean chain) {
		int id = groups.createGroup("Machines");
		groups.setViewMode(id, mode);
		groups.setCraftingMode(id, !chain);

		groups.setCraftingMode(id, chain);

		BookmarkGroup group = groups.getGroup(id).orElseThrow();
		Assertions.assertEquals(chain, group.craftingMode());
		Assertions.assertEquals(mode, group.viewMode());
	}

	@Test
	public void togglesIndependently() {
		int id = groups.createGroup("Machines");
		BookmarkGroup initial = groups.getGroup(id).orElseThrow();
		BookmarkGroup todo = initial.withViewMode(BookmarkViewMode.TODO_LIST);
		BookmarkGroup folded = new BookmarkGroup(id, "Machines", BookmarkViewMode.DEFAULT, true, false, Set.of());

		groups.toggleViewMode(id);
		Assertions.assertEquals(todo, groups.getGroup(id).orElseThrow());
		groups.toggleViewMode(id);
		Assertions.assertEquals(initial, groups.getGroup(id).orElseThrow());
		groups.toggleViewMode(id);
		groups.toggleCollapsed(id);
		Assertions.assertEquals(folded.withViewMode(BookmarkViewMode.TODO_LIST), groups.getGroup(id).orElseThrow());
		groups.toggleViewMode(id);
		Assertions.assertEquals(folded, groups.getGroup(id).orElseThrow());
		groups.toggleCollapsed(id);
		Assertions.assertEquals(initial, groups.getGroup(id).orElseThrow());
		groups.toggleCollapsed(id);
		groups.toggleCollapsed(id);
		Assertions.assertEquals(initial, groups.getGroup(id).orElseThrow());
	}

	@Test
	public void ungroupsRemovedItems() {
		int id = groups.createGroup("Machines");

		groups.addItem("gear", false);
		groups.moveItemToGroup("gear", id);

		Assertions.assertTrue(groups.removeGroup(id));
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, groups.getGroupId("gear"));
		Assertions.assertEquals(List.of("gear"), groups.getVisibleItems(List.of("gear")));
	}

	@Test
	public void preservesMovedMetadata() {
		int id = groups.createGroup("Machines");
		groups.addItem("gear", false);
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			id,
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
	public void refreshesChainDetails() {
		int id = groups.createGroup("Machines");
		groups.setCraftingMode(id, true);
		addPlateRecipe(id, 2);
		groups.setItemMetadata("machine", metadata(id, BookmarkItemType.RESULT, "test:machine", "machine", 1, 1));
		groups.setItemMetadata("machine_plate", metadata(id, BookmarkItemType.INGREDIENT, "test:machine", "plate", 1, 1));

		groups.refreshRecipeChainDetails(List.of("plate", "ingot", "machine", "machine_plate"));

		var details = groups.getRecipeChainDetails(id).orElseThrow();
		Assertions.assertEquals(Set.of(ResourceLocation.fromNamespaceAndPath("test", "machine")), details.outputRecipes());
		Assertions.assertEquals(Set.of(ResourceLocation.fromNamespaceAndPath("test", "plate")), details.middleRecipes());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(3).type());
		Assertions.assertEquals(0, details.calculatedItems().get(3).requiredAmount());
	}

	@Test
	public void refreshesDirtyDetails() {
		int id = groups.createGroup("Machines");
		groups.setCraftingMode(id, true);
		addPlateRecipe(id, 2);

		groups.markRecipeChainDetailsDirty(List.of("plate", "ingot"));

		var details = groups.getRecipeChainDetails(id).orElseThrow();
		Assertions.assertEquals(Set.of(ResourceLocation.fromNamespaceAndPath("test", "plate")), details.outputRecipes());
		Assertions.assertEquals(RecipeChainItemType.INGREDIENT, details.calculatedItems().get(1).type());
	}

	@Test
	public void omitsNonChainDetails() {
		int id = groups.createGroup("Machines");
		groups.setItemMetadata("plate", metadata(id, BookmarkItemType.RESULT, "test:plate", "plate", 1, 1));

		groups.refreshRecipeChainDetails(List.of("plate"));

		Assertions.assertTrue(groups.getRecipeChainDetails(id).isEmpty());
	}

	@Test
	public void exportsNonChainInputs() {
		int id = groups.createGroup("Machines");
		addPlateRecipe(id, 1);

		Assertions.assertEquals(
			List.of(0, 1),
			groups.getGroupRecipeInputs(List.of("plate", "ingot"), id).stream()
				.map(input -> input.index())
				.toList()
		);
	}

	private void addPlateRecipe(int id, long amount) {
		groups.setItemMetadata("plate", metadata(id, BookmarkItemType.RESULT, "test:plate", "plate", 1, 1));
		groups.setItemMetadata("ingot", metadata(id, BookmarkItemType.INGREDIENT, "test:plate", "ingot", amount, 1));
	}

	private static BookmarkItemMetadata metadata(
		int id,
		BookmarkItemType type,
		String recipeUid,
		String ingredientUid,
		long factor,
		long multiplier
	) {
		return new BookmarkItemMetadata(
			id,
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
