package mezz.jei.gui.recipes.filtering;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.search.IngredientSearchSnapshot;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import mezz.jei.api.recipe.category.IRecipeCategory;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.ArrayDeque;
import java.util.Set;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.common.search.compact.CompactSearchStorageBuilder;
import mezz.jei.common.recipes.IRecipeMaterialCacheInternal;
import java.util.concurrent.Future;

public final class RecipeSearchSession {
	private final IRecipeManager recipeManager;
	private final ISearchStorageBuilderFactory searchStorageBuilderFactory;
	private @Nullable IngredientFilter ingredientSearch;
	private long ingredientSearchRevision;
	private @Nullable IngredientSearchSnapshot ingredientSnapshot;
	private record Wave(RecipeSearchTraversal traversal, Future<?> task) {}
	private final List<Wave> waves = new ArrayList<>();
	private @Nullable Capture capture;
	private @Nullable RecipePreferenceRules preferenceRules;
	private RecipeFilterMode mode = RecipeFilterMode.ALL;
	private RecipeSearchQuery query = RecipeSearchQuery.parse("");
	private long revision;
	private long started;
	private int ticksSincePublication;
	private int publishedMatchCount = -1;
	private int visibleMatches;
	private @Nullable ForkJoinPool executor;

	private RecipeCategoryPreferences.State categoryPreferences = RecipeCategoryPreferences.get();

	public RecipeSearchSession(IRecipeManager recipeManager, ISearchStorageBuilderFactory searchStorageBuilderFactory) {
		this.recipeManager = recipeManager;
		this.searchStorageBuilderFactory = searchStorageBuilderFactory;
	}

	private record Frame(RecipeSearchQuery query, List<IFocusedRecipes<?>> results) {}
	private final ArrayDeque<Frame> history = new ArrayDeque<>();
	private @Nullable ILookupState view;
	private @Nullable RecipeSearchPage page;
	private @Nullable GlobalRecipeIndexCache global;
	private @Nullable ILookupState deferred;
	private RecipeFilterMode deferredMode;
	private RecipeSearchQuery deferredQuery;
	private Set<FocusedRecipe> preferred;
	private long materialRevision;
	private long identityRevision;
	private long visibilityRevision;

	private long visibilityRevision() {
		return recipeManager instanceof IRecipeMaterialCacheInternal cache ? cache.getRecipeVisibilityRevision() : 0;
	}
	private int flags;

	private long materialRevision() {
		return recipeManager instanceof IRecipeMaterialCacheInternal cache ? cache.getRecipeMaterialRevision() : 0;
	}

	private int flags() {
		return searchStorageBuilderFactory instanceof CompactSearchStorageBuilder.Factory factory ? factory.options().getAsInt() : 0;
	}

	public void request(ILookupState state, RecipeFilterMode mode, RecipeSearchQuery query) {
		cancel();
		deferredMode = mode;
		deferredQuery = query;
		deferred = state;
	}

	public Optional<Result> request(ILookupState state, RecipeFilterMode mode, RecipeSearchQuery query, RecipePreferenceRules rules) {
		return request(state, mode, query, rules, null);
	}

