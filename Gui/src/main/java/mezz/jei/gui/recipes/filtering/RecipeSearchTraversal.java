package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.match.IngredientMatchInfo;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;
import java.util.IdentityHashMap;
import mezz.jei.gui.search.IngredientSearchSnapshot;
import mezz.jei.common.recipes.IRecipeMaterialCacheInternal;

final class RecipeSearchTraversal {
	static final int RECIPES_PER_BATCH = 64;
	private final IRecipeManager recipeManager;
	private final RecipeFilterMode mode;
	private final RecipeSearchQuery query;
	private final RecipeCategoryPreferences.State categoryPreferences = RecipeCategoryPreferences.get();
	private final Supplier<IRecipeSearchTextMatcher> matcherFactory;
	private final BiPredicate<ITypedIngredient<?>, String> ingredientMatcher;
	private final List<Batch<?>> batches = new ArrayList<>();
	private volatile AtomicReferenceArray<List<?>> results;
	private final RecipeSearchPage page;
	private final List<IFocusedRecipes<?>> source;
	private final Map<IRecipeCategory<?>, RecipeSearchQuery.Compiled> compiled;
	private final List<IFocusedRecipes<?>> preferenceSource;
	private final List<IFocusedRecipes<?>> immediate = new ArrayList<>();
	private int nextCategory;
	private long inlineDeadline;
	private PageRecipeIndex<?>.Selection unfinished;
	private final Set<Batch<?>> indexedBatches = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	private IRecipeCategory<?> unfinishedCategory;
	private final Set<IRecipeCategory<?>> exactCategories;
	private final Map<IRecipeCategory<?>, List<ITypedIngredient<?>>> catalysts;
	private Set<FocusedRecipe> preferred;
	private volatile boolean cancelled;
	private final AtomicInteger progress = new AtomicInteger();

	RecipeSearchTraversal(IRecipeManager recipeManager, RecipeSearchPage page, List<IFocusedRecipes<?>> source,
		RecipeFilterMode mode, RecipeSearchQuery query, Supplier<IRecipeSearchTextMatcher> matcherFactory,
		BiPredicate<ITypedIngredient<?>, String> ingredientMatcher, Set<FocusedRecipe> preferred, Map<IRecipeCategory<?>, List<ITypedIngredient<?>>> catalysts, Set<IRecipeCategory<?>> exactCategories, Map<IRecipeCategory<?>, RecipeSearchQuery.Compiled> compiled, List<IFocusedRecipes<?>> preferenceSource) {
		this.compiled = compiled;
		this.preferenceSource = preferenceSource;
		this.exactCategories = exactCategories;
		this.catalysts = catalysts;
		this.recipeManager = recipeManager;
		this.page = page;
		this.source = source;
		this.mode = mode;
		this.query = query;
		this.matcherFactory = matcherFactory;
		this.ingredientMatcher = ingredientMatcher;
		this.preferred = preferred;
	}

	private RecipeSearchQuery.Compiled compile(IRecipeCategory<?> category, IRecipeSearchTextMatcher matcher) {
		return compiled.computeIfAbsent(category, c -> query.compileForCategory(c,
			() -> catalysts.computeIfAbsent(c, key -> recipeManager.createRecipeCatalystLookup(key.getRecipeType()).get().toList()), matcher, ingredientMatcher));
	}

	boolean tryImmediate(long deadline) {
		inlineDeadline = deadline;
		if (mode.filtersPreference())
			return false;
		var matcher = matcherFactory.get();
		while (nextCategory < source.size() && System.nanoTime() < deadline) {
			var focused = source.get(nextCategory);
			var condition = compile(focused.getRecipeCategory(), matcher);
			if (condition.constant()) {
				if (condition.always() && !focused.getRecipes().isEmpty())
					immediate.add(focused);
			} else if (tryCompact(focused, condition.residual(), matcher)) {
				// This category was completed within the request.
			} else if (System.nanoTime() < deadline && unfinished == null && focused.getRecipes().size() <= 32 && condition.residual().getIngredientTokens().isEmpty()) {
				addImmediateMatches(focused, matcher);
			} else {
				return false;
			}
			nextCategory++;
		}
		return nextCategory == source.size();
	}

	private <T> void addImmediateMatches(IFocusedRecipes<T> focused, IRecipeSearchTextMatcher matcher) {
		var hits = match(new Batch<>(focused.getRecipeCategory(), focused.getRecipes()), Set.of(), matcher);
		if (!hits.isEmpty())
			immediate.add(new StaticFocusedRecipes<>(focused.getRecipeCategory(), hits));
	}

	Set<FocusedRecipe> preferred() {
		return preferred;
	}

