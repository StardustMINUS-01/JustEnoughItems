package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable recipe relation plan for one bookmark-group version.
 * Quantity propagation deliberately remains in {@link RecipeChainMath}; this
 * class owns supplier choice, cycle handling, and output classification.
 */
public final class RecipeChainPlan {
	private final RecipeChainGraph graph;
	private final Map<ResourceLocation, RecipeChainInput> outputTargets;
	private final Map<RecipeChainInput, RecipeChainInput> preferredResults;
	private final Map<ResourceLocation, Long> outputRecipes;

	private RecipeChainPlan(
		RecipeChainGraph graph,
		Map<ResourceLocation, RecipeChainInput> outputTargets,
		Map<RecipeChainInput, RecipeChainInput> preferredResults,
		Map<ResourceLocation, Long> outputRecipes
	) {
		this.graph = graph;
		this.outputTargets = Collections.unmodifiableMap(new LinkedHashMap<>(outputTargets));
		this.preferredResults = Collections.unmodifiableMap(new LinkedHashMap<>(preferredResults));
		this.outputRecipes = Collections.unmodifiableMap(new LinkedHashMap<>(outputRecipes));
	}

	public static RecipeChainPlan compile(List<RecipeChainInput> inputs, Set<ResourceLocation> collapsedRecipes) {
		RecipeChainGraph graph = RecipeChainGraph.create(inputs);
		Map<ResourceLocation, RecipeChainInput> outputTargets = new LinkedHashMap<>();
		Map<ResourceLocation, Long> requestedMultipliers = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> recipeOrder = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			BookmarkItemMetadata metadata = input.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (recipeUid == null || !metadata.type().isGraphMember()) {
				continue;
			}
			recipeOrder.putIfAbsent(recipeUid, recipeOrder.size());
			if (metadata.type().isGraphOutput()) {
				outputTargets.putIfAbsent(recipeUid, input);
				requestedMultipliers.merge(recipeUid, Math.max(0, metadata.multiplier()), Math::max);
			}
		}

		Map<RecipeChainInput, RecipeChainInput> preferredResults = new LinkedHashMap<>();
		for (ResourceLocation recipeUid : recipeOrder.keySet()) {
			for (RecipeChainInput ingredient : graph.ingredientsFor(recipeUid)) {
				graph.findPreferredResult(ingredient, Set.of()).ifPresent(result -> preferredResults.put(ingredient, result));
			}
		}
		breakCycles(graph, preferredResults, recipeOrder);

		Set<ResourceLocation> middleRecipes = new HashSet<>();
		for (RecipeChainInput result : preferredResults.values()) {
			ResourceLocation recipeUid = result.metadata().recipeUid();
			if (recipeUid != null) {
				middleRecipes.add(recipeUid);
			}
		}

