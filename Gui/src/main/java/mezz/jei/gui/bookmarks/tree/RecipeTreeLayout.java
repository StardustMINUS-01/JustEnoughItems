package mezz.jei.gui.bookmarks.tree;

import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;
import mezz.jei.common.util.SaturatedMath;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** A view-local projection of suppliers and demand allocation from the existing chain calculation. */
public final class RecipeTreeLayout {
	public static final int WIDTH = 32;
	private static final int GAP = 12;
	private static final int LEVEL_STEP = 48;
	private static final int MAX_NODES = 4096;
	private final Map<Integer, RecipeChainInput> inputs = new LinkedHashMap<>();
	private final Map<ResourceLocation, List<RecipeChainInput>> recipes = new LinkedHashMap<>();
	private final Map<Integer, Integer> suppliers;
	private final List<Node> roots = new ArrayList<>();
	private List<Node> visible = List.of();
	private final Set<Integer> searchMatches = new HashSet<>();
	private final Set<ResourceLocation> searchBranches = new HashSet<>();
	private boolean remainingView;
	private int allocatedNodes;
	private double width;
	private double height;

	public RecipeTreeLayout(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		this.suppliers = details.suppliers();
		for (RecipeChainInput input : inputs) {
			this.inputs.put(input.index(), input);
			var metadata = input.metadata();
			if (metadata.recipeUid() != null && metadata.type().isRecipeAssociated()) {
				recipes.computeIfAbsent(metadata.recipeUid(), ignored -> new ArrayList<>()).add(input);
			}
		}
		recipes.replaceAll((recipe, entries) -> {
			List<RecipeChainInput> ordered = new ArrayList<>(entries.size());
			entries.stream().filter(input -> input.metadata().type().isGraphOutput()).forEach(ordered::add);
			entries.stream().filter(input -> !input.metadata().type().isGraphOutput() && !input.metadata().type().isNonConsumable()).forEach(ordered::add);
			entries.stream().filter(input -> input.metadata().type().isNonConsumable()).forEach(ordered::add);
			return List.copyOf(ordered);
		});
		for (var entry : recipes.entrySet()) {
			if (details.outputRecipes().contains(entry.getKey())) {
				entry.getValue().stream().filter(input -> input.metadata().type().isGraphOutput()).findFirst()
					.ifPresent(output -> roots.add(new Node(null, output, output.index(), true)));
			}
		}
		allocatedNodes = roots.size();
		relayout();
		// Start small even when a compact dependency graph expands into exponentially many paths.
		for (int depth = 0; depth < 2; depth++) {
			for (Node node : visible) {
				if (node.depth == depth && node.expandable()) {
					expand(node);
				}
			}
			relayout();
		}
	}

	public List<Node> nodes() { return visible; }
	public int getAllocatedNodeCount() { return allocatedNodes; }
	public double width() { return width; }
	public double height() { return height; }

	public void updateDemands(List<RecipeChainInput> inventory, Set<ResourceLocation> collapsedRecipes, boolean remainingView) {
		this.remainingView = remainingView;
		Map<Node, Map<Integer, Node>> children = new LinkedHashMap<>();
		Map<Integer, Node> rootIndexes = new LinkedHashMap<>();
		roots.forEach(node -> rootIndexes.put(node.incomingSlot, node));
		var pending = new ArrayDeque<>(roots);
		while (!pending.isEmpty()) {
			var node = pending.removeFirst();
			node.requested = 0;
			node.remaining = 0;
			Map<Integer, Node> indexes = new LinkedHashMap<>();
			node.children.forEach(child -> indexes.put(child.incomingSlot, child));
			children.put(node, indexes);
			pending.addAll(node.children);
		}
		var calculationInputs = new ArrayList<>(inputs.values().stream()
			.filter(input -> input.metadata().type().isRecipeAssociated()).toList());
		if (remainingView) {
			calculationInputs.addAll(inventory);
		}
		RecipeChainMath.visitDemands(calculationInputs, collapsedRecipes, demandVisitor(rootIndexes, children));
		relayout();
	}

	private RecipeChainMath.IDemandVisitor demandVisitor(Map<Integer, Node> indexes, Map<Node, Map<Integer, Node>> children) {
		return (input, requested, remaining) -> {
			Node node = indexes.get(input.index());
			if (node == null) {
				return null;
			}
			node.requested = SaturatedMath.add(node.requested, requested);
			node.remaining = SaturatedMath.add(node.remaining, remaining);
			return children.get(node).isEmpty() ? null : demandVisitor(children.get(node), children);
		};
	}