	private <T> void addBatches(IFocusedRecipes<T> focused) {
		List<T> recipes = focused.getRecipes();
		for (int start = 0; start < recipes.size(); start += RECIPES_PER_BATCH)
			batches.add(new Batch<>(focused.getRecipeCategory(), recipes.subList(start, Math.min(start + RECIPES_PER_BATCH, recipes.size()))));
	}

	public void scan(RecipePreferenceRules rules, int parallelism) {
		try {
			if (mode.filtersPreference() && preferred == null) {
				preferred = resolvePreferences(rules, parallelism);
			}
			var matcher = matcherFactory.get();
			for (; nextCategory < source.size(); nextCategory++) {
				checkCancelled();
				var focused = source.get(nextCategory);
				var condition = compile(focused.getRecipeCategory(), matcher);
				if (condition.constant() && !condition.always())
					continue;
				if (condition.constant() && !mode.filtersPreference())
					addWholeCategory(focused);
				else if (!condition.constant() && recipeManager instanceof IRecipeMaterialCacheInternal && ingredientMatcher instanceof IngredientSearchSnapshot snapshot && snapshot.hasNativeIds())
					addIndexedBatch(focused);
				else
					addBatches(focused);
			}
			results = new AtomicReferenceArray<>(batches.size());
			runWorkers(parallelism, () -> {
				var localMatcher = matcherFactory.get();
				return index -> {
					var batch = batches.get(index);
					var hits = indexedBatches.contains(batch) ? compactResults(batch, compiled.get(batch.category()).residual(), localMatcher, true) : null;
					results.set(index, hits == null ? match(batch, preferred, localMatcher) : hits);
					progress.incrementAndGet();
				};
			});
		} catch (RuntimeException | Error e) {
			cancelled = true;
			throw e;
		}
	}

	private <T> boolean tryCompact(IFocusedRecipes<T> focused, RecipeSearchQuery residual, IRecipeSearchTextMatcher matcher) {
		var hits = compactResults(new Batch<>(focused.getRecipeCategory(), focused.getRecipes()), residual, matcher, false);
		if (hits == null)
			return false;
		if (!hits.isEmpty())
			immediate.add(new StaticFocusedRecipes<>(focused.getRecipeCategory(), hits));
		return true;
	}

	private <T> void addIndexedBatch(IFocusedRecipes<T> focused) {
		var category = focused.getRecipeCategory();
		var rows = focused.getRecipes();
		int skip = 0;
		if (rows.size() > 2048 && category != unfinishedCategory) {
			skip = RECIPES_PER_BATCH;
			var first = new Batch<>(category, rows, 0, skip);
			if (page.index(category, false) != null)
				indexedBatches.add(first);
			batches.add(first);
		}
		var batch = new Batch<>(category, rows, skip, -1);
		indexedBatches.add(batch);
		batches.add(batch);
	}

	private <T> List<T> compactResults(Batch<T> batch, RecipeSearchQuery residual, IRecipeSearchTextMatcher matcher, boolean worker) {
		if (!(recipeManager instanceof IRecipeMaterialCacheInternal materials) ||
			!(ingredientMatcher instanceof IngredientSearchSnapshot snapshot) || !snapshot.hasNativeIds())
			return null;
		var category = batch.category();
		boolean small = batch.recipes().size() <= RECIPES_PER_BATCH;
		boolean build = worker && (!small || materials.getRegisteredRecipeCount(category) <= 256);
		var index = page.index(category, build);
		boolean exact = exactCategories.contains(category);
		boolean useIndex = index != null && (worker || exact || index.canRunInline(residual, snapshot, batch.recipes().size()));
		if (!useIndex && !worker && small) {
			var hits = new ArrayList<T>();
			try {
				for (T recipe : batch.range()) {
					checkTimeBudget();
					var supplied = materials.getRegisteredRecipeMaterials(category, recipe);
					if (supplied == null || !category.isHandled(recipe))
						return null;
					if (residual.matchesCached(category, recipe, supplied, matcher, (ingredient, token) -> {
						checkTimeBudget();
						return snapshot.test(ingredient, token);
						}))
						hits.add(recipe);
				}
			} catch (TimeBudgetExceeded exhausted) {
				return null;
			}
			return List.copyOf(hits);
		}
		if (!useIndex)
			return null;
		@SuppressWarnings("unchecked")
		var selection = category == unfinishedCategory ? (PageRecipeIndex<T>.Selection) unfinished : null;
		if (selection == null) {
			try {
				selection = index.select(batch.recipes(), batch.skip, batch.limit, residual, snapshot, exact, worker ? this::checkCancelled : this::checkTimeBudget);
			} catch (TimeBudgetExceeded exhausted) {
				return null;
			}
		}
		if (selection == null)
			return null;
		List<T> hits;
		if (worker) {
			BiPredicate<List<ITypedIngredient<?>>, String> roles = (values, token) -> page.dynamicMaterials.matches(values, token, snapshot);
			hits = selection.finish(recipe -> residual.matchesDynamic(category, recipe, recipeManager, matcher, ingredientMatcher, roles),
				this::checkCancelled, partial -> {
					if (!mode.filtersPreference()) {
						batch.partial = partial;
						progress.incrementAndGet();
					}
				}, progress::incrementAndGet);
		} else {
			hits = selection.finishInline(recipe -> {
				if (System.nanoTime() >= inlineDeadline)
					return null;
				var supplied = materials.getCachedDynamicMaterials(category, recipe, residual.ingredientRoles());
				if (supplied == null || !category.isHandled(recipe))
					return null;
				return residual.matchesCached(category, recipe, supplied, matcher, ingredientMatcher);
			});
		}
		if (!worker && hits == null) {
			unfinishedCategory = category;
			unfinished = selection;
		}
		if (hits == null)
			return null;
		if (mode.filtersPreference())
			hits = hits.stream().filter(recipe -> passesPreference(category, recipe, preferred)).toList();
		return hits;
	}