	public Optional<Result> request(ILookupState state, RecipeFilterMode mode, RecipeSearchQuery query, RecipePreferenceRules rules, @Nullable IRecipeCategory<?> selected) {
		cancel();
		started = System.nanoTime();
		var categories = RecipeCategoryPreferences.get();
		long ingredients = ingredientSearch == null ? 0 : ingredientSearch.getIngredientSearchRevision();
		prepareIndex(selected);
		if (mode.isUnrestricted() && query.isEmpty()) {
			if (view != state || categoryPreferences != categories || (page != null && this.mode != mode && !page.allowsSameCategories(mode)))
				resetView();
			history.clear();
			this.mode = mode;
			this.query = query;
			categoryPreferences = categories;
			return Optional.empty();
		}
		long materials = materialRevision();
		long visibility = visibilityRevision();
		int options = flags();
		long identity = ingredientSearch == null ? 0 : ingredientSearch.getRecipeIdentityRevision();
		boolean valid = visibility == visibilityRevision && identity == identityRevision && view == state && materialRevision == materials && categoryPreferences == categories;
		var current = valid && page != null && (this.mode == mode || page.allowsSameCategories(mode)) ? page : new RecipeSearchPage(state, mode, global);
		if (!valid || current != page || preferenceRules != rules || ingredients != ingredientSearchRevision)
			preferred = null;
		if (current != page || this.mode != mode || preferred == null && mode.filtersPreference() ||
			ingredientSearchRevision != ingredients || flags != options)
			history.clear();
		identityRevision = identity;
		visibilityRevision = visibility;
		view = state;
		page = current;
		this.mode = mode;
		this.query = query;
		materialRevision = materials;
		categoryPreferences = categories;
		preferenceRules = rules;
		ingredientSearchRevision = ingredients;
		flags = options;
		BiPredicate<ITypedIngredient<?>, String> ingredientMatcher = (ingredient, token) -> {
			throw new IllegalStateException("Recipe ingredient search has not been configured");
		};
		boolean nativeBackend = searchStorageBuilderFactory instanceof CompactSearchStorageBuilder.Factory;
		var catalysts = nativeBackend ? page.catalysts : new IdentityHashMap<IRecipeCategory<?>, List<ITypedIngredient<?>>>();
		if (ingredientSearch != null) {
			Set<String> tokens = query.getIngredientTokens();
			if (nativeBackend && query.onlyMatchesCatalysts()) {
				// A page without catalysts can resolve every catalyst-only term without querying the ingredient corpus.
				for (var category : page.categories) {
					if (System.nanoTime() - started >= 1_500_000L)
						break;
					var values = catalysts.computeIfAbsent(category, key -> recipeManager.createRecipeCatalystLookup(key.getRecipeType()).get().toList());
					if (!values.isEmpty())
						break;
				}
				if (catalysts.size() == page.categories.size() && catalysts.values().stream().allMatch(List::isEmpty))
					tokens = Set.of();
			}
			ingredientSnapshot = ingredientSearch.createRecipeSearchSnapshot(tokens, ingredientSnapshot);
			if (global != null)
				ingredientSnapshot.useRecipeCorpus(global.orphans);
			ingredientMatcher = ingredientSnapshot;
		}
		while (!history.isEmpty() && !query.sameTerms(history.getLast().query) && !query.narrows(history.getLast().query, nativeBackend))
			history.removeLast();
		if (!history.isEmpty() && query.sameTerms(history.getLast().query) && page.fullyIndexed())
			return Optional.of(new Result(revision, query, ingredientMatcher, history.getLast().results));
		capture = new Capture(nativeBackend, ingredientMatcher, catalysts, selected);
		capture.advance(nativeBackend ? started + 8_000_000L : Long.MAX_VALUE, true);
		if (capture.done() && waves.stream().allMatch(w -> w.task == null))
			return Optional.of(finish());
		return Optional.empty();
	}

	private final class Capture {
		final boolean nativeBackend;
		final BiPredicate<ITypedIngredient<?>, String> matcher;
		final java.util.Map<IRecipeCategory<?>, List<ITypedIngredient<?>>> catalysts;
		final List<IRecipeCategory<?>> ranked;
		final java.util.function.Supplier<IRecipeSearchTextMatcher> factory;
		final IRecipeSearchTextMatcher text;
		final Frame prior = history.peekLast();
		final List<IFocusedRecipes<?>> preferences = new ArrayList<>();
		final List<IFocusedRecipes<?>> source = new ArrayList<>();
		final java.util.Map<IRecipeCategory<?>, RecipeSearchQuery.Compiled> conditions = new IdentityHashMap<>();
		int next;

		Capture(boolean nativeBackend, BiPredicate<ITypedIngredient<?>, String> matcher,
			java.util.Map<IRecipeCategory<?>, List<ITypedIngredient<?>>> catalysts, IRecipeCategory<?> selected) {
			this.nativeBackend = nativeBackend;
			this.matcher = matcher;
			this.catalysts = catalysts;
			ranked = new ArrayList<>(page.categories);
			if (selected != null && ranked.remove(selected))
				ranked.addFirst(selected);
			factory = RecipeSearchTextMatcher.createFactory(searchStorageBuilderFactory, query);
			text = factory.get();
		}

		boolean done() { return next == ranked.size(); }

		void advance(long deadline, boolean initial) {
			int first = next;
			while (!done() && (first == next || System.nanoTime() < deadline)) {
				var category = ranked.get(next++);
				boolean excluded = false;
				if (nativeBackend) {
					var condition = query.compileForCategory(category, () -> catalysts.computeIfAbsent(category, key -> recipeManager.createRecipeCatalystLookup(key.getRecipeType()).get().toList()), text, matcher);
					conditions.put(category, condition);
					excluded = condition.constant() && !condition.always();
				}
				if (!excluded || mode.filtersPreference() && preferred == null) {
					var focused = page.capture(category);
					if (!excluded)
						source.add(focused);
					if (mode.filtersPreference() && preferred == null)
						preferences.add(focused);
				}
			}
			// Preference resolution compares all categories, so retain the slices until its input is complete.
			if (mode.filtersPreference() && preferred == null && !done())
				return;
			Set<IRecipeCategory<?>> exact = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			List<IFocusedRecipes<?>> candidates = nativeBackend && prior != null
				? page.narrow(source, prior.results, query.sameTerms(prior.query) ? exact : null) : List.copyOf(source);
			var work = new RecipeSearchTraversal(recipeManager, page, candidates, mode, query, factory, matcher, preferred,
				nativeBackend ? catalysts : new IdentityHashMap<>(catalysts), exact, new IdentityHashMap<>(conditions), List.copyOf(preferences));
			source.clear();
			conditions.clear();
			preferences.clear();
			if (initial && nativeBackend && work.tryImmediate(started + (ranked.size() <= 3 ? 8_000_000L : 2_000_000L)))
				waves.add(new Wave(work, null));
			else {
				var pool = executor();
				var rules = preferenceRules;
				waves.add(new Wave(work, pool.submit(() -> work.scan(rules, pool.getParallelism()))));
			}
		}
	}

