package mezz.jei.test.gui.bookmarks.tree;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.RecipeLayoutProjection;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkScrollHandler;
import mezz.jei.gui.bookmarks.tree.RecipeTreeBookmarkPanel;
import mezz.jei.gui.bookmarks.tree.RecipeTreeViewState;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeBookmarkPanelTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void handlesSingleColumn() {
		var book = book();
		int group = group(book, "a");
		book.setGroupCraftingMode(group, true);
		var narrow = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> book.getGroupEditorSlots(group, 1));
		assertEquals(book.getBookmarks().size(), narrow.size());
		assertEquals(List.of(0, 1), narrow.stream().map(slot -> slot.slotIndex()).toList());
		var wide = book.getGroupEditorSlots(group, 6);
		assertEquals(narrow.stream().map(slot -> slot.entry().item()).toList(), wide.stream().map(slot -> slot.entry().item()).toList());
	}

	@Test
	void scrollsByRow() {
		assertEquals(1, RecipeTreeBookmarkPanel.scrollRow(0, -0.1, 60, 6, 4));
		assertEquals(2, RecipeTreeBookmarkPanel.scrollRow(1, -12, 60, 6, 4));
		assertEquals(0, RecipeTreeBookmarkPanel.scrollRow(0, 1, 60, 6, 4));
		assertEquals(6, RecipeTreeBookmarkPanel.scrollRow(6, -1, 60, 6, 4));
		assertEquals(2, RecipeTreeBookmarkPanel.scrollRow(5, 0, 31, 6, 4));
		assertEquals(0, RecipeTreeBookmarkPanel.scrollRow(5, 0, 0, 6, 4));
	}

	@Test
	void cachesViewWithoutChanges() {
		var book = book();
		int group = group(book, "a");
		long version = book.getChangeVersion();
		assertTrue(book.getTreeViewState(group).isEmpty());
		var state = new RecipeTreeViewState(1, 0, 0, true, false, true, false, "", 0, 0,
			new RecipeTreeViewState.Expansion(List.of(), -1));
		book.cacheTreeViewState(group, state);
		assertEquals(state, book.getTreeViewState(group).orElseThrow());
		assertEquals(version, book.getChangeVersion());
	}

	@Test
	void preservesGroupProjection() {
		var book = book();
		group(book, "other");
		int group = group(book, "target");
		book.setGroupCraftingMode(group, true);
		book.toggleGroupCollapsed(group);
		var before = book.getBookmarkGroups();
		var slots = book.getGroupEditorSlots(group, 3);
		assertFalse(slots.isEmpty());
		assertEquals(0, slots.getFirst().slotIndex());
		assertTrue(slots.stream().allMatch(slot -> slot.entry().metadata().groupId() == group));
		assertTrue(slots.stream().anyMatch(slot -> slot.entry().metadata().type().isGraphInput()));
		assertTrue(slots.stream().allMatch(slot -> slot.entry().viewMode() == BookmarkViewMode.TODO_LIST && !slot.entry().collapsed()));
		for (var slot : slots) {
			assertSame(book.getBookmarks().get(slot.entry().sourceIndex()), slot.entry().item());
		}
		assertEquals(before, book.getBookmarkGroups());
		assertTrue(book.getGroupEditorSlots(-99, 3).isEmpty());
	}

	@Test
	void appliesBookmarkEdits() {
		var book = book();
		int first = group(book, "first");
		int second = group(book, "second");
		var input = input(book, first);
		var other = input(book, second);
		long version = book.getChangeVersion();
		assertFalse(scroll(book, input, 0, true, false, false, 16));
		assertFalse(scroll(book, input, 1, false, false, false, 16));
		assertEquals(version, book.getChangeVersion());
		assertTrue(scroll(book, input, 1, true, false, false, 16));
		assertEquals(2, book.getBookmarkMetadata(input).multiplier());
		assertEquals(1, book.getBookmarkMetadata(other).multiplier());
		assertTrue(scroll(book, input, 1, true, true, false, 16));
		assertEquals(18, book.getBookmarkMetadata(input).multiplier());
		assertTrue(scroll(book, input, 1, false, true, false, 16));
		assertTrue(book.getBookmarkMetadata(input).type().isNonConsumable());
		assertEquals(1, book.getBookmarkMetadata(input).amount());
		assertFalse(scroll(book, input, 1, true, false, false, 16));
		assertTrue(scroll(book, input, 1, false, false, true, 16));
		var switched = book.getBookmarks().stream()
			.filter(value -> book.getBookmarkGroupId(value) == first && book.getBookmarkMetadata(value).type().isNonConsumable())
			.findFirst().orElseThrow();
		assertTrue(switched.getElement().getTypedIngredient().getItemStack().orElseThrow().is(Items.SPRUCE_PLANKS));
		assertTrue(scroll(book, switched, -1, false, true, false, 16));
		assertTrue(book.getBookmarkMetadata(switched).type().isGraphInput());
	}

	@Test
	void preservesDeletion() {
		var book = book();
		int group = group(book, "a");
		int other = group(book, "other");
		book.setGroupCraftingMode(group, true);
		book.toggleGroupCollapsed(group);
		book.setGroupCollapsedRecipeIds(group, Set.of(ResourceLocation.fromNamespaceAndPath("test", "a")));
		var input = input(book, group);
		assertTrue(book.getGroupEditorSlots(group, 3).stream().anyMatch(slot -> slot.entry().item() == input));
		assertTrue(book.removeExpandedRecipeBookmark(input));
		assertFalse(book.contains(input));
		assertFalse(book.getGroupEditorSlots(group, 3).stream().anyMatch(slot -> slot.entry().item() == input));
		assertTrue(book.contains(input(book, other)));
	}

	@Test
	void keepsCatalystSlot() {
		var book = book();
		int group = createFoldedGroup(book, true);
		var target = input(book, group);
		int position = findPosition(book, group, target);
		for (int i = 0; i < 6; i++) {
			var hovered = getHoveredBookmark(book, group, position);
			assertSame(target, hovered);
			assertTrue(scroll(book, hovered, 1, false, true, false, 64));
			assertEquals(i % 2 == 0, book.getBookmarkMetadata(target).type().isNonConsumable());
			assertEquals(1, book.getBookmarkMetadata(target).amount());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void editsHoveredRecipe(boolean craftingMode) {
		var book = book();
		int group = createFoldedGroup(book, craftingMode);
		var target = book.getBookmarks().stream()
			.filter(value -> book.getBookmarkMetadata(value).recipeUid().getPath().equals("b") &&
				book.getBookmarkMetadata(value).type().isGraphInput())
			.findFirst().orElseThrow();
		int position = findPosition(book, group, target);
		long expected = 1;
		for (boolean alt : List.of(false, true, false, true)) {
			var hovered = getHoveredBookmark(book, group, position);
			assertSame(target, hovered);
			assertTrue(scroll(book, hovered, 1, true, alt, false, 16));
			expected += alt ? 16 : 1;
			for (var bookmark : book.getBookmarks()) {
				var metadata = book.getBookmarkMetadata(bookmark);
				assertEquals(metadata.recipeUid().getPath().equals("b") ? expected : 1, metadata.multiplier());
			}
			assertTrue(book.getBookmarkGroups().stream().filter(value -> value.id() == group).findFirst().orElseThrow().collapsed());
		}
	}

	private static int createFoldedGroup(BookmarkList book, boolean craftingMode) {
		int group = group(book, "a");
		book.addRecipeToGroup(group, recipe("b"));
		book.setGroupCraftingMode(group, craftingMode);
		book.toggleGroupCollapsed(group);
		return group;
	}

	private static int findPosition(BookmarkList book, int group, IBookmark target) {
		return book.getGroupEditorSlots(group, 3).stream()
			.filter(slot -> slot.entry().item() == target)
			.findFirst().orElseThrow().slotIndex();
	}

	private static IBookmark getHoveredBookmark(BookmarkList book, int group, int position) {
		var slot = book.getGroupEditorSlots(group, 3).stream()
			.filter(value -> value.slotIndex() == position).findFirst().orElseThrow();
		return book.createDisplayElement(slot.entry()).getBookmark().orElseThrow();
	}

	private static boolean scroll(BookmarkList book, IBookmark input, double delta, boolean control, boolean alt, boolean shift, long step) {
		return BookmarkScrollHandler.apply(book, input, delta, control, alt, shift, step, amount -> book.shiftRecipeAmount(input, amount));
	}

	private static BookmarkList book() {
		return new BookmarkList(null, null, ingredientManager(), null, null, null, null);
	}

	private static IBookmark input(BookmarkList book, int group) {
		return book.getBookmarks().stream()
			.filter(value -> book.getBookmarkGroupId(value) == group && book.getBookmarkMetadata(value).type().isGraphInput())
			.findFirst().orElseThrow();
	}

	private static int group(BookmarkList book, String id) {
		return book.addRecipeLayoutProjectionBookmarkGroup(List.of(recipe(id)), false).orElseThrow();
	}

	private static RecipeLayoutProjection recipe(String id) {
		return new RecipeLayoutProjection(RecipeLayoutTestFixtures.layout(RecipeType.create("test", "processing", Object.class), new Object(),
			ResourceLocation.fromNamespaceAndPath("test", id), List.of(List.of(item(Items.OAK_PLANKS), item(Items.SPRUCE_PLANKS))), List.of(List.of(item(Items.CHEST)))));
	}
}
