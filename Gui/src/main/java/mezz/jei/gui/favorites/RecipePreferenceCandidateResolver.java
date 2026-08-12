package mezz.jei.gui.favorites;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.favorites.preferences.RecipePreferenceCandidate;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.match.IngredientMatchInfo;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * On-demand resolver for recipe preference candidates and generated favorites.
 * Replaces the full-catalog index that was previously built by
 * {@code GeneratedFavoriteRecipeScanner}.
 */
public final class RecipePreferenceCandidateResolver {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int CANDIDATE_CACHE_LIMIT = 2048;
	private static final int VARIANT_RECIPES_CACHE_LIMIT = 4096;
	private static final int GENERATED_FAVORITE_CACHE_LIMIT = 4096;

	private final IRecipeCandidateFinder recipeFinder;
	private final IRecipeCandidateFactory candidateFactory;
	private final Function<BookmarkIngredientKey, Optional<ITypedIngredient<?>>> typedIngredientResolver;
	private final Supplier<RecipePreferenceRules> rulesSupplier;

	private final Map<FocusedRecipe, Optional<RecipeCandidateResult>> candidateCache = lru(CANDIDATE_CACHE_LIMIT);
	private final Map<BookmarkIngredientKey, List<RecipeCandidateReference>> variantRecipesCache = lru(VARIANT_RECIPES_CACHE_LIMIT);
	private final Map<BookmarkIngredientKey, Optional<FocusedRecipe>> generatedFavoriteCache = lru(GENERATED_FAVORITE_CACHE_LIMIT);

	public RecipePreferenceCandidateResolver(
		IRecipeCandidateFinder recipeFinder,
		IRecipeCandidateFactory candidateFactory,
		Function<BookmarkIngredientKey, Optional<ITypedIngredient<?>>> typedIngredientResolver,
		Supplier<RecipePreferenceRules> rulesSupplier
	) {
		this.recipeFinder = Objects.requireNonNull(recipeFinder);
		this.candidateFactory = Objects.requireNonNull(candidateFactory);
		this.typedIngredientResolver = Objects.requireNonNull(typedIngredientResolver);
		this.rulesSupplier = Objects.requireNonNull(rulesSupplier);
	}

	public static RecipePreferenceCandidateResolver create(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager,
		Supplier<RecipePreferenceRules> rulesSupplier
	) {
		JeiRecipeCandidateSource source = new JeiRecipeCandidateSource(recipeManager, focusFactory, ingredientManager);
		return new RecipePreferenceCandidateResolver(
			source,
			source,
			key -> resolveTypedIngredient(key, ingredientManager),
			rulesSupplier
		);
	}

	public List<RecipePreferenceCandidate> getCandidates(BookmarkIngredientKey target, ITypedIngredient<?> output) {
		List<RecipeCandidateReference> references = variantRecipesCache.computeIfAbsent(target, key -> findVerifiedRecipes(key, output));
		if (references.isEmpty()) {
			return List.of();
		}
		List<RecipePreferenceCandidate> candidates = new ArrayList<>(references.size());
		for (RecipeCandidateReference reference : references) {
			Optional<RecipeCandidateResult> result = getCandidateResult(reference);
			if (result.isPresent()) {
				candidates.add(result.get().candidate());
			}
		}
		return List.copyOf(candidates);
	}

	public Optional<FocusedRecipe> resolveGeneratedFavorite(BookmarkIngredientKey target) {
		Optional<FocusedRecipe> cached = generatedFavoriteCache.get(target);
		if (cached != null) {
			return cached;
		}
		Optional<FocusedRecipe> resolved = resolveGeneratedFavoriteUncached(target);
		generatedFavoriteCache.put(target, resolved);
		return resolved;
	}

	private Optional<FocusedRecipe> resolveGeneratedFavoriteUncached(BookmarkIngredientKey target) {
		Optional<ITypedIngredient<?>> typed = typedIngredientResolver.apply(target);
		if (typed.isEmpty()) {
			return Optional.empty();
		}
		List<RecipePreferenceCandidate> candidates = getCandidates(target, typed.get());
		if (candidates.size() == 1) {
			return Optional.of(candidates.getFirst().recipe());
		}
		return rulesSupplier.get().resolvePreferredRecipe(candidates);
	}

	private List<RecipeCandidateReference> findVerifiedRecipes(
		BookmarkIngredientKey target,
		ITypedIngredient<?> output
	) {
		Set<FocusedRecipe> seen = new LinkedHashSet<>();
		List<RecipeCandidateReference> result = new ArrayList<>();
		for (RecipeCandidateReference reference : recipeFinder.findRecipes(output)) {
			Optional<RecipeCandidateResult> candidateResult = getCandidateResult(reference);
			if (candidateResult.isEmpty()) {
				continue;
			}
			if (!candidateResult.get().outputKeys().contains(target)) {
				continue;
			}
			if (seen.add(reference.focusedRecipe())) {
				result.add(reference);
			}
		}
		return List.copyOf(result);
	}

	private Optional<RecipeCandidateResult> getCandidateResult(RecipeCandidateReference reference) {
		Optional<RecipeCandidateResult> cached = candidateCache.get(reference.focusedRecipe());
		if (cached != null) {
			return cached;
		}
		Optional<RecipeCandidateResult> created = candidateFactory.create(reference);
		candidateCache.put(reference.focusedRecipe(), created);
		return created;
	}

