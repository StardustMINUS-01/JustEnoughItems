package mezz.jei.gui.recipes.filtering;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.recipes.IRecipeMaterialCacheInternal;
import mezz.jei.gui.search.IngredientSearchSnapshot;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import mezz.jei.gui.search.OrphanIngredientCorpus;

/** Immutable static rows for one registered category, shared across focused pages. */
final class CompactRecipeIndex<T> {
	final IRecipeCategory<T> category;
	final List<T> recipes;
	private final Reference2IntOpenHashMap<T> ids;
	private final RecipeMaterialIndex inputs, outputs;
	private volatile RecipePathIndex paths;

	CompactRecipeIndex(IRecipeCategory<T> category, List<T> registered, IRecipeMaterialCacheInternal materials, OrphanIngredientCorpus orphans, Runnable checkpoint) {
		this.category = category;
		ids = new Reference2IntOpenHashMap<>(registered.size(), 0.9f);
		ids.defaultReturnValue(-1);
		var rows = new ArrayList<T>();
		var known = new ArrayList<IRecipeMaterialCacheInternal.RegisteredMaterials>();
		for (int i = 0; i < registered.size(); i++) {
			T recipe = registered.get(i);
			if ((i & 255) == 0)
				checkpoint.run();
			var supplier = materials.getRegisteredRecipeMaterials(category, recipe);
			if (supplier != null && ids.putIfAbsent(recipe, rows.size()) < 0) {
				rows.add(recipe);
				known.add(supplier);
			}
		}
		if (ids.size() < registered.size() / 2)
			ids.trim();
		recipes = rows.size() == registered.size() ? registered : List.copyOf(rows);
		var resolver = new RecipeMaterialIndex.Resolver(orphans);
		var in = new RecipeMaterialIndex.Builder(rows.size(), resolver);
		var out = new RecipeMaterialIndex.Builder(rows.size(), resolver);
		for (int row = 0; row < known.size(); row++) {
			if ((row & 255) == 0)
				checkpoint.run();
			var supplier = known.get(row);
			in.add(row, supplier.getIngredients(RecipeIngredientRole.INPUT), supplier.wideSlots(RecipeIngredientRole.INPUT));
			out.add(row, supplier.getIngredients(RecipeIngredientRole.OUTPUT), supplier.wideSlots(RecipeIngredientRole.OUTPUT));
		}
		inputs = new RecipeMaterialIndex(in, checkpoint);
		outputs = new RecipeMaterialIndex(out, checkpoint);
	}

	int id(Object recipe) { return ids.getInt(recipe); }

	private record Term(RecipeSearchQuery.SearchTerm term, long[] hits, int count) {}

	boolean canRunInline(RecipeSearchQuery query, IngredientSearchSnapshot snapshot, int candidates) {

		for (var branch : query.branches())
			for (var term : branch)
				if (paths == null && term.canMatchResourceId())
					return false;
		if (candidates > RecipeSearchTraversal.RECIPES_PER_BATCH && (inputs.hasOrphans() || outputs.hasOrphans()))
			return false;
		if (candidates <= 2048)
			return true;
		for (var branch : query.branches()) {
			boolean selective = false;
			for (var term : branch) {
				if (term.excluded() || (term.scope() == RecipeSearchQuery.Scope.RECIPE || term.canMatchResourceId()))
					continue;
				long[] words = snapshot.getMaterialBits(term.ingredientToken());
				if (words != null && count(words) <= 512)
					selective = true;
			}
			if (!selective)
				return false;
		}
		return true;
	}

	private static int count(long[] words) {
		int hits = 0;
		for (long word : words) {
			hits += Long.bitCount(word);
			if (hits > 512)
				break;
		}
		return hits;
	}

	BitSet match(BitSet candidates, RecipeSearchQuery residual, IngredientSearchSnapshot snapshot, Runnable checkpoint) {
		var handled = new BitSet();
		var unhandled = new BitSet();
		for (var branch : residual.branches()) {
			var terms = new ArrayList<Term>(branch.size());
			boolean requiresMaterial = false;
			for (var term : branch) {
				boolean material = term.scope() != RecipeSearchQuery.Scope.RECIPE;
				var words = material ? snapshot.getMaterialBits(term.ingredientToken()) : null;
				if (material && words == null)
					return null;
				terms.add(new Term(term, words, words == null || term.excluded() ? Integer.MAX_VALUE : count(words)));
				requiresMaterial |= material && !term.excluded() && !term.canMatchResourceId();
			}
			terms.sort(Comparator.comparingInt(Term::count));
			var remaining = (BitSet) candidates.clone();
			// An unhandled recipe has empty materials but can still match an id or a negated term.
			var withoutMaterials = requiresMaterial ? new BitSet() : (BitSet) candidates.clone();
			for (var entry : terms) {
				checkpoint.run();
				if (remaining.isEmpty() && withoutMaterials.isEmpty())
					break;
				var term = entry.term;
				var pathHits = new BitSet();
				if (term.canMatchResourceId()) {
					var relevant = (BitSet) remaining.clone();
					relevant.or(withoutMaterials);
					paths(checkpoint).addMatches(term, relevant, pathHits, checkpoint);
				}
				if (term.excluded())
					withoutMaterials.andNot(pathHits);
				else
					withoutMaterials.and(pathHits);
				boolean sparse = remaining.cardinality() >= 1024 && !term.excluded() && entry.count <= 512;
				if (term.scope() == RecipeSearchQuery.Scope.INPUT || term.scope() == RecipeSearchQuery.Scope.ALL)
					pathHits.or(inputs.matches(entry.hits, remaining, sparse, term.ingredientToken(), snapshot, checkpoint));
				if (term.scope() == RecipeSearchQuery.Scope.OUTPUT || term.scope() == RecipeSearchQuery.Scope.ALL)
					pathHits.or(outputs.matches(entry.hits, remaining, sparse, term.ingredientToken(), snapshot, checkpoint));
				if (term.excluded())
					remaining.andNot(pathHits);
				else
					remaining.and(pathHits);
			}
			handled.or(remaining);
			unhandled.or(withoutMaterials);
		}
		if (unhandled.isEmpty()) {
			return handled.isEmpty() ? handled : RecipeRowFilter.matching(handled, row -> category.isHandled(recipes.get(row)), checkpoint);
		}
		var dependent = (BitSet) handled.clone();
		dependent.xor(unhandled);
		if (!dependent.isEmpty()) {
			var accepting = RecipeRowFilter.matching(dependent, row -> category.isHandled(recipes.get(row)), checkpoint);
			handled.and(accepting);
			unhandled.andNot(accepting);
			handled.or(unhandled);
		}
		return handled;
	}

	RecipePathIndex paths(Runnable checkpoint) {
		var current = paths;
		if (current != null)
			return current;
		synchronized (this) {
			if (paths == null)
				paths = new RecipePathIndex(category, recipes, checkpoint);
			return paths;
		}
	}
}