		Map<ResourceLocation, Long> outputRecipes = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> entry : requestedMultipliers.entrySet()) {
			ResourceLocation recipeUid = entry.getKey();
			long multiplier = entry.getValue();
			boolean forcedOutput = multiplier > 1 || collapsedRecipes.contains(recipeUid);
			boolean middleRecipe = middleRecipes.contains(recipeUid);
			if (!middleRecipe || forcedOutput) {
				outputRecipes.put(recipeUid, forcedOutput && middleRecipe ? Math.max(0, multiplier - 1) : multiplier);
			}
		}

		return new RecipeChainPlan(graph, outputTargets, preferredResults, outputRecipes);
	}

	public RecipeChainGraph graph() {
		return graph;
	}

	public Map<ResourceLocation, RecipeChainInput> outputTargets() {
		return outputTargets;
	}

	public Map<RecipeChainInput, RecipeChainInput> preferredResults() {
		return preferredResults;
	}

	public Map<ResourceLocation, Long> outputRecipes() {
		return outputRecipes;
	}

	private static void breakCycles(
		RecipeChainGraph graph,
		Map<RecipeChainInput, RecipeChainInput> preferredResults,
		Map<ResourceLocation, Integer> recipeOrder
	) {
		for (Set<ResourceLocation> component : findStronglyConnectedComponents(graph, preferredResults, recipeOrder.keySet())) {
			if (!isCyclicComponent(component, graph, preferredResults)) {
				continue;
			}
			preferredResults.entrySet().removeIf(entry -> isInternalEdge(entry, component));
			List<ResourceLocation> orderedRecipes = component.stream()
				.sorted(Comparator.comparingInt(recipeOrder::get))
				.toList();
			Set<ResourceLocation> visited = new LinkedHashSet<>();
			for (ResourceLocation recipeUid : orderedRecipes) {
				retainAcyclicEdges(recipeUid, graph, component, preferredResults, visited);
			}
		}
	}

	private static boolean isInternalEdge(Map.Entry<RecipeChainInput, RecipeChainInput> entry, Set<ResourceLocation> component) {
		ResourceLocation consumer = entry.getKey().metadata().recipeUid();
		ResourceLocation producer = entry.getValue().metadata().recipeUid();
		return consumer != null && producer != null && component.contains(consumer) && component.contains(producer);
	}

	private static void retainAcyclicEdges(
		ResourceLocation recipeUid,
		RecipeChainGraph graph,
		Set<ResourceLocation> component,
		Map<RecipeChainInput, RecipeChainInput> preferredResults,
		Set<ResourceLocation> visited
	) {
		if (!visited.add(recipeUid)) {
			return;
		}
		for (RecipeChainInput ingredient : graph.ingredientsFor(recipeUid)) {
			RecipeChainInput preferred = findPotentialPreferredResult(ingredient, graph);
			if (preferred == null) {
				continue;
			}
			ResourceLocation preferredRecipe = preferred.metadata().recipeUid();
			if (preferredRecipe != null && component.contains(preferredRecipe) && !visited.contains(preferredRecipe)) {
				preferredResults.put(ingredient, preferred);
				retainAcyclicEdges(preferredRecipe, graph, component, preferredResults, visited);
			}
		}
	}

	private static boolean isCyclicComponent(
		Set<ResourceLocation> component,
		RecipeChainGraph graph,
		Map<RecipeChainInput, RecipeChainInput> preferredResults
	) {
		if (component.size() > 1) {
			return true;
		}
		ResourceLocation recipeUid = component.iterator().next();
		for (RecipeChainInput ingredient : graph.ingredientsFor(recipeUid)) {
			RecipeChainInput preferred = preferredResults.get(ingredient);
			if (preferred != null && recipeUid.equals(preferred.metadata().recipeUid())) {
				return true;
			}
		}
		return false;
	}

	private static List<Set<ResourceLocation>> findStronglyConnectedComponents(
		RecipeChainGraph graph,
		Map<RecipeChainInput, RecipeChainInput> preferredResults,
		Set<ResourceLocation> recipeUids
	) {
		Tarjan tarjan = new Tarjan(graph, preferredResults);
		for (ResourceLocation recipeUid : recipeUids) {
			tarjan.visit(recipeUid);
		}
		return tarjan.components;
	}

	private static RecipeChainInput findPotentialPreferredResult(RecipeChainInput ingredient, RecipeChainGraph graph) {
		return graph.findPreferredResult(ingredient, Set.of()).orElse(null);
	}

	private static final class Tarjan {
		private final RecipeChainGraph graph;
		private final Map<RecipeChainInput, RecipeChainInput> preferredResults;
		private final Map<ResourceLocation, Integer> indexByRecipe = new HashMap<>();
		private final Map<ResourceLocation, Integer> lowLinkByRecipe = new HashMap<>();
		private final List<ResourceLocation> stack = new ArrayList<>();
		private final Set<ResourceLocation> onStack = new HashSet<>();
		private final List<Set<ResourceLocation>> components = new ArrayList<>();
		private int nextIndex;

		private Tarjan(RecipeChainGraph graph, Map<RecipeChainInput, RecipeChainInput> preferredResults) {
			this.graph = graph;
			this.preferredResults = preferredResults;
		}

		private void visit(ResourceLocation recipeUid) {
			if (indexByRecipe.containsKey(recipeUid)) {
				return;
			}
			indexByRecipe.put(recipeUid, nextIndex);
			lowLinkByRecipe.put(recipeUid, nextIndex++);
			stack.add(recipeUid);
			onStack.add(recipeUid);

			for (RecipeChainInput ingredient : graph.ingredientsFor(recipeUid)) {
				RecipeChainInput preferred = preferredResults.get(ingredient);
				ResourceLocation nextRecipe = preferred == null ? null : preferred.metadata().recipeUid();
				if (nextRecipe == null) {
					continue;
				}
				if (!indexByRecipe.containsKey(nextRecipe)) {
					visit(nextRecipe);
					lowLinkByRecipe.compute(recipeUid, (ignored, lowLink) -> Math.min(lowLink, lowLinkByRecipe.get(nextRecipe)));
				} else if (onStack.contains(nextRecipe)) {
					lowLinkByRecipe.compute(recipeUid, (ignored, lowLink) -> Math.min(lowLink, indexByRecipe.get(nextRecipe)));
				}
			}

			if (!lowLinkByRecipe.get(recipeUid).equals(indexByRecipe.get(recipeUid))) {
				return;
			}
			Set<ResourceLocation> component = new LinkedHashSet<>();
			ResourceLocation member;
			do {
				member = stack.remove(stack.size() - 1);
				onStack.remove(member);
				component.add(member);
			} while (!recipeUid.equals(member));
			components.add(component);
		}
	}
}