	public void invalidateGeneratedFavorites() {
		generatedFavoriteCache.clear();
	}

	public void invalidateAll() {
		candidateCache.clear();
		variantRecipesCache.clear();
		generatedFavoriteCache.clear();
	}

	private static <K, V> Map<K, V> lru(int limit) {
		return new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				return size() > limit;
			}
		};
	}

	@SuppressWarnings({"removal", "rawtypes", "unchecked"})
	private static Optional<ITypedIngredient<?>> resolveTypedIngredient(
		BookmarkIngredientKey key,
		IIngredientManager ingredientManager
	) {
		Optional<IIngredientType<?>> type = ingredientManager.getIngredientTypeForUid(key.ingredientTypeUid());
		if (type.isEmpty()) {
			return Optional.empty();
		}
		IIngredientType rawType = type.get();
		return ingredientManager.getTypedIngredientByUid(rawType, key.ingredientUid())
			.map(ingredient -> (ITypedIngredient<?>) ingredient);
	}

	private static final class JeiRecipeCandidateSource implements IRecipeCandidateFinder, IRecipeCandidateFactory {
		private final IRecipeManager recipeManager;
		private final IFocusFactory focusFactory;
		private final IIngredientManager ingredientManager;

		private JeiRecipeCandidateSource(
			IRecipeManager recipeManager,
			IFocusFactory focusFactory,
			IIngredientManager ingredientManager
		) {
			this.recipeManager = recipeManager;
			this.focusFactory = focusFactory;
			this.ingredientManager = ingredientManager;
		}

		@Override
		public List<RecipeCandidateReference> findRecipes(ITypedIngredient<?> output) {
			IFocus<?> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, output);
			List<RecipeCandidateReference> result = new ArrayList<>();
			recipeManager.createRecipeCategoryLookup()
				.limitFocus(List.of(focus))
				.get()
				.forEach(category -> collectCategoryRecipes(category, focus, result));
			return List.copyOf(result);
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private void collectCategoryRecipes(
			IRecipeCategory recipeCategory,
			IFocus focus,
			List<RecipeCandidateReference> result
		) {
			recipeManager.createRecipeLookup(recipeCategory.getRecipeType())
				.limitFocus(List.of(focus))
				.get()
				.forEach(recipe -> {
					ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
					if (recipeUid != null) {
						FocusedRecipe focusedRecipe = new FocusedRecipe(recipeCategory.getRecipeType().getUid(), recipeUid);
						result.add(new RecipeCandidateReference(focusedRecipe, recipe));
					}
				});
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		@Override
		public Optional<RecipeCandidateResult> create(RecipeCandidateReference reference) {
			FocusedRecipe focusedRecipe = reference.focusedRecipe();
			Optional<RecipeType<?>> recipeType = recipeManager.getRecipeType(focusedRecipe.recipeTypeUid());
			if (recipeType.isEmpty()) {
				return Optional.empty();
			}
			IRecipeCategory recipeCategory = recipeManager.getRecipeCategory(recipeType.get());
			Optional<IRecipeLayoutDrawable<?>> layout = createLayout(recipeCategory, reference.recipe());
			if (layout.isEmpty()) {
				return Optional.empty();
			}
			IRecipeSlotsView slotsView = layout.get().getRecipeSlotsView();
			List<IngredientMatchInfo> inputs = collectIngredientMatches(slotsView, RecipeIngredientRole.INPUT);
			List<IngredientMatchInfo> outputs = collectIngredientMatches(slotsView, RecipeIngredientRole.OUTPUT);
			Set<BookmarkIngredientKey> outputKeys = slotsView
				.getSlotViews(RecipeIngredientRole.OUTPUT)
				.stream()
				.flatMap(IRecipeSlotView::getAllIngredients)
				.map(ingredient -> BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager))
				.collect(Collectors.toUnmodifiableSet());
			RecipePreferenceCandidate candidate = new RecipePreferenceCandidate(focusedRecipe, inputs, outputs);
			return Optional.of(new RecipeCandidateResult(candidate, outputKeys));
		}

		private static List<IngredientMatchInfo> collectIngredientMatches(
			IRecipeSlotsView slotsView,
			RecipeIngredientRole role
		) {
			return slotsView.getSlotViews(role)
				.stream()
				.flatMap(IRecipeSlotView::getAllIngredients)
				.map(IngredientMatchInfo::fromIngredient)
				.flatMap(Optional::stream)
				.toList();
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private Optional<IRecipeLayoutDrawable<?>> createLayout(
			IRecipeCategory recipeCategory,
			Object recipe
		) {
			try {
				return recipeManager.createRecipeLayoutDrawable(
					recipeCategory,
					recipe,
					focusFactory.getEmptyFocusGroup()
				)
				.map(layout -> (IRecipeLayoutDrawable<?>) layout);
			} catch (RuntimeException | LinkageError e) {
				LOGGER.warn(
					"Skipping recipe preference scan for recipe {} in category {}.",
					recipeCategory.getRegistryName(recipe),
					recipeCategory.getRecipeType(),
					e
				);
				return Optional.empty();
			}
		}
	}
}
