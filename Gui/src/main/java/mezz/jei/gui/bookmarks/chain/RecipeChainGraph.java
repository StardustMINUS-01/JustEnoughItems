package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The immutable, lookup-oriented part of a recipe-chain calculation.
 *
 * Relation lookup happens many times while calculating one chain. Keeping it
 * here makes those lookups proportional to a recipe's own slots instead of to
 * every bookmark in the group.
 *
 * Logic ported from JEI 1.21.1 (StardustMINUS-01/JustEnoughItems, branch 1.21.1).
 * 1.20.1 adaptation: JEI 1.20.1's VanillaTypes.ITEM_STACK uid is the short
 * "item_stack" form (1.21.1 uses "minecraft:item_stack"), so the relaxed
 * producer index keys on the stable item identity instead of one hard-coded
 * type annotation. {@link BookmarkIngredientKey#isItemKey} and
 * {@link BookmarkIngredientKey#itemBaseId} cover both forms plus the
 * legacy/unknown keys produced by favorites.json round-trips.
 */
public final class RecipeChainGraph {
	private final Map<ResourceLocation, List<RecipeChainInput>> ingredientsByRecipe;
	private final Map<ResourceLocation, List<RecipeChainInput>> resultsByRecipe;
	private final Map<ResourceLocation, List<RecipeChainInput>> itemsByRecipe;
	private final Map<Set<BookmarkIngredientKey>, List<RecipeChainInput>> resultsByPermutations;
	private final Map<BookmarkIngredientKey, List<RecipeChainInput>> resultsByKey;
	/**
	 * Result inputs indexed by stable item identity (namespace:path), so recipe
	 * ingredients link to their producers even when the two sides carry different
	 * type annotations ("item_stack" vs legacy/unknown) or different snapshot
	 * counts/NBT (recipe output Count:8b vs input Count:1b). Exact keys take
	 * precedence; {@link #findBest} still gates candidates with isSatisfiedBy.
	 */
	private final Map<String, List<RecipeChainInput>> resultsByItemBaseId;
	private final Map<RecipeChainInput, Integer> resultOrder;

	private RecipeChainGraph(
		Map<ResourceLocation, List<RecipeChainInput>> ingredientsByRecipe,
		Map<ResourceLocation, List<RecipeChainInput>> resultsByRecipe,
		Map<ResourceLocation, List<RecipeChainInput>> itemsByRecipe,
		Map<Set<BookmarkIngredientKey>, List<RecipeChainInput>> resultsByPermutations,
		Map<BookmarkIngredientKey, List<RecipeChainInput>> resultsByKey,
		Map<String, List<RecipeChainInput>> resultsByItemBaseId,
		Map<RecipeChainInput, Integer> resultOrder
	) {
		this.ingredientsByRecipe = ingredientsByRecipe;
		this.resultsByRecipe = resultsByRecipe;
		this.itemsByRecipe = itemsByRecipe;
		this.resultsByPermutations = resultsByPermutations;
		this.resultsByKey = resultsByKey;
		this.resultsByItemBaseId = resultsByItemBaseId;
		this.resultOrder = resultOrder;
	}

	public static RecipeChainGraph create(List<RecipeChainInput> inputs) {
		Map<ResourceLocation, List<RecipeChainInput>> ingredientsByRecipe = new LinkedHashMap<>();
		Map<ResourceLocation, List<RecipeChainInput>> resultsByRecipe = new LinkedHashMap<>();
		Map<ResourceLocation, List<RecipeChainInput>> itemsByRecipe = new LinkedHashMap<>();
		Map<Set<BookmarkIngredientKey>, List<RecipeChainInput>> resultsByPermutations = new HashMap<>();
		Map<BookmarkIngredientKey, List<RecipeChainInput>> resultsByKey = new HashMap<>();
		Map<String, List<RecipeChainInput>> resultsByItemBaseId = new HashMap<>();
		Map<RecipeChainInput, Integer> resultOrder = new HashMap<>();

		for (RecipeChainInput input : inputs) {
			BookmarkItemMetadata metadata = input.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			BookmarkItemType type = metadata.type();
			if (recipeUid == null || !type.isGraphMember()) {
				continue;
			}
			itemsByRecipe.computeIfAbsent(recipeUid, ignored -> new ArrayList<>()).add(input);
			if (type.isGraphInput()) {
				ingredientsByRecipe.computeIfAbsent(recipeUid, ignored -> new ArrayList<>()).add(input);
				continue;
			}

			resultOrder.put(input, resultOrder.size());
			resultsByRecipe.computeIfAbsent(recipeUid, ignored -> new ArrayList<>()).add(input);
			Set<BookmarkIngredientKey> permutations = metadata.permutations();
			resultsByPermutations.computeIfAbsent(permutations, ignored -> new ArrayList<>()).add(input);
			for (BookmarkIngredientKey key : permutations) {
				resultsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(input);
				if (BookmarkIngredientKey.isItemKey(key)) {
					resultsByItemBaseId.computeIfAbsent(key.itemBaseId(), ignored -> new ArrayList<>()).add(input);
				}
			}
		}

		return new RecipeChainGraph(
			freeze(ingredientsByRecipe),
			freeze(resultsByRecipe),
			freeze(itemsByRecipe),
			freeze(resultsByPermutations),
			freeze(resultsByKey),
			freeze(resultsByItemBaseId),
			Map.copyOf(resultOrder)
		);
	}

	public List<RecipeChainInput> ingredientsFor(ResourceLocation recipeUid) {
		return ingredientsByRecipe.getOrDefault(recipeUid, List.of());
	}

	public List<RecipeChainInput> resultsFor(ResourceLocation recipeUid) {
		return resultsByRecipe.getOrDefault(recipeUid, List.of());
	}

	public List<RecipeChainInput> itemsFor(ResourceLocation recipeUid) {
		return itemsByRecipe.getOrDefault(recipeUid, List.of());
	}

	public Optional<RecipeChainInput> findPreferredResult(RecipeChainInput ingredient, Set<ResourceLocation> visited) {
		BookmarkItemMetadata ingredientMetadata = ingredient.metadata();
		if (ingredientMetadata.emptyFactor()) {
			return Optional.empty();
		}

		RecipeChainInput exact = findBest(
			resultsByPermutations.getOrDefault(ingredientMetadata.permutations(), List.of()),
			ingredientMetadata,
			visited
		);
		if (exact != null) {
			return Optional.of(exact);
		}

		LinkedHashSet<RecipeChainInput> candidates = new LinkedHashSet<>();
		for (BookmarkIngredientKey key : ingredientMetadata.permutations()) {
			candidates.addAll(resultsByKey.getOrDefault(key, List.of()));
			if (BookmarkIngredientKey.isItemKey(key)) {
				candidates.addAll(resultsByItemBaseId.getOrDefault(key.itemBaseId(), List.of()));
			}
		}
		RecipeChainInput fallback = findBest(order(candidates), ingredientMetadata, visited);
		return Optional.ofNullable(fallback);
	}

	private RecipeChainInput findBest(
		List<RecipeChainInput> candidates,
		BookmarkItemMetadata ingredientMetadata,
		Set<ResourceLocation> visited
	) {
		RecipeChainInput best = null;
		for (RecipeChainInput result : candidates) {
			BookmarkItemMetadata resultMetadata = result.metadata();
			ResourceLocation recipeUid = resultMetadata.recipeUid();
			if (
				resultMetadata.emptyFactor() ||
				recipeUid == null ||
				visited.contains(recipeUid) ||
				!ingredientMetadata.isSatisfiedBy(resultMetadata)
			) {
				continue;
			}
			if (best == null || resultMetadata.amount(1) > best.metadata().amount(1)) {
				best = result;
			}
		}
		return best;
	}

	private List<RecipeChainInput> order(Set<RecipeChainInput> candidates) {
		return candidates.stream()
			.sorted(Comparator.comparingInt(resultOrder::get))
			.toList();
	}

	private static <K> Map<K, List<RecipeChainInput>> freeze(Map<K, List<RecipeChainInput>> map) {
		Map<K, List<RecipeChainInput>> frozen = new LinkedHashMap<>();
		for (Map.Entry<K, List<RecipeChainInput>> entry : map.entrySet()) {
			frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(frozen);
	}
}