	private <T> boolean passesPreference(IRecipeCategory<T> category, T recipe, Set<FocusedRecipe> preferred) {
		boolean selected = categoryPreferences.isPreferred(category.getRecipeType().getUid(), getFocusedRecipe(category, recipe), preferred);
		return selected == (mode == RecipeFilterMode.PREFERRED);
	}

	private <T> void addWholeCategory(IFocusedRecipes<T> focused) {
		if (!focused.getRecipes().isEmpty())
			batches.add(new Batch<>(focused.getRecipeCategory(), focused.getRecipes()));
	}

	private void runWorkers(int parallelism, Supplier<IntConsumer> workerFactory) {
		AtomicInteger next = new AtomicInteger();
		IntStream.range(0, Math.min(parallelism, batches.size())).parallel().forEach(worker -> {
			IntConsumer action = workerFactory.get();
			int index;
			while ((index = next.getAndIncrement()) < batches.size()) {
				checkCancelled();
				action.accept(index);
			}
		});
	}

	private Set<FocusedRecipe> resolvePreferences(RecipePreferenceRules rules, int parallelism) {
		if (rules.isEmpty()) {
			var identities = preferenceSource.stream().flatMap(this::getRecipeIdentities).distinct().limit(2).toList();
			return identities.size() == 1 ? Set.of(identities.getFirst()) : Set.of();
		}
		for (var focused : preferenceSource)
			addBatches(focused);
		AtomicReferenceArray<List<RecipePreferenceCandidate>> candidates = new AtomicReferenceArray<>(batches.size());
		runWorkers(parallelism, () -> index -> candidates.set(index, collectPreferences(batches.get(index))));
		checkCancelled();
		List<RecipePreferenceCandidate> all = new ArrayList<>();
		for (int i = 0; i < candidates.length(); i++)
			all.addAll(candidates.get(i));
		batches.clear();
		return Set.copyOf(rules.resolvePreferredRecipes(all));
	}

	private <T> Stream<FocusedRecipe> getRecipeIdentities(IFocusedRecipes<T> focused) {
		var category = focused.getRecipeCategory();
		return focused.getRecipes().stream().map(recipe -> {
				checkCancelled();
				return getFocusedRecipe(category, recipe);
			})
			.filter(Objects::nonNull);
	}

	private static <T> @Nullable FocusedRecipe getFocusedRecipe(IRecipeCategory<T> category, T recipe) {
		var id = category.getRegistryName(recipe);
		return id == null ? null : new FocusedRecipe(category.getRecipeType().getUid(), id);
	}

