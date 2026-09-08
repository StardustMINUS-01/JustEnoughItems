package mezz.jei.test.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.gui.IRecipeSlotCandidateView;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkRecipeSelection;
import mezz.jei.gui.bookmarks.RecipeLayoutProjection;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.ingredientManager;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkRecipeSelectionTest {
	private static final IIngredientManager MANAGER = ingredientManager();
	@BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

	@Test void savedSubsetIsPinnedAndUnsavedInputIsEmpty() {
		var book = bookmarks();
		var original = layout("a", List.of(List.of(item(Items.OAK_PLANKS), item(Items.SPRUCE_PLANKS), item(Items.BIRCH_PLANKS)), List.of(item(Items.STICK))));
		var projection = new RecipeLayoutProjection(original, Map.of(), Map.of(0, List.of(item(Items.OAK_PLANKS), item(Items.SPRUCE_PLANKS))));
		int group = book.addRecipeLayoutProjectionBookmarkGroup(List.of(projection), false).orElseThrow();
		var planks = book.getBookmarks().stream().filter(value -> value.getElement().getTypedIngredient().getItemStack().orElseThrow().is(Items.OAK_PLANKS)).findFirst().orElseThrow();
		assertTrue(book.cycleBookmarkPermutation(planks, 1));
		book.getBookmarks().stream().filter(value -> value.getElement().getTypedIngredient().getItemStack().orElseThrow().is(Items.STICK)).findFirst().ifPresent(book::remove);
		Slot first = new Slot(original.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).getFirst().getAllIngredients().toList());
		Slot missing = new Slot(List.of(item(Items.STICK)));
		Slot output = new Slot(RecipeIngredientRole.OUTPUT, List.of(item(Items.CHEST)));
		Slot missingOutput = new Slot(RecipeIngredientRole.OUTPUT, List.of(item(Items.DIAMOND)));
		var saved = book.getRecipeChainTooltipInputs(group).stream().map(input -> new RecipeChainInput(input.index(),
			input.metadata().withPermutations(input.metadata().permutations().stream()
				.map(key -> new BookmarkIngredientKey(key.ingredientTypeUid(), key.ingredientUid())).collect(Collectors.toSet())),
			input.selectedKey(), input.selectedIngredient())).toList();
		var selection = new BookmarkRecipeSelection(preview(first, missing, output, missingOutput), saved, MANAGER);
		assertTrue(first.displayed.getItemStack().orElseThrow().is(Items.SPRUCE_PLANKS));
		assertEquals(2, first.candidates.size());
		assertNull(missing.displayed);
		assertTrue(missing.candidates.isEmpty());
		assertTrue(selection.source(missing.view).isEmpty());
		assertTrue(output.displayed.getItemStack().orElseThrow().is(Items.CHEST));
		assertTrue(selection.source(output.view).orElseThrow().metadata().type().isGraphOutput());
		assertNull(missingOutput.displayed);
		assertTrue(selection.scroll(0, 0, -1, false, book));
		assertTrue(first.displayed.getItemStack().orElseThrow().is(Items.OAK_PLANKS));
		assertEquals(1, inputAmount(book, group, Items.OAK_PLANKS));
		assertEquals(0, inputAmount(book, group, Items.STICK));
	}

	@Test void singleSlotChoiceSplitsEightInputsAndFamilyChoiceMergesThemAgain() {
		var book = bookmarks();
		var family = List.<ITypedIngredient<?>>of(item(Items.OAK_PLANKS), item(Items.SPRUCE_PLANKS));
		var recipe = layout("eight", Collections.nCopies(8, family));
		int group = book.addRecipeLayoutProjectionBookmarkGroup(List.of(new RecipeLayoutProjection(recipe)), false).orElseThrow();
		Slot[] slots = new Slot[8];
		Arrays.setAll(slots, ignored -> new Slot(family));
		var drawing = preview(slots);
		var selection = new BookmarkRecipeSelection(drawing, book.getRecipeChainTooltipInputs(group), MANAGER);
		assertTrue(selection.scroll(0, 0, -1, false, book));
		assertEquals(1, inputAmount(book, group, Items.SPRUCE_PLANKS));
		assertEquals(7, inputAmount(book, group, Items.OAK_PLANKS));
		selection = new BookmarkRecipeSelection(drawing, book.getRecipeChainTooltipInputs(group), MANAGER);
		assertTrue(slots[0].displayed.getItemStack().orElseThrow().is(Items.SPRUCE_PLANKS));
		assertTrue(slots[1].displayed.getItemStack().orElseThrow().is(Items.OAK_PLANKS));
		assertTrue(selection.scroll(0, 0, -1, true, book));
		assertEquals(8, inputAmount(book, group, Items.OAK_PLANKS));
		assertEquals(0, inputAmount(book, group, Items.SPRUCE_PLANKS));
	}

	@Test void addToTreeIsGroupScopedAndDuplicatesDoNotRestoreDeletedMaterials() {
		var book = bookmarks();
		var recipe = layout("recipe", List.of(List.of(item(Items.OAK_PLANKS)), List.of(item(Items.STICK))));
		int first = book.addRecipeLayoutProjectionBookmarkGroup(List.of(new RecipeLayoutProjection(recipe)), false).orElseThrow();
		int second = book.addRecipeLayoutProjectionBookmarkGroup(List.of(new RecipeLayoutProjection(layout("root", List.of(List.of(item(Items.IRON_INGOT)))))), false).orElseThrow();
		assertTrue(book.addRecipeToGroup(second, new RecipeLayoutProjection(recipe)));
		assertEquals(1, inputAmount(book, first, Items.STICK));
		book.getBookmarks().stream().filter(value -> book.getBookmarkGroupId(value) == second && value.getElement().getTypedIngredient().getItemStack().orElseThrow().is(Items.STICK))
			.findFirst().ifPresent(book::remove);
		int count = book.getBookmarks().size();
		assertFalse(book.addRecipeToGroup(second, new RecipeLayoutProjection(recipe)));
		assertEquals(count, book.getBookmarks().size());
		assertEquals(0, inputAmount(book, second, Items.STICK));
		assertFalse(book.addRecipeToGroup(-123, new RecipeLayoutProjection(recipe)));
	}

	@Test void middleSlotChoiceDoesNotMoveAfterSavedInputsAreMerged() {
		var book = bookmarks();
		var family = List.<ITypedIngredient<?>>of(item(Items.OAK_PLANKS), item(Items.SPRUCE_PLANKS));
		int group = book.addRecipeLayoutProjectionBookmarkGroup(List.of(new RecipeLayoutProjection(layout("eight", Collections.nCopies(8, family)))), false).orElseThrow();
		Slot[] slots = new Slot[8];
		Arrays.setAll(slots, ignored -> new Slot(family));
		var drawing = previewAt(3, slots);
		var selection = new BookmarkRecipeSelection(drawing, book.getRecipeChainTooltipInputs(group), MANAGER);
		assertTrue(selection.scroll(0, 0, -1, false, book));
		selection = new BookmarkRecipeSelection(drawing, book.getRecipeChainTooltipInputs(group), MANAGER, selection.selectedKeys());
		for (int i = 0; i < slots.length; i++) {
			assertTrue(slots[i].displayed.getItemStack().orElseThrow().is(i == 3 ? Items.SPRUCE_PLANKS : Items.OAK_PLANKS));
		}
		assertTrue(selection.scroll(0, 0, -1, false, book));
		assertEquals(8, inputAmount(book, group, Items.OAK_PLANKS));
	}

	@Test void invalidChoiceDoesNotPartiallyWriteBookmarks() {
		var book = bookmarks();
		int group = book.addRecipeLayoutProjectionBookmarkGroup(List.of(new RecipeLayoutProjection(layout("recipe", List.of(List.of(item(Items.OAK_PLANKS), item(Items.SPRUCE_PLANKS)))))), false).orElseThrow();
		var input = book.getRecipeChainTooltipInputs(group).stream().filter(value -> value.metadata().type().isGraphInput()).findFirst().orElseThrow();
		var before = List.copyOf(book.getBookmarks());
		assertFalse(book.applyRecipeInputChoices(List.of(new BookmarkRecipeSelection.Choice(input.index(), key(Items.OAK_PLANKS), key(Items.DIAMOND), 1))));
		assertFalse(book.applyRecipeInputChoices(List.of(new BookmarkRecipeSelection.Choice(input.index(), key(Items.OAK_PLANKS), key(Items.SPRUCE_PLANKS), 2))));
		assertEquals(before, book.getBookmarks());
	}

	@Test void catalystKeepsSavedQuantityAndTypeAcrossRepeatedEdits() {
		var book = bookmarks();
		var family = List.<ITypedIngredient<?>>of(item(Items.IRON_INGOT), item(Items.GOLD_INGOT));
		int group = book.addRecipeLayoutProjectionBookmarkGroup(List.of(new RecipeLayoutProjection(layout("catalyst", List.of(family)))), false).orElseThrow();
		var input = book.getBookmarks().stream().filter(value -> book.getBookmarkMetadata(value).type().isGraphInput()).findFirst().orElseThrow();
		book.setBookmarkMetadata(input, book.getBookmarkMetadata(input).withType(BookmarkItemType.NONCONSUMABLE).withFactor(64).withMultiplier(32));
		Slot slot = new Slot(family);
		var drawing = preview(slot);
		for (int i = 0; i < 3; i++) {
			var selection = new BookmarkRecipeSelection(drawing, book.getRecipeChainTooltipInputs(group), MANAGER);
			assertEquals(64, slot.displayed.getItemStack().orElseThrow().getCount());
			assertTrue(selection.scroll(0, 0, -1, false, book));
			var catalyst = book.getRecipeChainTooltipInputs(group).stream().filter(value -> value.metadata().type().isNonConsumable()).findFirst().orElseThrow();
			assertEquals(64, catalyst.metadata().factor());
			assertEquals(64, catalyst.metadata().amount(32));
			assertTrue(book.getRecipeChainDetails(group).orElseThrow().suppliers().isEmpty());
		}
	}

	private static long inputAmount(BookmarkList book, int group, Item item) {
		return book.getBookmarks().stream().filter(value -> book.getBookmarkGroupId(value) == group && book.getBookmarkMetadata(value).type().isGraphInput() &&
			value.getElement().getTypedIngredient().getItemStack().orElseThrow().is(item)).mapToLong(value -> book.getBookmarkMetadata(value).factor()).sum();
	}
	private static BookmarkList bookmarks() { return new BookmarkList(null, null, MANAGER, null, null, null, null); }
	private static BookmarkIngredientKey key(Item item) { return BookmarkItemMetadataFactory.createPermutationKey(item(item), MANAGER); }
	private static IRecipeLayoutDrawable<?> layout(String id, List<? extends List<ITypedIngredient<?>>> inputs) {
		return RecipeLayoutTestFixtures.layout(RecipeType.create("test", "processing", Object.class), new Object(), ResourceLocation.fromNamespaceAndPath("test", id), inputs, List.of(List.of(item(Items.CHEST))));
	}
	private static IRecipeLayoutDrawable<?> preview(Slot... slots) {
		return previewAt(0, slots);
	}
	private static IRecipeLayoutDrawable<?> previewAt(int hovered, Slot... slots) {
		return (IRecipeLayoutDrawable<?>) Proxy.newProxyInstance(IRecipeLayoutDrawable.class.getClassLoader(), new Class<?>[]{IRecipeLayoutDrawable.class}, (proxy, method, args) -> switch (method.getName()) {
			case "getRecipeSlotsView" -> (IRecipeSlotsView) () -> Arrays.stream(slots).map(slot -> (IRecipeSlotView) slot.view).toList();
			case "getSlotUnderMouse" -> Optional.of(new RecipeSlotUnderMouse(slots[hovered].view, 0, 0));
			default -> throw new AssertionError(method.getName());
		});
	}
	private static class Slot {
		ITypedIngredient<?> displayed;
		List<ITypedIngredient<?>> candidates = List.of();
		final IRecipeSlotDrawable view;
		Slot(List<ITypedIngredient<?>> original) {
			this(RecipeIngredientRole.INPUT, original);
		}
		@SuppressWarnings("unchecked")
		Slot(RecipeIngredientRole role, List<ITypedIngredient<?>> original) {
			displayed = original.getFirst();
			view = (IRecipeSlotDrawable) Proxy.newProxyInstance(IRecipeSlotDrawable.class.getClassLoader(), new Class<?>[]{IRecipeSlotDrawable.class, IRecipeSlotCandidateView.class}, (proxy, method, args) -> switch (method.getName()) {
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				case "getRole" -> role;
				case "getAllIngredients" -> original.stream();
				case "getDisplayedIngredient" -> Optional.ofNullable(displayed);
				case "setDisplayedCandidates" -> { candidates = (List<ITypedIngredient<?>>) args[0]; yield null; }
				case "clearDisplayOverrides" -> { displayed = null; yield null; }
				case "createDisplayOverrides" -> Proxy.newProxyInstance(IIngredientConsumer.class.getClassLoader(), new Class<?>[]{IIngredientConsumer.class}, (p, m, a) -> {
					if (m.getName().equals("addTypedIngredient")) {
						displayed = (ITypedIngredient<?>) a[0]; return p;
					}
					throw new AssertionError(m.getName());
				});
				default -> throw new AssertionError(method.getName());
			});
		}
	}
}