	public void search(Predicate<RecipeChainInput> matches) {
		searchMatches.clear();
		searchBranches.clear();
		inputs.values().stream().filter(matches).forEach(input -> searchMatches.add(input.index()));
		Map<ResourceLocation, Set<ResourceLocation>> consumers = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs.values()) {
			var recipe = input.metadata().recipeUid();
			if (recipe == null || !input.metadata().type().isGraphInput()) {
				continue;
			}
			var supply = inputs.get(suppliers.get(input.index()));
			boolean recipeSupply = supply != null && supply.metadata().type().isGraphOutput();
			if (searchMatches.contains(recipeSupply ? supply.index() : input.index())) {
				searchBranches.add(recipe);
			}
			if (recipeSupply) {
				consumers.computeIfAbsent(supply.metadata().recipeUid(), ignored -> new HashSet<>()).add(recipe);
			}
		}
		// Propagate over the compact supplier graph, not the potentially exponential display tree.
		var pending = new ArrayDeque<>(searchBranches);
		while (!pending.isEmpty()) {
			for (var consumer : consumers.getOrDefault(pending.removeFirst(), Set.of())) {
				if (searchBranches.add(consumer)) {
					pending.addLast(consumer);
				}
			}
		}
	}

	public boolean matchesSearch(Node node) {
		return searchMatches.contains(node.target.index()) ||
			!node.expanded() && node.recipe && searchBranches.contains(node.target.metadata().recipeUid());
	}

	public Optional<Node> recipeNode(ResourceLocation recipeUid) {
		return visible.stream().filter(node -> node.recipe && recipeUid.equals(node.target.metadata().recipeUid())).findFirst()
			.or(() -> recipes.getOrDefault(recipeUid, List.of()).stream().filter(input -> input.metadata().type().isGraphOutput()).findFirst()
				.map(output -> new Node(null, output, output.index(), true)));
	}

	public RecipeTreeViewState.Expansion captureExpansion(@Nullable Node selected) {
		record Pending(Node node, int parent, int child) {}
		var pending = new ArrayDeque<Pending>();
		for (int i = 0; i < Math.min(roots.size(), MAX_NODES); i++) {
			pending.add(new Pending(roots.get(i), -1, i));
		}
		List<RecipeTreeViewState.Branch> branches = new ArrayList<>();
		int selection = -1;
		while (!pending.isEmpty()) {
			var entry = pending.removeFirst();
			var node = entry.node();
			int index = branches.size();
			if (node == selected) {
				selection = index;
			}
			var metadata = node.target.metadata();
			branches.add(new RecipeTreeViewState.Branch(entry.parent(), entry.child(), metadata.recipeTypeUid(), metadata.recipeUid(), node.recipe, node.expanded));
			for (int i = 0; i < node.children.size() && branches.size() + pending.size() < MAX_NODES; i++) {
				Node child = node.children.get(i);
				if (child.recipe || child == selected) {
					pending.addLast(new Pending(child, index, i));
				}
			}
		}
		return new RecipeTreeViewState.Expansion(branches, selection);
	}

	public Optional<Node> restoreExpansion(RecipeTreeViewState.Expansion expansion) {
		List<Node> restored = new ArrayList<>();
		Map<ResourceLocation, Node> rootsByRecipe = new LinkedHashMap<>();
		roots.forEach(root -> rootsByRecipe.put(root.target.metadata().recipeUid(), root));
		for (var branch : expansion.branches()) {
			Node parent = branch.parent() < 0 ? null : restored.get(branch.parent());
			if (parent != null && parent.children.isEmpty()) {
				expand(parent);
			}
			List<Node> siblings = branch.parent() < 0 ? roots : parent == null ? List.of() : parent.children;
			Node node = branch.child() < siblings.size() ? siblings.get(branch.child()) : null;
			if (node == null || !matchesBranch(node, branch)) {
				node = branch.parent() < 0 ? rootsByRecipe.get(branch.recipeUid()) : null;
				if (node != null && !matchesBranch(node, branch)) {
					node = null;
				}
			}
			if (node != null && branch.expanded() && node.children.isEmpty()) {
				expand(node);
			}
			restored.add(node);
		}
		for (int i = 0; i < restored.size(); i++) {
			Node node = restored.get(i);
			if (node != null) {
				node.expanded = expansion.branches().get(i).expanded() && !node.children.isEmpty();
			}
		}
		relayout();
		return expansion.selected() < 0 ? Optional.empty() : Optional.ofNullable(restored.get(expansion.selected()));
	}

	private static boolean matchesBranch(Node node, RecipeTreeViewState.Branch branch) {
		return node.recipe == branch.recipe() && java.util.Objects.equals(node.target.metadata().recipeTypeUid(), branch.recipeType()) &&
			java.util.Objects.equals(node.target.metadata().recipeUid(), branch.recipeUid());
	}
	public Optional<Node> nodeAt(double x, double y) {
		return visible.stream().filter(node -> x >= node.x && x < node.x + WIDTH && y >= node.y && y < node.y + node.height()).findFirst();
	}

	public boolean toggle(Node node) {
		if (!node.expandable()) {
			return false;
		}
		if (node.expanded) {
			node.expanded = false;
		} else if (!expand(node)) {
			return false;
		}
		relayout();
		return true;
	}

	private boolean expand(Node node) {
		if (!node.children.isEmpty()) {
			node.expanded = true;
			return true;
		}
		List<RecipeChainInput> demands = node.slots.stream()
			.filter(input -> node.recipe && input.metadata().type().isGraphInput()).toList();
		// Folded branches still own their nodes; reopening them never spends this budget again.
		if (demands.size() > MAX_NODES - allocatedNodes) {
			return false;
		}
		List<Node> children = new ArrayList<>();
		for (RecipeChainInput demand : demands) {
			RecipeChainInput supply = inputs.get(suppliers.get(demand.index()));
			boolean recipe = supply != null && supply.metadata().type().isGraphOutput();
			for (Node ancestor = node; recipe && ancestor != null; ancestor = ancestor.parent) {
				if (supply.metadata().recipeUid().equals(ancestor.target.metadata().recipeUid())) {
					recipe = false;
				}
			}
			children.add(new Node(node, recipe ? supply : demand, demand.index(), recipe));
		}
		node.children = List.copyOf(children);
		allocatedNodes += children.size();
		node.expanded = true;
		return true;
	}

	private void relayout() {
		List<Node> nodes = new ArrayList<>(roots);
		int levels = 0;
		for (int i = 0; i < nodes.size(); i++) {
			Node node = nodes.get(i);
			nodes.addAll(node.visibleChildren());
			node.layoutChild = 0;
			levels = Math.max(levels, node.depth + 1);
		}
		width = 0;
		height = 0;
		double[] nextX = new double[levels];
		// Pack each depth independently. Align parents to their first child so sibling connectors stay ordered.
		// An explicit traversal stack keeps both work and storage linear, including very deep chains.
		ArrayDeque<Node> path = new ArrayDeque<>();
		for (Node root : roots) {
			root.x = nextX[0];
			path.push(root);
			while (!path.isEmpty()) {
				Node node = path.peek();
				if (node.layoutChild < node.visibleChildren().size()) {
					Node child = node.children.get(node.layoutChild++);
					child.x = Math.max(node.x, nextX[child.depth]);
					path.push(child);
				} else {
					node.y = node.depth * LEVEL_STEP;
					nextX[node.depth] = node.x + WIDTH + GAP;
					width = Math.max(width, node.x + WIDTH);
					height = Math.max(height, node.y + node.height());
					path.pop();
					if (!path.isEmpty() && path.peek().layoutChild == 1) {
						path.peek().x = node.x;
					}
				}
			}
		}
		visible = List.copyOf(nodes);
	}

	public final class Node {
		private final @Nullable Node parent;
		private final RecipeChainInput target;
		private final int incomingSlot;
		private final boolean recipe;
		private final List<RecipeChainInput> slots;
		private final int depth;
		private List<Node> children = List.of();
		private boolean expanded;
		private long requested, remaining;
		private int layoutChild;
		private double x, y;

		private Node(@Nullable Node parent, RecipeChainInput target, int incomingSlot, boolean recipe) {
			this.parent = parent;
			this.target = target;
			this.incomingSlot = incomingSlot;
			this.recipe = recipe;
			this.depth = parent == null ? 0 : parent.depth + 1;
			slots = recipe ? recipes.getOrDefault(target.metadata().recipeUid(), List.of()) : List.of(target);
		}

		public @Nullable Node parent() { return parent; }
		public RecipeChainInput target() { return target; }
		public int incomingSlot() { return incomingSlot; }
		public boolean recipe() { return recipe; }
		public List<RecipeChainInput> slots() { return slots; }
		private List<Node> visibleChildren() { return expanded() ? children : List.of(); }
		public long demandAmount() { return remainingView ? remaining : requested; }
		private boolean inventorySatisfied() { return remainingView && requested > 0 && remaining == 0; }
		public boolean expanded() { return expanded && !inventorySatisfied(); }
		public boolean expandable() { return recipe && !inventorySatisfied() && slots.stream().anyMatch(input -> input.metadata().type().isGraphInput()); }
		public double x() { return x; }
		public double y() { return y; }
		public int height() { return WIDTH; }
	}
}