	private <T> List<RecipePreferenceCandidate> collectPreferences(Batch<T> batch) {
		List<RecipePreferenceCandidate> candidates = new ArrayList<>();
		for (T recipe : batch.recipes()) {
			checkCancelled();
			var focused = getFocusedRecipe(batch.category(), recipe);
			if (focused != null) {
				var materials = recipeManager.getRecipeIngredients(batch.category(), recipe, Set.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT));
				candidates.add(new RecipePreferenceCandidate(focused,
					createMatchInfo(materials.getIngredients(RecipeIngredientRole.INPUT)), createMatchInfo(materials.getIngredients(RecipeIngredientRole.OUTPUT))));
			}
		}
		return candidates;
	}

	private <T> List<T> match(Batch<T> batch, Set<FocusedRecipe> preferred, IRecipeSearchTextMatcher matcher) {
		var condition = compiled.get(batch.category());
		if (condition.always() && !mode.filtersPreference())
			return batch.range();
		List<T> matches = new ArrayList<>();
		for (T recipe : batch.range()) {
			checkCancelled();
			IRecipeCategory<T> category = batch.category();
			if (mode.filtersPreference() && !passesPreference(category, recipe, preferred))
				continue;
			if (condition.always() || condition.residual().matches(category, recipe, recipeManager, null, matcher, ingredientMatcher))
				matches.add(recipe);
		}
		return List.copyOf(matches);
	}

	private static List<IngredientMatchInfo> createMatchInfo(List<ITypedIngredient<?>> ingredients) {
		return ingredients.stream().map(IngredientMatchInfo::fromIngredient).flatMap(java.util.Optional::stream).toList();
	}

	public void cancel() {
		cancelled = true;
	}

	private static final class TimeBudgetExceeded extends RuntimeException {
		private static final TimeBudgetExceeded INSTANCE = new TimeBudgetExceeded();
		private TimeBudgetExceeded() { super(null, null, false, false); }
	}

	private void checkTimeBudget() {
		if (System.nanoTime() >= inlineDeadline)
			throw TimeBudgetExceeded.INSTANCE;
	}

	private void checkCancelled() {
		if (cancelled || Thread.currentThread().isInterrupted())
			throw new CancellationException();
	}

	int progress() { return progress.get(); }

	// Only the client advances this ordered prefix. Unfinished dynamic batches publish immutable snapshots.
	private final List<IFocusedRecipes<?>> completed = new ArrayList<>();
	private int publishedBatches;
	private IRecipeCategory<?> currentCategory;
	private List<?> currentRows = List.of();
	private ArrayList<Object> mergedRows;

	private void appendRows(List<?> rows) {
		if (rows.isEmpty())
			return;
		if (currentRows.isEmpty())
			currentRows = rows;
		else if (mergedRows != null)
			mergedRows.addAll(rows);
		else {
			var joined = PageRecipeIndex.joinRows(currentRows, rows);
			if (joined != null)
				currentRows = joined;
			else {
				mergedRows = new ArrayList<>(currentRows);
				mergedRows.addAll(rows);
			}
		}
	}

	private void addCurrentCategory(List<IFocusedRecipes<?>> target, List<?> partial) {
		if (currentRows.isEmpty() && partial.isEmpty())
			return;
		List<?> rows = mergedRows == null ? currentRows : mergedRows;
		if (!partial.isEmpty() && !rows.isEmpty()) {
			var combined = new ArrayList<Object>(rows);
			combined.addAll(partial);
			rows = List.copyOf(combined);
		} else if (!partial.isEmpty())
			rows = partial;
		else if (mergedRows != null)
			rows = List.copyOf(mergedRows);
		target.add(frozenMatches(currentCategory, rows));
	}

	public List<IFocusedRecipes<?>> getResults() {
		var published = results;
		List<?> partial = List.of();
		while (published != null && publishedBatches < published.length()) {
			var batch = batches.get(publishedBatches);
			if (currentCategory != batch.category()) {
				addCurrentCategory(completed, List.of());
				currentCategory = batch.category();
				currentRows = List.of();
				mergedRows = null;
			}
			var rows = published.get(publishedBatches);
			if (rows == null) {
				var snapshot = batch.partial;
				if (snapshot != null)
					partial = snapshot.get();
				break;
			}
			appendRows(rows);
			publishedBatches++;
		}
		var focused = new ArrayList<>(immediate);
		focused.addAll(completed);
		addCurrentCategory(focused, partial);
		focused.sort(java.util.Comparator.comparingInt(group -> page.order(group.getRecipeCategory())));
		return List.copyOf(focused);
	}

	@SuppressWarnings("unchecked")
	private static <T> IFocusedRecipes<T> frozenMatches(IRecipeCategory<T> category, List<?> recipes) {
		return new StaticFocusedRecipes<>(category, (List<T>) recipes);
	}

	private static final class Batch<T> {
		private final IRecipeCategory<T> category;
		private final List<T> recipes;
		private final int skip, limit;
		volatile Supplier<List<T>> partial;

		Batch(IRecipeCategory<T> category, List<T> recipes) { this(category, recipes, 0, -1); }
		Batch(IRecipeCategory<T> category, List<T> recipes, int skip, int limit) {
			this.category = category;
			this.recipes = recipes;
			this.skip = skip;
			this.limit = limit;
		}
		IRecipeCategory<T> category() { return category; }
		List<T> recipes() { return recipes; }
		List<T> range() {
			return skip == 0 && limit == -1 ? recipes : recipes.subList(skip, limit == -1 ? recipes.size() : skip + limit);
		}
	}
}