	private void prepareIndex(@Nullable IRecipeCategory<?> selected) {
		if (ingredientSearch == null || !(recipeManager instanceof IRecipeMaterialCacheInternal materials) ||
			!(searchStorageBuilderFactory instanceof CompactSearchStorageBuilder.Factory))
			return;
		long revision = materials.getRecipeMaterialRevision();
		long identity = ingredientSearch.getRecipeIndexGeneration();
		if (global != null && global.materialRevision == revision && global.identityRevision == identity)
			return;
		if (global != null)
			global.stop();
		var snapshot = ingredientSearch.createRecipeSearchSnapshot(Set.of(), null);
		global = snapshot.hasNativeIds() ? new GlobalRecipeIndexCache(materials, snapshot, revision, identity) : null;
		if (global != null)
			global.warm(selected);
	}

	private ForkJoinPool executor() {
		if (executor == null) {
			int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() * 2 / 3);
			executor = new ForkJoinPool(parallelism, pool -> {
				var worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
				worker.setDaemon(true);
				worker.setName("JEI Recipe Search-" + worker.getPoolIndex());
				return worker;
			}, null, false);
		}
		return executor;
	}

	private Result result() {
		var recipes = waves.stream().flatMap(w -> w.traversal.getResults().stream())
			.sorted(java.util.Comparator.comparingInt(group -> page.order(group.getRecipeCategory()))).toList();
		return new Result(revision, query, capture.matcher, recipes);
	}

	private Result finish() {
		var result = result();
		if (history.size() == 6)
			history.removeFirst();
		history.addLast(new Frame(query, result.recipes()));
		if (!waves.isEmpty())
			preferred = waves.getFirst().traversal.preferred();
		waves.clear();
		capture = null;
		return result;
	}

	public void setIngredientSearch(IngredientFilter ingredientSearch) {
		if (this.ingredientSearch != null && this.ingredientSearch != ingredientSearch)
			clear();
		this.ingredientSearch = ingredientSearch;
	}

	public boolean needsIngredientRefresh() {
		return view != null && (!query.isEmpty() || mode.filtersPreference()) && (flags != flags() || visibilityRevision != visibilityRevision() || materialRevision != materialRevision() ||
			ingredientSearch != null && ingredientSearchRevision != ingredientSearch.getIngredientSearchRevision());
	}

	public boolean isSearching() {
		return deferred != null || capture != null;
	}

	public boolean shouldShowProgress() {
		return isSearching() && System.nanoTime() - started >= 200_000_000L;
	}

	public boolean needsPreferenceRefresh(RecipePreferenceRules rules) {
		return deferred == null && (categoryPreferences != RecipeCategoryPreferences.get() || mode.filtersPreference() && preferenceRules != null && preferenceRules != rules);
	}

	public Optional<Result> tick(RecipePreferenceRules rules) {
		if (deferred != null)
			return request(deferred, deferredMode, deferredQuery, rules);
		if (capture == null)
			return Optional.empty();
		if (!capture.done())
			capture.advance(System.nanoTime() + 4_000_000L, false);
		ticksSincePublication++;
		boolean complete = capture.done();
		try {
			for (var wave : waves) {
				if (wave.task == null)
					continue;
				if (wave.task.isDone())
					wave.task.get();
				else
					complete = false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			cancel();
			return Optional.empty();
		} catch (ExecutionException e) {
			cancel();
			throw new IllegalStateException("Recipe search failed", e.getCause());
		}
		if (complete)
			return Optional.of(finish());
		int interval = System.nanoTime() - started < 1_000_000_000L ? 2 : 10;
		int progress = waves.stream().mapToInt(w -> w.traversal.progress() + 1).sum();
		if (ticksSincePublication >= interval && progress != publishedMatchCount) {
			ticksSincePublication = 0;
			publishedMatchCount = progress;
			var update = result();
			int count = update.recipes.stream().mapToInt(group -> group.getRecipes().size()).sum();
			if (count > visibleMatches) {
				visibleMatches = count;
				return Optional.of(update);
			}
		}
		return Optional.empty();
	}

	public void cancel() {
		revision++;
		deferred = null;
		for (var wave : waves)
			wave.traversal.cancel();
		waves.clear();
		capture = null;
		ticksSincePublication = 0;
		visibleMatches = 0;
		publishedMatchCount = -1;
	}

	public void resetView() {
		cancel();
		history.clear();
		page = null;
		view = null;
		preferred = null;
		preferenceRules = null;
	}

	public void clear() {
		resetView();
		ingredientSnapshot = null;
		if (global != null)
			global.stop();
		global = null;
		if (executor != null)
			executor.shutdownNow();
		executor = null;
	}

	public record Result(long revision, RecipeSearchQuery query,
		BiPredicate<ITypedIngredient<?>, String> ingredientMatcher, List<IFocusedRecipes<?>> recipes) {}
}
