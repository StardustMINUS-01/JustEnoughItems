package mezz.jei.gui.bookmarks;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupingPlan;
import mezz.jei.gui.overlay.bookmarks.BookmarkItemMovePlan;
import mezz.jei.gui.overlay.bookmarks.BookmarkPanelLayout;
import mezz.jei.gui.overlay.elements.LayoutPlaceholderElement;
import mezz.jei.gui.util.FocusUtil;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class BookmarkListInvariantTest {
	private static final ResourceLocation RECIPE_TYPE = ResourceLocation.parse("minecraft:crafting");
	private static final ResourceLocation RECIPE = ResourceLocation.parse("test:plate");
	private static final ResourceLocation RECIPE_B = ResourceLocation.parse("test:machine");

	@BeforeAll
	public static void bootStrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void movingLooseBookmarkCannotSplitRecipeBlock() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark result = bookmark("result");
		TestBookmark loose = bookmark("loose");
		TestBookmark input = bookmark("input");

		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.addToListWithoutNotifying(loose, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "plate"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "ingot"));

		bookmarks.moveBookmarks(List.of(loose), input, BookmarkGroupManager.DEFAULT_GROUP_ID, 0);

		Assertions.assertEquals(List.of(result, input, loose), bookmarks.getBookmarks());
	}

	@Test
	public void removingLastBookmarkInGroupRemovesEmptyGroupMetadata() {
		BookmarkList bookmarks = bookmarkList();
		int groupId = 1;
		TestBookmark bookmark = bookmark("plate");

		bookmarks.addGroupFromConfig(new BookmarkGroup(groupId, "Machines"));
		bookmarks.addToListWithoutNotifying(bookmark, false);
		bookmarks.moveBookmarkMetadataFromConfig(bookmark, BookmarkItemMetadata.defaultForGroup(groupId));

		bookmarks.remove(bookmark);

		Assertions.assertTrue(bookmarks.getBookmarkGroups().stream().noneMatch(group -> group.id() == groupId));
	}

	@Test
	public void removingRecipeBlockKeepsLooseItemBookmarkWithNullRecipeUid() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark result = bookmark("result");
		TestBookmark input = bookmark("input");
		TestBookmark loose = bookmark("loose");

		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.addToListWithoutNotifying(loose, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "plate"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "ingot"));

		boolean removed = bookmarks.removeRecipeBookmark(result, false);

		Assertions.assertTrue(removed);
		Assertions.assertEquals(List.of(loose), bookmarks.getBookmarks());
	}

	@Test
	public void removingRecipeBlockInCraftingGroupKeepsLooseItemBookmark() {
		BookmarkList bookmarks = bookmarkList();
		int groupId = 1;
		TestBookmark result = bookmark("result");
		TestBookmark input = bookmark("input");
		TestBookmark loose = bookmark("loose");

		bookmarks.addGroupFromConfig(new BookmarkGroup(groupId, "Machines", BookmarkViewMode.TODO_LIST, false, true, Set.of()));
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.addToListWithoutNotifying(loose, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(groupId, BookmarkItemType.RESULT, RECIPE, "plate"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(groupId, BookmarkItemType.INGREDIENT, RECIPE, "ingot"));
		bookmarks.moveBookmarkMetadataFromConfig(loose, BookmarkItemMetadata.defaultForGroup(groupId));
		bookmarks.notifyListenersOfChange();

		boolean removed = bookmarks.removeRecipeBookmark(result, true);

		Assertions.assertTrue(removed);
		Assertions.assertEquals(List.of(loose), bookmarks.getBookmarks());
	}

	@Test
	public void groupedRecipeCopyKeepsDistinctIdentityFromDefaultCopy() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> result = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));

		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(result));

		Assertions.assertEquals(1, bookmarks.getBookmarks().size());
		Assertions.assertEquals(groupId, ((RecipeBookmark<?, ?>) bookmarks.getBookmarks().get(0)).getEqualityScope());

		RecipeBookmark<Object, ItemStack> fresh = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		Assertions.assertTrue(bookmarks.addToListWithoutNotifying(fresh, false));
		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
		Assertions.assertNull(((RecipeBookmark<?, ?>) bookmarks.getBookmarks().get(1)).getEqualityScope());
	}

	@Test
	public void releasingRecipeIntoDefaultMergesMatchingCopy() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> groupedResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(groupedResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(
			groupedResult,
			metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.RESULT, RECIPE, 4, 3, 5000, Set.of(key("iron"), key("gold")))
		);
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(groupedResult));
		IBookmark currentGroupedResult = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(groupId, bookmarks.getBookmarkGroupId(currentGroupedResult));

		RecipeBookmark<Object, ItemStack> defaultResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(defaultResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(
			defaultResult,
			metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.RESULT, RECIPE, 1, 2, BookmarkItemMetadata.CHANCE_FULL, Set.of(key("iron")))
		);

		boolean changed = bookmarks.moveBookmarksToGroup(List.of(currentGroupedResult), BookmarkGroupManager.DEFAULT_GROUP_ID);

		Assertions.assertTrue(changed);
		Assertions.assertEquals(1, bookmarks.getBookmarks().size());
		BookmarkItemMetadata merged = bookmarks.getBookmarkMetadata(bookmarks.getBookmarks().get(0));
		Assertions.assertEquals(BookmarkItemType.RESULT, merged.type());
		Assertions.assertEquals(1, merged.multiplier());
		Assertions.assertEquals(4, merged.factor());
		Assertions.assertEquals(5000, merged.chance());
		Assertions.assertEquals(Set.of(key("iron"), key("gold")), merged.permutations());
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, merged.groupId());
	}

	@Test
	public void releasingRecipeWithoutDefaultCopyKeepsGroupParameters() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> groupedResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(groupedResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(
			groupedResult,
			metadata(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkItemType.RESULT, RECIPE, 4, 3, 5000, Set.of(key("iron")))
		);
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(groupedResult));
		IBookmark currentGroupedResult = bookmarks.getBookmarks().get(0);

		boolean changed = bookmarks.moveBookmarksToGroup(List.of(currentGroupedResult), BookmarkGroupManager.DEFAULT_GROUP_ID);

		Assertions.assertTrue(changed);
		Assertions.assertEquals(1, bookmarks.getBookmarks().size());
		BookmarkItemMetadata released = bookmarks.getBookmarkMetadata(bookmarks.getBookmarks().get(0));
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, released.groupId());
		Assertions.assertEquals(3, released.multiplier());
		Assertions.assertEquals(4, released.factor());
		Assertions.assertEquals(5000, released.chance());
		Assertions.assertNull(((RecipeBookmark<?, ?>) bookmarks.getBookmarks().get(0)).getEqualityScope());
	}

	@Test
	public void releasingPlainBookmarkStaysIndependentOfRecipeCopy() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> groupedResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(groupedResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(groupedResult, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		TestBookmark groupedLoose = bookmark("loose");
		bookmarks.addToListWithoutNotifying(groupedLoose, false);
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(groupedResult, groupedLoose));
		List<IBookmark> groupedBookmarks = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.toList();
		Assertions.assertEquals(2, groupedBookmarks.size());

		TestBookmark loose = bookmark("loose");
		bookmarks.addToListWithoutNotifying(loose, false);
		RecipeBookmark<Object, ItemStack> defaultResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(defaultResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(defaultResult, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));

		bookmarks.moveBookmarksToGroup(groupedBookmarks, BookmarkGroupManager.DEFAULT_GROUP_ID);

		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
		Assertions.assertTrue(bookmarks.getBookmarks().contains(defaultResult));
		Assertions.assertTrue(bookmarks.getBookmarks().contains(loose));
	}

	@Test
	public void movingRecipeIntoGroupWithSameRecipeSkipsDuplicate() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> groupResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(groupResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(groupResult, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(groupResult));

		RecipeBookmark<Object, ItemStack> defaultResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(defaultResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(defaultResult, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));

		boolean changed = bookmarks.moveBookmarksToGroup(List.of(defaultResult), groupId);

		Assertions.assertFalse(changed);
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, bookmarks.getBookmarkGroupId(defaultResult));
		Assertions.assertEquals(1, bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.count());
	}

	@Test
	public void groupingExistingBookmarksScopesAndAllowsNewDefaultCopy() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> result = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));

		int groupId = bookmarks.addRecipeBookmarkGroup("Machines", List.of(result));

		IBookmark grouped = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(groupId, ((RecipeBookmark<?, ?>) grouped).getEqualityScope());

		RecipeBookmark<Object, ItemStack> fresh = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		Assertions.assertTrue(bookmarks.addToListWithoutNotifying(fresh, false));
		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
	}

	@Test
	public void draggingGroupedRecipeToDefaultMergesMatchingCopy() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> groupedResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(groupedResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(groupedResult, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(groupedResult));
		IBookmark currentGrouped = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.findFirst()
			.orElseThrow();

		RecipeBookmark<Object, ItemStack> defaultResult = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(defaultResult, false);
		bookmarks.moveBookmarkMetadataFromConfig(defaultResult, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));

		bookmarks.moveBookmarks(List.of(currentGrouped), defaultResult, BookmarkGroupManager.DEFAULT_GROUP_ID, 0);

		Assertions.assertEquals(1, bookmarks.getBookmarks().size());
		Assertions.assertTrue(bookmarks.getBookmarks().contains(defaultResult));
	}

	@Test
	public void expandToRecipeBlocksIncludesHiddenMembers() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> result = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> input = recipeBookmark(Items.GOLD_INGOT, RecipeIngredientRole.INPUT);
		TestBookmark loose = bookmark("loose");
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.addToListWithoutNotifying(loose, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "gold"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(result, input));
		IBookmark currentResult = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.filter(b -> bookmarks.getBookmarkMetadata(b).type() == BookmarkItemType.RESULT)
			.findFirst()
			.orElseThrow();

		List<IBookmark> expanded = bookmarks.expandToRecipeBlocks(List.of(currentResult));

		Assertions.assertEquals(2, expanded.size());
		Assertions.assertEquals(List.of(loose), bookmarks.expandToRecipeBlocks(List.of(loose)));
	}

	@Test
	public void groupingPlanExcludeReleasesWholeBlockIncludingHiddenInputs() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> result = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> input = recipeBookmark(Items.GOLD_INGOT, RecipeIngredientRole.INPUT);
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "gold"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(result, input));
		IBookmark currentResult = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.filter(b -> bookmarks.getBookmarkMetadata(b).type() == BookmarkItemType.RESULT)
			.findFirst()
			.orElseThrow();

		BookmarkGroupingPlan plan = new BookmarkGroupingPlan(
			List.of(currentResult),
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			true,
			List.of()
		);
		Assertions.assertTrue(plan.apply(bookmarks, "Group"));

		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
		Assertions.assertTrue(bookmarks.getBookmarks().stream()
			.allMatch(b -> bookmarks.getBookmarkGroupId(b) == BookmarkGroupManager.DEFAULT_GROUP_ID));
		Assertions.assertTrue(bookmarks.getBookmarkGroups().stream().noneMatch(group -> group.id() == groupId));
	}

	@Test
	public void removingResultFromCollapsedChainRemovesWholeBlock() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> result = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> input = recipeBookmark(Items.GOLD_INGOT, RecipeIngredientRole.INPUT);
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "gold"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(result, input));
		bookmarks.setGroupCraftingMode(groupId, true);
		IBookmark currentResult = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.filter(b -> bookmarks.getBookmarkMetadata(b).type() == BookmarkItemType.RESULT)
			.findFirst()
			.orElseThrow();
		Assertions.assertTrue(bookmarks.isGroupCraftingMode(groupId));

		Assertions.assertTrue(bookmarks.removeRecipeBookmark(currentResult, false));

		Assertions.assertTrue(bookmarks.getBookmarks().isEmpty());
		Assertions.assertTrue(bookmarks.getBookmarkGroups().stream().noneMatch(group -> group.id() == groupId));
	}

	@Test
	public void releasingResultFromGroupNormalizesRemainingInputs() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> result = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> input = recipeBookmark(Items.GOLD_INGOT, RecipeIngredientRole.INPUT);
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "gold"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(result, input));
		IBookmark currentResult = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.filter(b -> bookmarks.getBookmarkMetadata(b).type() == BookmarkItemType.RESULT)
			.findFirst()
			.orElseThrow();

		Assertions.assertTrue(bookmarks.moveBookmarksToGroup(List.of(currentResult), BookmarkGroupManager.DEFAULT_GROUP_ID));

		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
		IBookmark released = bookmarks.getBookmarks().stream()
			.filter(b -> bookmarks.getBookmarkMetadata(b).type() == BookmarkItemType.RESULT)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(BookmarkGroupManager.DEFAULT_GROUP_ID, bookmarks.getBookmarkGroupId(released));
		IBookmark remaining = bookmarks.getBookmarks().stream()
			.filter(b -> b != released)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(BookmarkItemType.ITEM, bookmarks.getBookmarkMetadata(remaining).type());
		Assertions.assertEquals(groupId, bookmarks.getBookmarkGroupId(remaining));
	}

	@Test
	public void removingRecipeCleansStaleCollapsedRecipeIds() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> resultA = recipeBookmark(RECIPE, Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> inputA = recipeBookmark(RECIPE, Items.GOLD_INGOT, RecipeIngredientRole.INPUT);
		RecipeBookmark<Object, ItemStack> resultB = recipeBookmark(RECIPE_B, Items.DIAMOND, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> inputB = recipeBookmark(RECIPE_B, Items.EMERALD, RecipeIngredientRole.INPUT);
		bookmarks.addToListWithoutNotifying(resultA, false);
		bookmarks.addToListWithoutNotifying(inputA, false);
		bookmarks.addToListWithoutNotifying(resultB, false);
		bookmarks.addToListWithoutNotifying(inputB, false);
		bookmarks.moveBookmarkMetadataFromConfig(resultA, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		bookmarks.moveBookmarkMetadataFromConfig(inputA, metadata(BookmarkItemType.INGREDIENT, RECIPE, "gold"));
		bookmarks.moveBookmarkMetadataFromConfig(resultB, metadata(BookmarkItemType.RESULT, RECIPE_B, "diamond"));
		bookmarks.moveBookmarkMetadataFromConfig(inputB, metadata(BookmarkItemType.INGREDIENT, RECIPE_B, "emerald"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(resultA, inputA, resultB, inputB));
		bookmarks.setGroupViewMode(groupId, BookmarkViewMode.TODO_LIST);
		bookmarks.setGroupCraftingMode(groupId, true);
		bookmarks.setGroupCollapsedRecipeIds(groupId, Set.of(RECIPE, RECIPE_B));
		IBookmark currentResultA = bookmarks.getBookmarks().stream()
			.filter(b -> groupId == bookmarks.getBookmarkGroupId(b))
			.filter(b -> bookmarks.getBookmarkMetadata(b).type() == BookmarkItemType.RESULT)
			.filter(b -> RECIPE.equals(bookmarks.getBookmarkMetadata(b).recipeUid()))
			.findFirst()
			.orElseThrow();

		Assertions.assertTrue(bookmarks.removeRecipeBookmark(currentResultA, true));

		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
		BookmarkGroup group = bookmarks.getBookmarkGroups().stream()
			.filter(g -> g.id() == groupId)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(Set.of(RECIPE_B), group.collapsedRecipeIds());
	}

	@Test
	public void convertingChainToGroupClearsCollapsedRecipeIds() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> result = recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(result));
		bookmarks.setGroupViewMode(groupId, BookmarkViewMode.TODO_LIST);
		bookmarks.setGroupCraftingMode(groupId, true);
		bookmarks.setGroupCollapsedRecipeIds(groupId, Set.of(RECIPE));

		bookmarks.setGroupCraftingMode(groupId, false);

		BookmarkGroup group = bookmarks.getBookmarkGroups().stream()
			.filter(g -> g.id() == groupId)
			.findFirst()
			.orElseThrow();
		Assertions.assertFalse(group.craftingMode());
		Assertions.assertTrue(group.collapsedRecipeIds().isEmpty());
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, group.viewMode());
	}

	@Test
	public void togglingRecipeInsideCollapsedClosureTogglesTheRoot() {
		BookmarkList bookmarks = bookmarkList();
		int groupId = createTwoRecipeChain(bookmarks);
		bookmarks.setGroupCollapsedRecipeIds(groupId, Set.of(RECIPE));

		Assertions.assertTrue(bookmarks.toggleGroupCollapsedRecipeId(groupId, RECIPE_B));
		Assertions.assertTrue(bookmarks.getCollapsedRecipeIds(groupId).isEmpty());

		Assertions.assertTrue(bookmarks.toggleGroupCollapsedRecipeId(groupId, RECIPE_B));
		Assertions.assertEquals(Set.of(RECIPE_B), bookmarks.getCollapsedRecipeIds(groupId));
	}

	@Test
	public void togglingMiddleRecipeFlipsItsMultiplier() {
		BookmarkList bookmarks = bookmarkList();
		int groupId = createTwoRecipeChain(bookmarks);

		Assertions.assertTrue(bookmarks.toggleGroupCollapsedRecipeId(groupId, RECIPE_B));
		Assertions.assertEquals(Set.of(RECIPE_B), bookmarks.getCollapsedRecipeIds(groupId));
		for (IBookmark bookmark : bookmarks.getBookmarks()) {
			BookmarkItemMetadata metadata = bookmarks.getBookmarkMetadata(bookmark);
			if (RECIPE_B.equals(metadata.recipeUid())) {
				Assertions.assertEquals(0, metadata.multiplier());
			}
		}

		Assertions.assertTrue(bookmarks.toggleGroupCollapsedRecipeId(groupId, RECIPE_B));
		Assertions.assertTrue(bookmarks.getCollapsedRecipeIds(groupId).isEmpty());
		for (IBookmark bookmark : bookmarks.getBookmarks()) {
			BookmarkItemMetadata metadata = bookmarks.getBookmarkMetadata(bookmark);
			if (RECIPE_B.equals(metadata.recipeUid())) {
				Assertions.assertEquals(1, metadata.multiplier());
			}
		}
	}

	@Test
	public void displaySlotsAreCachedUntilVersionOrColumnsChange() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark bookmark = bookmark("iron");
		bookmarks.addToListWithoutNotifying(bookmark, false);
		bookmarks.moveBookmarkMetadataFromConfig(bookmark, metadata(BookmarkItemType.ITEM, null, "iron"));

		List<BookmarkDisplaySlot<IBookmark>> first = bookmarks.getDisplaySlots(3);
		List<BookmarkDisplaySlot<IBookmark>> cached = bookmarks.getDisplaySlots(3);
		Assertions.assertSame(first, cached);

		List<BookmarkDisplaySlot<IBookmark>> otherColumns = bookmarks.getDisplaySlots(4);
		Assertions.assertNotSame(first, otherColumns);

		bookmarks.setGroupViewMode(BookmarkGroupManager.DEFAULT_GROUP_ID, BookmarkViewMode.DEFAULT);
		List<BookmarkDisplaySlot<IBookmark>> refreshed = bookmarks.getDisplaySlots(3);
		Assertions.assertNotSame(first, refreshed);
		Assertions.assertSame(refreshed, bookmarks.getDisplaySlots(3));
	}

	@Test
	public void getMatchingBookmarkOnlyMatchesDefaultGroupBookmarks() {
		BookmarkList bookmarks = bookmarkList();
		RecipeBookmark<Object, ItemStack> defaultBookmark = recipeBookmark(RECIPE, Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		bookmarks.addToListWithoutNotifying(defaultBookmark, false);
		bookmarks.moveBookmarkMetadataFromConfig(defaultBookmark, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		Assertions.assertSame(
			defaultBookmark,
			bookmarks.getMatchingBookmark(new TestRecipeCategory(RECIPE).getRecipeType(), defaultBookmark.getRecipe())
		);

		RecipeBookmark<Object, ItemStack> groupedBookmark = new RecipeBookmark<>(
			new TestRecipeCategory(RECIPE_B),
			new Object(),
			RECIPE_B,
			new TestTypedIngredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.GOLD_INGOT)),
			RecipeIngredientRole.OUTPUT,
			null
		);
		bookmarks.addToListWithoutNotifying(groupedBookmark, false);
		bookmarks.createGroupForBookmarks("Machines", List.of(groupedBookmark));
		Assertions.assertNull(
			bookmarks.getMatchingBookmark(new TestRecipeCategory(RECIPE_B).getRecipeType(), groupedBookmark.getRecipe())
		);
	}

	@Test
	public void displayEntryUsesColumnAwareBorderData() {
		BookmarkList bookmarks = bookmarkList();
		int groupId = 1;
		TestBookmark result = bookmark("plate");
		bookmarks.addGroupFromConfig(new BookmarkGroup(groupId, "Machines", BookmarkViewMode.DEFAULT, true, false, Set.of()));
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(groupId, BookmarkItemType.RESULT, RECIPE, "plate"));

		bookmarks.getDisplaySlots(3);
		BookmarkDisplayEntry<IBookmark> entry = bookmarks.getDisplayEntry(result).orElseThrow();
		BookmarkSlotBorder border = entry.border();
		Assertions.assertNotNull(border);
		Assertions.assertTrue(border.left());
		Assertions.assertTrue(border.right());
		Assertions.assertTrue(border.top());
		Assertions.assertTrue(border.bottom());
	}

	@Test
	public void displayEntryUsesLatestNonUniformRowLayout() {
		BookmarkList bookmarks = bookmarkList();
		int groupId = 1;
		TestBookmark first = bookmark("plate");
		TestBookmark second = bookmark("gear");
		TestBookmark third = bookmark("machine");
		bookmarks.addGroupFromConfig(new BookmarkGroup(groupId, "Machines", BookmarkViewMode.DEFAULT, true, false, Set.of()));
		for (TestBookmark bookmark : List.of(first, second, third)) {
			bookmarks.addToListWithoutNotifying(bookmark, false);
			bookmarks.moveBookmarkMetadataFromConfig(bookmark, BookmarkItemMetadata.defaultForGroup(groupId));
		}

		BookmarkDisplayEntry<IBookmark> expected = bookmarks.getDisplaySlots(3, List.of(2, 3)).stream()
			.map(BookmarkDisplaySlot::entry)
			.filter(entry -> entry.item().equals(second))
			.findFirst()
			.orElseThrow();
		BookmarkDisplayEntry<IBookmark> actual = bookmarks.getDisplayEntry(second).orElseThrow();

		Assertions.assertNotNull(expected.border());
		Assertions.assertTrue(expected.border().right());
		Assertions.assertEquals(expected, actual);
	}

	@Test
	public void catalystBookmarkAmountIsNotShiftedByScroll() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark catalyst = bookmark("mold");
		bookmarks.addToListWithoutNotifying(catalyst, false);
		bookmarks.moveBookmarkMetadataFromConfig(catalyst, metadata(BookmarkItemType.NONCONSUMABLE, RECIPE, "mold"));

		long multiplierBefore = bookmarks.getBookmarkMetadata(catalyst).multiplier();

		Assertions.assertFalse(bookmarks.shiftBookmarkAmount(catalyst, 1));
		Assertions.assertFalse(bookmarks.shiftBookmarkAmount(catalyst, -1));
		Assertions.assertEquals(multiplierBefore, bookmarks.getBookmarkMetadata(catalyst).multiplier());
	}

	@Test
	public void toggleBookmarkInputCatalystSwitchesBetweenInputAndCatalyst() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark input = bookmark("ingot");
		bookmarks.addToListWithoutNotifying(input, false);
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.INGREDIENT,
			3,
			4,
			BookmarkItemMetadata.CHANCE_FULL,
			RECIPE_TYPE,
			RECIPE,
			Set.of(new BookmarkIngredientKey("test:item", "ingot"))
		);
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata);

		Assertions.assertTrue(bookmarks.toggleBookmarkInputCatalyst(input));
		BookmarkItemMetadata catalyst = bookmarks.getBookmarkMetadata(input);
		Assertions.assertEquals(BookmarkItemType.NONCONSUMABLE, catalyst.type());
		Assertions.assertEquals(3, catalyst.multiplier());
		Assertions.assertEquals(4, catalyst.factor());

		Assertions.assertTrue(bookmarks.toggleBookmarkInputCatalyst(input));
		BookmarkItemMetadata restoredInput = bookmarks.getBookmarkMetadata(input);
		Assertions.assertEquals(BookmarkItemType.INGREDIENT, restoredInput.type());
		Assertions.assertEquals(3, restoredInput.multiplier());
		Assertions.assertEquals(4, restoredInput.factor());
	}

	@Test
	public void toggleBookmarkInputCatalystKeepsResultsUntouched() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark result = bookmark("plate");
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "plate"));

		Assertions.assertFalse(bookmarks.toggleBookmarkInputCatalyst(result));
		Assertions.assertEquals(BookmarkItemType.RESULT, bookmarks.getBookmarkMetadata(result).type());
	}

	@Test
	public void toggleBookmarkInputCatalystRejectsPlainBookmarks() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark item = bookmark("iron");
		bookmarks.addToListWithoutNotifying(item, false);
		bookmarks.moveBookmarkMetadataFromConfig(item, metadata(BookmarkItemType.ITEM, null, "iron"));

		Assertions.assertFalse(bookmarks.toggleBookmarkInputCatalyst(item));
		Assertions.assertEquals(BookmarkItemType.ITEM, bookmarks.getBookmarkMetadata(item).type());
	}

	@Test
	public void amountScrollDoesNotToggleCatalystAndToggleDoesNotChangeAmount() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark input = bookmark("ingot");
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "ingot"));

		Assertions.assertTrue(bookmarks.shiftBookmarkAmount(input, 1));
		BookmarkItemMetadata afterScroll = bookmarks.getBookmarkMetadata(input);
		Assertions.assertEquals(BookmarkItemType.INGREDIENT, afterScroll.type());
		Assertions.assertEquals(2, afterScroll.multiplier());

		Assertions.assertTrue(bookmarks.toggleBookmarkInputCatalyst(input));
		BookmarkItemMetadata afterToggle = bookmarks.getBookmarkMetadata(input);
		Assertions.assertEquals(BookmarkItemType.NONCONSUMABLE, afterToggle.type());
		Assertions.assertEquals(2, afterToggle.multiplier());
	}

	private static int createTwoRecipeChain(BookmarkList bookmarks) {
		RecipeBookmark<Object, ItemStack> aResult = recipeBookmark(RECIPE, Items.IRON_INGOT, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> aInput = recipeBookmark(RECIPE, Items.GOLD_INGOT, RecipeIngredientRole.INPUT);
		RecipeBookmark<Object, ItemStack> bResult = recipeBookmark(RECIPE_B, Items.GOLD_INGOT, RecipeIngredientRole.OUTPUT);
		RecipeBookmark<Object, ItemStack> bInput = recipeBookmark(RECIPE_B, Items.DIAMOND, RecipeIngredientRole.INPUT);
		bookmarks.addToListWithoutNotifying(aResult, false);
		bookmarks.addToListWithoutNotifying(aInput, false);
		bookmarks.addToListWithoutNotifying(bResult, false);
		bookmarks.addToListWithoutNotifying(bInput, false);
		bookmarks.moveBookmarkMetadataFromConfig(aResult, metadata(BookmarkItemType.RESULT, RECIPE, "iron"));
		bookmarks.moveBookmarkMetadataFromConfig(aInput, metadata(BookmarkItemType.INGREDIENT, RECIPE, "gold"));
		bookmarks.moveBookmarkMetadataFromConfig(bResult, metadata(BookmarkItemType.RESULT, RECIPE_B, "gold"));
		bookmarks.moveBookmarkMetadataFromConfig(bInput, metadata(BookmarkItemType.INGREDIENT, RECIPE_B, "diamond"));
		int groupId = bookmarks.createGroupForBookmarks("Machines", List.of(aResult, aInput, bResult, bInput));
		bookmarks.setGroupViewMode(groupId, BookmarkViewMode.TODO_LIST);
		bookmarks.setGroupCraftingMode(groupId, true);
		return groupId;
	}

	private static BookmarkList bookmarkList() {
		return new BookmarkList(null, null, null, null, null, null, null);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	public void recipeInputsPreserveSparseSourcePositionsAndSelectedIngredients(int inputSource) {
		var ingredientManager = mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager();
		BookmarkList bookmarks = new BookmarkList(null, null, ingredientManager, null, null, null, null);
		int groupId = 1;
		bookmarks.addGroupFromConfig(new BookmarkGroup(groupId, "Selected"));
		bookmarks.setGroupCraftingMode(groupId, true);
		Assertions.assertTrue(bookmarks.getGroupRecipeInputs(groupId).isEmpty());
		Assertions.assertTrue(bookmarks.getRecipeChainInputs(groupId).isEmpty());
		Assertions.assertTrue(bookmarks.getRecipeChainTooltipInputs(groupId).isEmpty());
		List<RecipeBookmark<Object, ItemStack>> source = List.of(
			recipeBookmark(Items.IRON_INGOT, RecipeIngredientRole.OUTPUT),
			recipeBookmark(Items.GOLD_INGOT, RecipeIngredientRole.INPUT),
			recipeBookmark(Items.DIAMOND, RecipeIngredientRole.INPUT),
			recipeBookmark(Items.EMERALD, RecipeIngredientRole.INPUT),
			recipeBookmark(Items.COAL, RecipeIngredientRole.INPUT)
		);
		List<Integer> selectedIndices = List.of(1, 3, 4);
		for (int i = 0; i < source.size(); i++) {
			var bookmark = source.get(i);
			bookmarks.addToListWithoutNotifying(bookmark, false);
			var key = BookmarkItemMetadataFactory.createPermutationKey(bookmark.getElement().getTypedIngredient(), ingredientManager);
			bookmarks.moveBookmarkMetadataFromConfig(bookmark, metadata(
				selectedIndices.contains(i) ? groupId : 0, BookmarkItemType.INGREDIENT, RECIPE, i + 1, 2,
				BookmarkItemMetadata.CHANCE_FULL, Set.of(key)
			));
		}

		var inputs = switch (inputSource) {
			case 0 -> bookmarks.getGroupRecipeInputs(groupId);
			case 1 -> bookmarks.getRecipeChainInputs(groupId);
			default -> bookmarks.getRecipeChainTooltipInputs(groupId);
		};

		Assertions.assertEquals(selectedIndices, inputs.stream().map(input -> input.index()).toList());
		List<IBookmark> stored = bookmarks.getBookmarks();
		for (var input : inputs) {
			var bookmark = stored.get(input.index());
			Assertions.assertEquals(bookmarks.getBookmarkMetadata(bookmark), input.metadata());
			Assertions.assertSame(bookmark.getElement().getTypedIngredient(), input.selectedIngredient());
			Assertions.assertEquals(input.metadata().permutations().iterator().next(), input.selectedKey());
		}
	}

	@ParameterizedTest
	@CsvSource({"0.25,1,0,false", "0.5,0,0,true", "0.74,0,0,true", "0.75,2,1,false"})
	public void crossGroupDragPreservesQuarterRowBoundaries(double fraction, int groupId, int offset, boolean rejected) {
		var before = new BookmarkPanelLayout.RowSlot<IBookmark>(bookmark("before"), 1, new ImmutableRect2i(0, 0, 20, 20));
		var target = new BookmarkPanelLayout.RowSlot<IBookmark>(bookmark("target"), 0, new ImmutableRect2i(0, 20, 20, 20));
		var after = new BookmarkPanelLayout.RowSlot<IBookmark>(bookmark("after"), 2, new ImmutableRect2i(0, 40, 20, 20));
		Map<Integer, BookmarkGroup> groups = Map.of(
			1, new BookmarkGroup(1, "Before").withViewMode(BookmarkViewMode.TODO_LIST),
			2, new BookmarkGroup(2, "After").withViewMode(BookmarkViewMode.TODO_LIST)
		);
		var plan = BookmarkItemMovePlan.createCrossGroup(List.of(before, target, after), target, 20 + fraction * 20, groups, Set.of(), Map.of());
		Assertions.assertEquals(new BookmarkItemMovePlan(target.item(), groupId, offset, rejected), plan);
		Assertions.assertTrue(BookmarkItemMovePlan.createCrossGroup(List.of(target), target, 20 + fraction * 20, groups, Set.of(), Map.of()).rejected());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	public void movingRecipeUsesTargetBlockBoundary(int offset) {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark result = bookmark("result");
		TestBookmark input = bookmark("input");
		TestBookmark moving = bookmark("moving");
		bookmarks.addToListWithoutNotifying(result, false);
		bookmarks.addToListWithoutNotifying(input, false);
		bookmarks.addToListWithoutNotifying(moving, false);
		bookmarks.moveBookmarkMetadataFromConfig(result, metadata(BookmarkItemType.RESULT, RECIPE, "plate"));
		bookmarks.moveBookmarkMetadataFromConfig(input, metadata(BookmarkItemType.INGREDIENT, RECIPE, "ingot"));
		bookmarks.moveBookmarkMetadataFromConfig(moving, metadata(BookmarkItemType.RESULT, RECIPE_B, "machine"));
		new BookmarkMoveSelection(List.of(moving), true, false, Set.of(RECIPE_B))
			.moveToBookmark(bookmarks, offset == 0 ? input : result, 0, offset);
		Assertions.assertEquals(offset == 0 ? List.of(moving, result, input) : List.of(result, input, moving), bookmarks.getBookmarks());
	}

	@Test
	public void elementsPreservePlaceholdersBetweenNonUniformGroupRows() {
		BookmarkList bookmarks = bookmarkList();
		TestBookmark loose = bookmark("loose");
		TestBookmark grouped = bookmark("grouped");
		bookmarks.addToListWithoutNotifying(loose, false);
		bookmarks.addToListWithoutNotifying(grouped, false);
		bookmarks.addGroupFromConfig(new BookmarkGroup(1, "Group"));
		bookmarks.moveBookmarkMetadataFromConfig(grouped, BookmarkItemMetadata.defaultForGroup(1));
		List<IElement<?>> elements = bookmarks.getElements(3, List.of(2, 3));
		Assertions.assertEquals(3, elements.size());
		Assertions.assertEquals(Optional.of(loose), elements.get(0).getBookmark());
		Assertions.assertSame(LayoutPlaceholderElement.INSTANCE, elements.get(1));
		Assertions.assertEquals(Optional.of(grouped), elements.get(2).getBookmark());
	}

	private static TestBookmark bookmark(String id) {
		return new TestBookmark(id);
	}

	private static BookmarkItemMetadata metadata(BookmarkItemType type, ResourceLocation recipeUid, String ingredientUid) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			type,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			RECIPE_TYPE,
			recipeUid,
			Set.of(new BookmarkIngredientKey("test:item", ingredientUid))
		);
	}

	private static BookmarkItemMetadata metadata(
		int groupId,
		BookmarkItemType type,
		ResourceLocation recipeUid,
		String ingredientUid
	) {
		return new BookmarkItemMetadata(
			groupId,
			type,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			RECIPE_TYPE,
			recipeUid,
			Set.of(new BookmarkIngredientKey("test:item", ingredientUid))
		);
	}

	private static BookmarkItemMetadata metadata(
		int groupId,
		BookmarkItemType type,
		ResourceLocation recipeUid,
		long factor,
		long multiplier,
		long chance,
		Set<BookmarkIngredientKey> permutations
	) {
		return new BookmarkItemMetadata(
			groupId,
			type,
			multiplier,
			factor,
			chance,
			RECIPE_TYPE,
			recipeUid,
			permutations
		);
	}

	private static RecipeBookmark<Object, ItemStack> recipeBookmark(Item item, RecipeIngredientRole role) {
		return recipeBookmark(RECIPE, item, role);
	}

	private static RecipeBookmark<Object, ItemStack> recipeBookmark(ResourceLocation recipeUid, Item item, RecipeIngredientRole role) {
		return new RecipeBookmark<>(
			new TestRecipeCategory(recipeUid),
			new Object(),
			recipeUid,
			new TestTypedIngredient<>(VanillaTypes.ITEM_STACK, new ItemStack(item)),
			role,
			null
		);
	}

	private static BookmarkIngredientKey key(String uid) {
		return new BookmarkIngredientKey("test:item", uid);
	}

	private record TestTypedIngredient<T>(IIngredientType<T> type, T ingredient) implements ITypedIngredient<T> {
		@Override
		public IIngredientType<T> getType() {
			return type;
		}

		@Override
		public T getIngredient() {
			return ingredient;
		}
	}

	private record TestRecipeCategory(ResourceLocation recipeUid) implements IRecipeCategory<Object> {
		@Override
		public RecipeType<Object> getRecipeType() {
			return RecipeType.create(RECIPE_TYPE.getNamespace(), RECIPE_TYPE.getPath(), Object.class);
		}

		@Override
		public Component getTitle() {
			return Component.literal("test");
		}

		@Override
		public @Nullable IDrawable getIcon() {
			return null;
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Object recipe, IFocusGroup focuses) {
		}

		@Override
		public @Nullable ResourceLocation getRegistryName(Object recipe) {
			return recipeUid;
		}
	}

	private record TestBookmark(String id) implements IBookmark {
		@Override
		public BookmarkType getType() {
			return BookmarkType.INGREDIENT;
		}

		@Override
		public IElement<?> getElement() {
			return new TestElement(this);
		}

		@Override
		public boolean isVisible() {
			return true;
		}

		@Override
		public void setVisible(boolean visible) {
		}
	}

	private record TestElement(IBookmark bookmark) implements IElement<Object> {
		@Override
		public ITypedIngredient<Object> getTypedIngredient() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<IBookmark> getBookmark() {
			return Optional.of(bookmark);
		}

		@Override
		public @Nullable IDrawable createRenderOverlay() {
			return null;
		}

		@Override
		public void show(IRecipesGui recipesGui, FocusUtil focusUtil, List<RecipeIngredientRole> roles) {
		}

		@Override
		public void getTooltip(JeiTooltip tooltip, IngredientGridTooltipHelper tooltipHelper, IIngredientRenderer<Object> ingredientRenderer, IIngredientHelper<Object> ingredientHelper) {
		}

		@Override
		public boolean isVisible() {
			return true;
		}

		@Override
		public boolean handleClick(UserInput input, IInternalKeyMappings keyBindings) {
			return false;
		}

		@Override
		public void tick() {
		}
	}
}
