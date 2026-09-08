package mezz.jei.test.gui.bookmarks.tree;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import mezz.jei.gui.bookmarks.tree.RecipeTreeLayout;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTreeLayoutTest {
	@Test
	void snapshotRestoresSelectionAndManualFoldingInsteadOfInventoryFolding() {
		var inputs = List.of(input(0, "root", BookmarkItemType.RESULT, "result", 1),
			input(1, "root", BookmarkItemType.INGREDIENT, "plate", 2),
			input(2, "source", BookmarkItemType.RESULT, "plate", 1), input(3, "source", BookmarkItemType.INGREDIENT, "ore", 1));
		var details = RecipeChainMath.refresh(inputs, Set.of());
		var tree = new RecipeTreeLayout(inputs, details);
		var selected = tree.nodes().getLast();
		tree.updateDemands(List.of(input(-1, "unused", BookmarkItemType.ITEM, "plate", 2)), Set.of(), true);
		assertEquals(2, tree.nodes().size());
		var state = tree.captureExpansion(selected);
		var restored = new RecipeTreeLayout(inputs, details);
		assertEquals(3, restored.restoreExpansion(state).orElseThrow().target().index());
		assertEquals(3, restored.nodes().size());
		var changed = new RecipeTreeLayout(List.of(input(0, "new", BookmarkItemType.RESULT, "new", 1)),
			RecipeChainMath.refresh(List.of(input(0, "new", BookmarkItemType.RESULT, "new", 1)), Set.of()));
		assertTrue(changed.restoreExpansion(state).isEmpty());
		assertEquals(1, changed.nodes().size());
	}

	@Test
	void snapshotSizeIsBoundedEvenForThousandsOfIndependentRoots() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		Set<ResourceLocation> outputs = new HashSet<>();
		for (int i = 0; i < 4200; i++) {
			inputs.add(input(i, "r" + i, BookmarkItemType.RESULT, "p" + i, 1));
			outputs.add(id("r" + i));
		}
		var details = new RecipeChainDetails(Map.of(), Map.of(), Map.of(), outputs, Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Map.of());
		var tree = new RecipeTreeLayout(inputs, details);
		assertEquals(4096, tree.captureExpansion(null).branches().size());
	}

	@Test
	void demandIsConsumedAmountNotSupplierBatchAndPartialStockReducesChildren() {
		var inputs = List.of(input(0, "root", BookmarkItemType.RESULT, "result", 1),
			input(1, "root", BookmarkItemType.INGREDIENT, "gear", 6),
			input(2, "gear", BookmarkItemType.RESULT, "gear", 4), input(3, "gear", BookmarkItemType.INGREDIENT, "iron", 2));
		var details = RecipeChainMath.refresh(inputs, Set.of());
		var tree = new RecipeTreeLayout(inputs, details);
		tree.updateDemands(List.of(), Set.of(), false);
		var gear = tree.nodes().stream().filter(node -> node.target().index() == 2).findFirst().orElseThrow();
		var iron = tree.nodes().stream().filter(node -> node.target().index() == 3).findFirst().orElseThrow();
		assertEquals(6, gear.demandAmount());
		assertEquals(4, iron.demandAmount());
		var stock = input(-1, "unused", BookmarkItemType.ITEM, "gear", 4);
		tree.updateDemands(List.of(stock), Set.of(), true);
		assertEquals(2, gear.demandAmount());
		assertEquals(2, iron.demandAmount());
		assertTrue(gear.expanded());
		tree.updateDemands(List.of(), Set.of(), false);
		assertEquals(6, gear.demandAmount());
		assertEquals(4, iron.demandAmount());
		assertEquals(details, RecipeChainMath.refresh(inputs, Set.of()));
	}

	@Test
	void foldedBranchesKeepTheirAllocationAndCanReopenAtTheLimit() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (String recipe : List.of("left", "right")) {
			inputs.add(input(inputs.size(), recipe, BookmarkItemType.RESULT, recipe, 1));
			for (int i = 0; i < 2200; i++) {
				inputs.add(input(inputs.size(), recipe, BookmarkItemType.INGREDIENT, "ore", 1));
			}
		}
		var details = new RecipeChainDetails(Map.of(), Map.of(), Map.of(), Set.of(id("left"), id("right")), Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Map.of());
		var tree = new RecipeTreeLayout(inputs, details);
		var roots = tree.nodes().stream().filter(node -> node.parent() == null).toList();
		assertTrue(roots.getFirst().expanded());
		assertFalse(roots.getLast().expanded());
		assertTrue(tree.toggle(roots.getFirst()));
		assertEquals(2, tree.nodes().size());
		assertFalse(tree.toggle(roots.getLast()));
		assertTrue(tree.toggle(roots.getFirst()));
		assertEquals(2202, tree.nodes().size());
	}

	@Test
	void sharedStockAllocationDoesNotDependOnFoldingAndRestoresManualExpansion() {
		var inputs = List.of(input(0, "source", BookmarkItemType.RESULT, "plate", 1),
			input(1, "source", BookmarkItemType.INGREDIENT, "ore", 1),
			input(2, "left", BookmarkItemType.RESULT, "left", 1), input(3, "left", BookmarkItemType.INGREDIENT, "plate", 3),
			input(4, "right", BookmarkItemType.RESULT, "right", 1), input(5, "right", BookmarkItemType.INGREDIENT, "plate", 5));
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var copies = tree.nodes().stream().filter(node -> node.target().index() == 0).toList();
		tree.updateDemands(List.of(), Set.of(), false);
		assertEquals(List.of(3L, 5L), copies.stream().map(RecipeTreeLayout.Node::demandAmount).toList());
		var stock = List.of(input(-1, "unused", BookmarkItemType.ITEM, "plate", 4));
		tree.updateDemands(stock, Set.of(), true);
		assertEquals(List.of(0L, 4L), copies.stream().map(RecipeTreeLayout.Node::demandAmount).toList());
		assertFalse(copies.getFirst().expanded());
		assertTrue(copies.getLast().expanded());
		assertEquals(4, tree.nodes().stream().filter(node -> node.target().index() == 1).mapToLong(RecipeTreeLayout.Node::demandAmount).sum());
		tree.toggle(copies.getLast());
		tree.updateDemands(stock, Set.of(), true);
		assertEquals(List.of(0L, 4L), copies.stream().map(RecipeTreeLayout.Node::demandAmount).toList());
		tree.updateDemands(List.of(), Set.of(), false);
		assertTrue(copies.getFirst().expanded());
		assertFalse(copies.getLast().expanded());
	}

	@Test
	void searchFindsHiddenSharedBranchesWithoutExpandingOrChangingLayout() {
		var inputs = List.of(input(0, "source", BookmarkItemType.RESULT, "plate", 1),
			input(1, "source", BookmarkItemType.INGREDIENT, "ore", 1),
			input(2, "left", BookmarkItemType.RESULT, "left", 1), input(3, "left", BookmarkItemType.INGREDIENT, "plate", 1),
			input(4, "right", BookmarkItemType.RESULT, "right", 1), input(5, "right", BookmarkItemType.INGREDIENT, "plate", 1));
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var roots = tree.nodes().stream().filter(node -> node.parent() == null).toList();
		roots.forEach(tree::toggle);
		var before = List.copyOf(tree.nodes());
		var positions = before.stream().map(node -> List.of(node.x(), node.y())).toList();
		tree.search(input -> input.index() == 1);
		assertEquals(before, tree.nodes());
		assertEquals(positions, tree.nodes().stream().map(node -> List.of(node.x(), node.y())).toList());
		assertTrue(roots.stream().allMatch(tree::matchesSearch));
		tree.toggle(roots.getFirst());
		assertFalse(tree.matchesSearch(roots.getFirst()));
		assertTrue(tree.matchesSearch(roots.getLast()));
		assertTrue(tree.nodes().stream().filter(node -> node.target().index() == 1).allMatch(tree::matchesSearch));
		tree.search(input -> false);
		assertTrue(tree.nodes().stream().noneMatch(tree::matchesSearch));
		tree.search(input -> true);
		assertTrue(tree.nodes().stream().allMatch(tree::matchesSearch));
	}

	@Test
	void sharedMultiOutputSupplierAppearsOnBothPathsWithoutChangingTotals() {
		List<RecipeChainInput> inputs = List.of(
			input(0, "source", BookmarkItemType.RESULT, "plate", 2),
			input(1, "source", BookmarkItemType.RESULT, "dust", 1),
			input(2, "source", BookmarkItemType.INGREDIENT, "ore", 1),
			input(3, "case", BookmarkItemType.RESULT, "case", 1),
			input(4, "case", BookmarkItemType.INGREDIENT, "plate", 2),
			input(5, "gear", BookmarkItemType.RESULT, "gear", 1),
			input(6, "gear", BookmarkItemType.INGREDIENT, "dust", 1)
		);
		var details = RecipeChainMath.refresh(inputs, Set.of());
		assertEquals(0, details.suppliers().get(4));
		assertEquals(1, details.suppliers().get(6));
		var tree = new RecipeTreeLayout(inputs, details);
		var copies = tree.nodes().stream().filter(node -> node.recipe() && node.target().metadata().recipeUid().equals(id("source"))).toList();
		assertEquals(2, copies.size());
		assertNotSame(copies.getFirst(), copies.getLast());
		assertEquals(Set.of(0, 1), Set.of(copies.getFirst().target().index(), copies.getLast().target().index()));
		assertEquals(3, copies.getFirst().slots().size());
		tree.updateDemands(List.of(), Set.of(), false);
		assertEquals(1, tree.nodes().stream().filter(node -> node.target().index() == 2).mapToLong(RecipeTreeLayout.Node::demandAmount).sum());
		assertEquals(details, RecipeChainMath.refresh(inputs, Set.of()));
		assertNoOverlap(tree);
	}

	@Test
	void nonConsumablesRemainInNodeWithoutAChildAndDoNotScale() {
		var catalyst = input(2, "machine", BookmarkItemType.NONCONSUMABLE, "catalyst", 100);
		catalyst = new RecipeChainInput(catalyst.index(), catalyst.metadata().withMultiplier(64));
		var inputs = List.of(input(0, "machine", BookmarkItemType.RESULT, "machine", 1),
			input(1, "machine", BookmarkItemType.INGREDIENT, "ore", 1), catalyst,
			input(3, "catalyst", BookmarkItemType.RESULT, "catalyst", 100));
		var details = RecipeChainMath.refresh(inputs, Set.of());
		assertFalse(details.suppliers().containsKey(2));
		var tree = new RecipeTreeLayout(inputs, details);
		assertTrue(tree.nodes().stream().anyMatch(node -> node.slots().stream().anyMatch(slot -> slot.index() == 2)));
		assertFalse(tree.nodes().stream().anyMatch(node -> node.parent() != null && node.incomingSlot() == 2));
		assertEquals(100, catalyst.metadata().amount(64));
	}

	@Test
	void collapseIsLocalToOneOccurrenceAndExpandRestoresItsInputs() {
		List<RecipeChainInput> inputs = List.of(input(0, "source", BookmarkItemType.RESULT, "plate", 1),
			input(1, "source", BookmarkItemType.INGREDIENT, "ore", 1),
			input(2, "left", BookmarkItemType.RESULT, "left", 1), input(3, "left", BookmarkItemType.INGREDIENT, "plate", 1),
			input(4, "right", BookmarkItemType.RESULT, "right", 1), input(5, "right", BookmarkItemType.INGREDIENT, "plate", 1));
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var copies = tree.nodes().stream().filter(node -> node.recipe() && node.target().index() == 0).toList();
		int before = tree.nodes().size();
		assertTrue(tree.toggle(copies.getFirst()));
		assertEquals(before - 1, tree.nodes().size());
		assertTrue(copies.getLast().expanded());
		var restored = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var restoredSelection = restored.restoreExpansion(tree.captureExpansion(copies.getLast())).orElseThrow();
		var restoredCopies = restored.nodes().stream().filter(node -> node.recipe() && node.target().index() == 0).toList();
		assertSame(restoredCopies.getLast(), restoredSelection);
		assertFalse(restoredCopies.getFirst().expanded());
		assertTrue(restoredCopies.getLast().expanded());
		assertTrue(tree.toggle(copies.getFirst()));
		assertEquals(before, tree.nodes().size());
		assertNoOverlap(tree);
	}

	@Test
	void cycleUsesCalculatedCutAndEndsInAMaterialLeaf() {
		var inputs = List.of(input(0, "a", BookmarkItemType.RESULT, "a", 1), input(1, "a", BookmarkItemType.INGREDIENT, "b", 1),
			input(2, "b", BookmarkItemType.RESULT, "b", 1), input(3, "b", BookmarkItemType.INGREDIENT, "a", 1));
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		assertEquals(3, tree.nodes().size());
		assertEquals(2, tree.nodes().stream().filter(RecipeTreeLayout.Node::recipe).count());
		assertFalse(tree.nodes().getLast().recipe());
		assertNoOverlap(tree);
	}

	@Test
	void independentRecipesAndManySlotRowsStaySeparate() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (int recipe = 0; recipe < 30; recipe++) {
			String name = "r" + recipe;
			inputs.add(input(inputs.size(), name, BookmarkItemType.RESULT, name, 1));
			for (int slot = 0; slot < 20; slot++) {
				inputs.add(input(inputs.size(), name, BookmarkItemType.INGREDIENT, "ore", 1));
			}
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		assertEquals(30, tree.nodes().stream().filter(node -> node.parent() == null).count());
		assertEquals(630, tree.nodes().size());
		assertNoOverlap(tree);
	}

	@Test
	void deepChainStartsPartiallyExpandedAndCanContinue() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			inputs.add(input(inputs.size(), "r" + i, BookmarkItemType.RESULT, "p" + i, 1));
			inputs.add(input(inputs.size(), "r" + i, BookmarkItemType.INGREDIENT, "p" + (i + 1), 1));
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var initiallyVisible = List.copyOf(tree.nodes());
		tree.search(input -> input.index() == 49);
		assertEquals(initiallyVisible, tree.nodes());
		assertTrue(tree.matchesSearch(initiallyVisible.getLast()));
		assertFalse(tree.matchesSearch(initiallyVisible.getFirst()));
		assertEquals(3, tree.nodes().size());
		assertTrue(tree.toggle(tree.nodes().getLast()));
		assertEquals(4, tree.nodes().size());
		assertNoOverlap(tree);
	}

	@Test
	void editorCanSelectHiddenRecipeWithoutExpandingTheTree() {
		var inputs = List.of(input(0, "root", BookmarkItemType.RESULT, "root", 1), input(1, "root", BookmarkItemType.INGREDIENT, "a", 1),
			input(2, "a", BookmarkItemType.RESULT, "a", 1), input(3, "a", BookmarkItemType.INGREDIENT, "raw", 1));
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		assertTrue(tree.toggle(tree.nodes().getFirst()));
		assertEquals(2, tree.recipeNode(id("a")).orElseThrow().slots().size());
		assertEquals(1, tree.nodes().size());
		assertTrue(tree.recipeNode(id("missing")).isEmpty());
	}

	@Test
	void emptyGroupProducesAnEmptyView() {
		var tree = new RecipeTreeLayout(List.of(), RecipeChainMath.refresh(List.of(), Set.of()));
		assertTrue(tree.nodes().isEmpty());
		assertEquals(0, tree.width());
	}

	@Test
	void oversizedBranchStaysCollapsedWithoutDroppingSavedSlots() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		inputs.add(input(0, "large", BookmarkItemType.RESULT, "large", 1));
		for (int i = 1; i <= 4100; i++) {
			inputs.add(input(i, "large", BookmarkItemType.INGREDIENT, "ore", 1));
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		assertEquals(1, tree.nodes().size());
		assertEquals(inputs.size(), tree.nodes().getFirst().slots().size());
		assertTrue(tree.nodes().getFirst().expandable());
		assertFalse(tree.toggle(tree.nodes().getFirst()));
		assertEquals(1, tree.nodes().size());
	}

	@Test
	void staggeredBranchesReuseSpaceAtDifferentDepths() {
		List<RecipeChainInput> inputs = new ArrayList<>(List.of(
			input(0, "root", BookmarkItemType.RESULT, "root", 1),
			input(1, "root", BookmarkItemType.INGREDIENT, "a", 1),
			input(2, "root", BookmarkItemType.INGREDIENT, "b", 1),
			input(3, "a", BookmarkItemType.RESULT, "a", 1),
			input(4, "a", BookmarkItemType.INGREDIENT, "deep", 1),
			input(5, "deep", BookmarkItemType.RESULT, "deep", 1),
			input(6, "b", BookmarkItemType.RESULT, "b", 1)));
		for (String recipe : List.of("deep", "b")) {
			for (int i = 0; i < 12; i++) {
				inputs.add(input(inputs.size(), recipe, BookmarkItemType.INGREDIENT, "raw" + i, 1));
			}
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var deep = tree.nodes().stream().filter(node -> node.target().index() == 5).findFirst().orElseThrow();
		assertTrue(tree.toggle(deep));
		assertEquals(28, tree.nodes().size());
		var leaves = tree.nodes().stream().filter(node -> node.parent() == deep).toList();
		double pitch = leaves.get(1).x() - leaves.getFirst().x();
		assertTrue(tree.width() <= 13 * pitch, "Different-depth fans must not reserve two full subtree rectangles");
		assertNoOverlap(tree);
		assertOrderedConnectors(tree);
		double expandedWidth = tree.width();
		assertTrue(tree.toggle(deep));
		assertNoOverlap(tree);
		assertTrue(tree.toggle(deep));
		assertEquals(expandedWidth, tree.width());
		assertOrderedConnectors(tree);
	}

	@Test
	void nodeDimensionsDoNotDependOnRecipeSlotCountOrExpansion() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		inputs.add(input(0, "large", BookmarkItemType.RESULT, "large", 1));
		for (int i = 1; i <= 50; i++) {
			inputs.add(input(i, "large", BookmarkItemType.INGREDIENT, "ore", 1));
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var root = tree.nodes().getFirst();
		assertEquals(51, root.slots().size());
		assertTrue(RecipeTreeLayout.WIDTH <= 40);
		assertEquals(RecipeTreeLayout.WIDTH, root.height());
		var leaf = tree.nodes().getLast();
		assertEquals(root.height(), leaf.height());
		assertFalse(tree.toggle(leaf));
		assertTrue(tree.toggle(root));
		assertEquals(RecipeTreeLayout.WIDTH, root.height());
	}

	@Test
	void singleSupplierChainStaysInOneColumn() {
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			inputs.add(input(inputs.size(), "r" + i, BookmarkItemType.RESULT, "p" + i, 1));
			inputs.add(input(inputs.size(), "r" + i, BookmarkItemType.INGREDIENT, "p" + (i + 1), 1));
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		while (tree.nodes().getLast().expandable()) { assertTrue(tree.toggle(tree.nodes().getLast())); }
		assertEquals(101, tree.nodes().size());
		assertEquals(RecipeTreeLayout.WIDTH, tree.width());
		assertTrue(tree.nodes().stream().allMatch(node -> node.x() == 0));
		assertNoOverlap(tree);
	}

	@Test
	void wholeNodeIsHitTargetAndRemovedChildrenCannotBeHit() {
		var inputs = List.of(input(0, "root", BookmarkItemType.RESULT, "root", 1),
			input(1, "root", BookmarkItemType.INGREDIENT, "raw", 1));
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var root = tree.nodes().getFirst();
		for (double x : new double[] {0, RecipeTreeLayout.WIDTH / 2.0, RecipeTreeLayout.WIDTH - 0.1}) {
			for (double y : new double[] {0, root.height() / 2.0, root.height() - 0.1}) {
				assertSame(root, tree.nodeAt(root.x() + x, root.y() + y).orElseThrow());
			}
		}
		assertTrue(tree.nodeAt(root.x() - 1, root.y()).isEmpty());
		assertTrue(tree.nodeAt(root.x() + RecipeTreeLayout.WIDTH, root.y()).isEmpty());
		var child = tree.nodes().getLast();
		assertTrue(tree.toggle(root));
		assertTrue(tree.nodeAt(child.x() + 5, child.y() + 5).isEmpty());
		assertTrue(tree.toggle(root));
		assertTrue(tree.nodeAt(child.x() + 5, child.y() + 5).isPresent());
	}

	@Test
	void unevenForestKeepsNodesAndSiblingConnectorsSeparate() {
		Random random = new Random(314159);
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			inputs.add(input(inputs.size(), "r" + i, BookmarkItemType.RESULT, "p" + i, 1));
			inputs.add(input(inputs.size(), "r" + i, BookmarkItemType.INGREDIENT, "raw", 1));
			if (i >= 3) {
				inputs.add(input(inputs.size(), "r" + random.nextInt(i), BookmarkItemType.INGREDIENT, "p" + i, 1));
			}
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		while (true) {
			var collapsed = tree.nodes().stream().filter(node -> node.expandable() && !node.expanded()).findFirst();
			if (collapsed.isEmpty()) {
				break;
			}
			assertTrue(tree.toggle(collapsed.get()));
		}
		assertEquals(240, tree.nodes().size());
		assertNoOverlap(tree);
		assertOrderedConnectors(tree);
		for (var root : tree.nodes().stream().filter(node -> node.parent() == null).toList()) {
			assertTrue(tree.toggle(root));
			assertNoOverlap(tree);
			assertOrderedConnectors(tree);
		}
	}

	private static void assertOrderedConnectors(RecipeTreeLayout tree) {
		for (var node : tree.nodes()) {
			var children = tree.nodes().stream().filter(child -> child.parent() == node).toList();
			if (children.isEmpty()) {
				continue;
			}
			assertEquals(node.x(), children.getFirst().x());
			for (var other : tree.nodes()) {
				if (other.y() != node.y() || other.x() <= node.x() || !other.expanded()) {
					continue;
				}
				assertTrue(children.getLast().x() < other.x(), "Sibling connector spans must not cross");
			}
		}
	}

	@Test
	void reopeningParentPreservesMixedChildStatesAndNodeIdentity() {
		var inputs = List.of(
			input(0, "root", BookmarkItemType.RESULT, "root", 1),
			input(1, "root", BookmarkItemType.INGREDIENT, "a", 1),
			input(2, "root", BookmarkItemType.INGREDIENT, "b", 1),
			input(3, "a", BookmarkItemType.RESULT, "a", 1),
			input(4, "a", BookmarkItemType.INGREDIENT, "raw_a", 1),
			input(5, "b", BookmarkItemType.RESULT, "b", 1),
			input(6, "b", BookmarkItemType.INGREDIENT, "raw_b", 1));
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var root = tree.nodes().getFirst();
		var a = tree.nodes().stream().filter(node -> node.target().index() == 3).findFirst().orElseThrow();
		var b = tree.nodes().stream().filter(node -> node.target().index() == 5).findFirst().orElseThrow();
		assertTrue(tree.toggle(a));
		var before = List.copyOf(tree.nodes());
		double width = tree.width(), height = tree.height();
		for (int i = 0; i < 3; i++) {
			assertTrue(tree.toggle(root));
			assertEquals(List.of(root), tree.nodes());
			assertFalse(a.expanded());
			assertTrue(b.expanded());
			assertTrue(tree.toggle(root));
			assertEquals(before, tree.nodes());
			assertEquals(width, tree.width());
			assertEquals(height, tree.height());
			assertFalse(a.expanded());
			assertTrue(b.expanded());
			assertNoOverlap(tree);
		}
	}

	@Test
	void refreshPreservesHiddenDescendantExpansion() {
		var inputs = List.of(
			input(0, "root", BookmarkItemType.RESULT, "root", 1),
			input(1, "root", BookmarkItemType.INGREDIENT, "a", 1),
			input(2, "root", BookmarkItemType.INGREDIENT, "b", 1),
			input(3, "a", BookmarkItemType.RESULT, "a", 1),
			input(4, "a", BookmarkItemType.INGREDIENT, "raw_a", 1),
			input(5, "b", BookmarkItemType.RESULT, "b", 1),
			input(6, "b", BookmarkItemType.INGREDIENT, "raw_b", 1));
		var details = RecipeChainMath.refresh(inputs, Set.of());
		var previous = new RecipeTreeLayout(inputs, details);
		var a = previous.nodes().stream().filter(node -> node.target().index() == 3).findFirst().orElseThrow();
		assertTrue(previous.toggle(a));
		assertTrue(previous.toggle(previous.nodes().getFirst()));
		var refreshed = new RecipeTreeLayout(inputs, details);
		refreshed.restoreExpansion(previous.captureExpansion(null));
		assertEquals(1, refreshed.nodes().size());
		assertTrue(refreshed.toggle(refreshed.nodes().getFirst()));
		assertFalse(refreshed.nodes().stream().filter(node -> node.target().index() == 3).findFirst().orElseThrow().expanded());
		assertTrue(refreshed.nodes().stream().filter(node -> node.target().index() == 5).findFirst().orElseThrow().expanded());
		assertNoOverlap(refreshed);
	}

	@Test
	void reopeningCachedDescendantsPreservesTheirStateWithinAllocationBudget() {
		List<RecipeChainInput> inputs = new ArrayList<>(List.of(
			input(0, "a", BookmarkItemType.RESULT, "a", 1),
			input(1, "a", BookmarkItemType.INGREDIENT, "deep", 1),
			input(2, "deep", BookmarkItemType.RESULT, "deep", 1),
			input(3, "b", BookmarkItemType.RESULT, "b", 1)));
		for (String recipe : List.of("deep", "b")) {
			for (int i = 0; i < 2000; i++) {
				inputs.add(input(inputs.size(), recipe, BookmarkItemType.INGREDIENT, "raw", 1));
			}
		}
		var tree = new RecipeTreeLayout(inputs, RecipeChainMath.refresh(inputs, Set.of()));
		var a = tree.nodes().stream().filter(node -> node.target().index() == 0).findFirst().orElseThrow();
		var b = tree.nodes().stream().filter(node -> node.target().index() == 3).findFirst().orElseThrow();
		var deep = tree.nodes().stream().filter(node -> node.target().index() == 2).findFirst().orElseThrow();
		assertTrue(tree.toggle(b));
		assertTrue(deep.expanded());
		var before = List.copyOf(tree.nodes());
		assertTrue(tree.toggle(a));
		assertTrue(tree.toggle(b));
		assertTrue(tree.toggle(a));
		assertTrue(a.expanded());
		assertTrue(deep.expanded());
		assertTrue(tree.nodes().size() <= 4096);
		assertTrue(tree.toggle(b));
		assertEquals(before, tree.nodes());
		assertTrue(deep.expanded());
	}

	private static void assertNoOverlap(RecipeTreeLayout tree) {
		for (int i = 0; i < tree.nodes().size(); i++) {
			var a = tree.nodes().get(i);
			if (a.parent() != null) {
				assertTrue(a.y() > a.parent().y() + a.parent().height());
			}
			for (int j = i + 1; j < tree.nodes().size(); j++) {
				var b = tree.nodes().get(j);
				assertFalse(a.x() < b.x() + RecipeTreeLayout.WIDTH && a.x() + RecipeTreeLayout.WIDTH > b.x() &&
					a.y() < b.y() + b.height() && a.y() + a.height() > b.y());
			}
		}
	}

	private static RecipeChainInput input(int index, String recipe, BookmarkItemType type, String item, long amount) {
		return new RecipeChainInput(index, new BookmarkItemMetadata(1, type, 1, amount, BookmarkItemMetadata.CHANCE_FULL,
			id("processing"), id(recipe), Set.of(new BookmarkIngredientKey("test:item", item))));
	}

	private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("test", value); }
}
