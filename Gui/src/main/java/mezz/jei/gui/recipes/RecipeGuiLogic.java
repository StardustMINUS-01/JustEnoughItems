package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IJeiClientConfigs;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.common.search.BakedSubstringIndexBuilder;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistory;
import mezz.jei.gui.recipes.filtering.IRecipeSearchTextMatcher;
import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.filtering.RecipeLookupSnapshot;
import mezz.jei.gui.recipes.filtering.RecipeLookupSnapshotFactory;
import mezz.jei.gui.recipes.filtering.RecipeSearchIngredient;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.IngredientLookupState;
import mezz.jei.gui.recipes.lookups.LookupStatePositionUtil;
import mezz.jei.gui.recipes.lookups.ProjectedLookupState;
import mezz.jei.gui.recipes.lookups.SingleCategoryLookupState;
import mezz.jei.gui.recipes.navigation.RecipeNavigationDirection;
import mezz.jei.gui.recipes.navigation.RecipeNavigationEntry;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RecipeGuiLogic implements IRecipeGuiLogic {
	private final IRecipeManager recipeManager;
	private final IRecipeTransferManager recipeTransferManager;
	private final IIngredientManager ingredientManager;
	private final IRecipeLogicStateListener stateListener;

	private final Supplier<RecipePreferenceRules> preferenceRulesSupplier;
	private final RecipeLookupSnapshotFactory snapshotFactory;
	private ILookupState unfilteredState;
	private final Stack<ILookupState> forwardHistory = new Stack<>();
	private final Map<ILookupState, RecipeNavigationEntry> navigationEntries = new IdentityHashMap<>();
	private RecipeFilterMode filterMode = RecipeFilterMode.ALL;
	private String searchQueryText = "";
	private RecipeSearchQuery searchQuery = RecipeSearchQuery.parse("");
	private IRecipeSearchTextMatcher searchTextMatcher = IRecipeSearchTextMatcher.DEFAULT;
	private @Nullable RecipeLookupSnapshot snapshot;
	private @Nullable RecipePreferenceRules snapshotPreferenceRules;

	private boolean initialState = true;
	private ILookupState state;
	private final Stack<ILookupState> stateHistory = new Stack<>();
	private final LookupHistory lookupHistory;
	private final IFocusFactory focusFactory;
	private final BookmarkList bookmarks;
	private final IRecipeLayoutWithButtonsFactory recipeLayoutFactory;
	private @Nullable IRecipeCategory<?> cachedRecipeCategory;
	private @Nullable IRecipeLayoutList cachedRecipeLayoutsWithButtons;
	private int cachedContainerId = -1;
	private Set<RecipeSorterStage> cachedSorterStages = Set.of();

	public RecipeGuiLogic(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		LookupHistory lookupHistory,
		IRecipeTransferManager recipeTransferManager,
		IRecipeLogicStateListener stateListener,
		IFocusFactory focusFactory,
		BookmarkList bookmarks,
		IRecipeLayoutWithButtonsFactory recipeLayoutFactory
	) {
		this(recipeManager, ingredientManager, lookupHistory, recipeTransferManager, stateListener, focusFactory, bookmarks, recipeLayoutFactory, () -> RecipePreferenceRules.EMPTY, BakedSubstringIndexBuilder::new);
	}

	public RecipeGuiLogic(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		LookupHistory lookupHistory,
		IRecipeTransferManager recipeTransferManager,
		IRecipeLogicStateListener stateListener,
		IFocusFactory focusFactory,
		BookmarkList bookmarks,
		IRecipeLayoutWithButtonsFactory recipeLayoutFactory,
		Supplier<RecipePreferenceRules> preferenceRulesSupplier,
		ISearchStorageBuilderFactory searchStorageBuilderFactory
	) {
		this.preferenceRulesSupplier = preferenceRulesSupplier;
		this.snapshotFactory = new RecipeLookupSnapshotFactory(recipeManager, ingredientManager, focusFactory, searchStorageBuilderFactory);
		this.recipeManager = recipeManager;
		this.ingredientManager = ingredientManager;
		this.lookupHistory = lookupHistory;
		this.recipeTransferManager = recipeTransferManager;
		this.stateListener = stateListener;
		this.recipeLayoutFactory = recipeLayoutFactory;
		this.bookmarks = bookmarks;
		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.get()
			.toList();
		this.state = IngredientLookupState.create(
			recipeManager,
			focusFactory.getEmptyFocusGroup(),
			recipeCategories,
			recipeTransferManager
		);
		this.unfilteredState = this.state;
		this.focusFactory = focusFactory;
	}

	@Override
	public void tick(@Nullable AbstractContainerMenu container) {
		if (cachedRecipeLayoutsWithButtons != null) {
			cachedRecipeLayoutsWithButtons.tick(container);
		}
		if (snapshotPreferenceRules != null && snapshotPreferenceRules != preferenceRulesSupplier.get()) {
			rebuildDisplayedState();
			clearLayoutCache();
			stateListener.onStateChange();
		}
	}

	@Override
	public boolean showFocus(IFocusGroup focuses) {
		List<IFocus<?>> allFocuses = focuses.getAllFocuses();
		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.limitFocus(allFocuses)
			.get()
			.toList();
		ILookupState state = IngredientLookupState.create(
			recipeManager,
			focuses,
			recipeCategories,
			recipeTransferManager
		);

		for (IFocus<?> focus : allFocuses) {
			IngredientBookmark<?> ingredientBookmark = IngredientBookmark.create(focus.getTypedValue(), ingredientManager);
			this.lookupHistory.add(ingredientBookmark);
		}

		return setState(state, true);
	}

	public boolean showRecipes(IFocusedRecipes<?> focusedRecipes, IFocusGroup focuses) {
		var recipeBookmark = createRecipeBookmark(recipeManager, ingredientManager, focusedRecipes, focuses);
		if (recipeBookmark != null) {
			this.lookupHistory.add(recipeBookmark);
		} else {
			for (IFocus<?> focus : focuses.getAllFocuses()) {
				IngredientBookmark<?> ingredientBookmark = IngredientBookmark.create(focus.getTypedValue(), ingredientManager);
				this.lookupHistory.add(ingredientBookmark);
			}
		}
		ILookupState state = new SingleCategoryLookupState(focusedRecipes, focuses);
		return setState(state, true);
	}

	private static <T> @Nullable RecipeBookmark<T, ?> createRecipeBookmark(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		IFocusedRecipes<T> focusedRecipes,
		IFocusGroup focusGroup
	) {
		IRecipeCategory<T> recipeCategory = focusedRecipes.getRecipeCategory();
		List<T> recipes = focusedRecipes.getRecipes();
		if (recipes.size() != 1) {
			return null;
		}
		T recipe = recipes.get(0);
		return recipeManager.createRecipeLayoutDrawable(recipeCategory, recipe, focusGroup)
			.map(drawable -> RecipeBookmark.create(drawable, ingredientManager))
			.orElse(null);
	}

	@Override
	public boolean back() {
		return navigate(RecipeNavigationDirection.BACK, false);
	}

	public boolean navigate(RecipeNavigationDirection direction, boolean jumpToEnd) {
		Stack<ILookupState> source = direction == RecipeNavigationDirection.BACK ? stateHistory : forwardHistory;
		Stack<ILookupState> destination = direction == RecipeNavigationDirection.BACK ? forwardHistory : stateHistory;
		if (source.empty()) {
			return false;
		}
		updateCurrentNavigationEntry();
		do {
			destination.push(unfilteredState);
			unfilteredState = source.pop();
		} while (jumpToEnd && !source.empty());
		return restoreNavigationEntry(navigationEntries.get(unfilteredState));
	}

	public boolean canNavigate(RecipeNavigationDirection direction) {
		return !(direction == RecipeNavigationDirection.BACK ? stateHistory : forwardHistory).empty();
	}

	public Optional<Component> getNavigationTargetTitle(RecipeNavigationDirection direction) {
		Stack<ILookupState> history = direction == RecipeNavigationDirection.BACK ? stateHistory : forwardHistory;
		return history.empty() ? Optional.empty() : Optional.of(navigationEntries.get(history.peek()).getTitle());
	}

	public void updateCurrentInputSelections(Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> inputSelections) {
		Optional.ofNullable(navigationEntries.get(unfilteredState)).ifPresent(entry -> entry.updateInputSelections(inputSelections));
	}

	public Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> getCurrentInputSelections() {
		return Optional.ofNullable(navigationEntries.get(unfilteredState))
			.map(RecipeNavigationEntry::getInputSelections).orElseGet(Map::of);
	}

	public RecipeFilterMode getFilterMode() {
		return filterMode;
	}

	public String getSearchQueryText() {
		return searchQueryText;
	}

	boolean hasInputSearchTerms() {
		return searchQuery.hasInputTerms();
	}

	boolean matchesInputCandidate(RecipeSearchIngredient ingredient) {
		return searchQuery.matchesInputCandidate(ingredient, searchTextMatcher);
	}

	@Override
	public void clearHistory() {
		stateHistory.clear();
		forwardHistory.clear();
		navigationEntries.clear();
		initialState = true;
	}

	private boolean setState(ILookupState state, boolean saveHistory) {
		if (state.getRecipeCategories().isEmpty()) {
			return false;
		}
		if (saveHistory) {
			updateCurrentNavigationEntry();
			if (!initialState) {
				stateHistory.push(unfilteredState);
				if (stateHistory.size() >= 128) {
					navigationEntries.remove(stateHistory.remove(0));
				}
			}
			forwardHistory.forEach(navigationEntries::remove);
			forwardHistory.clear();
		}
		this.unfilteredState = state;
		this.state = state;
		this.initialState = false;
		this.snapshot = null;
		this.snapshotPreferenceRules = null;
		this.searchTextMatcher = IRecipeSearchTextMatcher.DEFAULT;
		rebuildDisplayedState();
		clearLayoutCache();
		if (saveHistory) {
			navigationEntries.put(state, createNavigationEntry());
		}
		stateListener.onStateChange();
		updateCurrentNavigationEntry();
		return true;
	}

	private RecipeNavigationEntry createNavigationEntry() {
		return new RecipeNavigationEntry(
			unfilteredState,
			createNavigationTitle(unfilteredState),
			filterMode,
			searchQueryText,
			state
		);
	}

	private void updateCurrentNavigationEntry() {
		Optional.ofNullable(navigationEntries.get(unfilteredState))
			.ifPresent(entry -> entry.updateView(filterMode, searchQueryText, state));
	}

	private boolean restoreNavigationEntry(RecipeNavigationEntry entry) {
		this.filterMode = entry.getFilterMode();
		this.searchQueryText = entry.getSearchQuery();
		this.searchQuery = RecipeSearchQuery.parse(searchQueryText);
		this.unfilteredState = entry.getLookupState();
		this.state = unfilteredState;
		this.snapshot = null;
		this.snapshotPreferenceRules = null;
		this.searchTextMatcher = IRecipeSearchTextMatcher.DEFAULT;
		rebuildDisplayedState();
		restorePosition(entry);
		clearLayoutCache();
		stateListener.onStateChange();
		return true;
	}

	private void restorePosition(RecipeNavigationEntry entry) {
		int recipesPerPage = Math.max(1, entry.getRecipesPerPage());
		state.setRecipesPerPage(recipesPerPage);
		state.moveToRecipeCategory(entry.getRecipeCategory());
		LookupStatePositionUtil.restoreRecipeIndex(state, entry.getRecipeIndex());
	}

	private Component createNavigationTitle(ILookupState lookupState) {
		return lookupState.getFocuses().getAllFocuses().stream()
			.findFirst()
			.map(this::createNavigationTitle)
			.orElseGet(() -> lookupState.getFocusedRecipes().getRecipeCategory().getTitle());
	}

	private <T> Component createNavigationTitle(IFocus<T> focus) {
		ITypedIngredient<T> typedIngredient = focus.getTypedValue();
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		Component ingredientName = Component.literal(ingredientHelper.getDisplayName(typedIngredient.getIngredient()));
		String translationKey = focus.getRole() == mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT ? "gui.jei.recipe_navigation.target.recipes" : "gui.jei.recipe_navigation.target.uses";
		return Component.translatable(translationKey, ingredientName);
	}

	private void rebuildDisplayedState() {
		IRecipeCategory<?> selectedCategory = this.state.getFocusedRecipes().getRecipeCategory();
		if (filterMode == RecipeFilterMode.ALL && searchQuery.isEmpty()) {
			this.snapshot = null;
			this.snapshotPreferenceRules = null;
			this.searchTextMatcher = IRecipeSearchTextMatcher.DEFAULT;
			this.state = unfilteredState;
			this.state.moveToRecipeCategory(selectedCategory);
			return;
		}

		RecipePreferenceRules preferenceRules = filterMode != RecipeFilterMode.ALL || snapshotPreferenceRules != null ? preferenceRulesSupplier.get() : null;
		if (snapshot == null || snapshotPreferenceRules != preferenceRules) {
			this.snapshot = preferenceRules == null ? snapshotFactory.create(unfilteredState) : snapshotFactory.create(unfilteredState, preferenceRules);
			this.snapshotPreferenceRules = preferenceRules;
		}
		this.searchTextMatcher = searchQuery.isEmpty() ? IRecipeSearchTextMatcher.DEFAULT : snapshot.createSearchTextMatcher();
		this.state = new ProjectedLookupState(
			unfilteredState,
			snapshot.project(filterMode, searchQuery, searchTextMatcher)
		);
		this.state.moveToRecipeCategory(selectedCategory);
	}

	private void clearLayoutCache() {
		this.cachedRecipeCategory = null;
		this.cachedRecipeLayoutsWithButtons = null;
		this.cachedContainerId = -1;
	}

	@Override
	public void applyRecipeResultFilter(RecipeFilterMode mode, String query) {
		this.filterMode = mode;
		this.searchQueryText = query;
		this.searchQuery = RecipeSearchQuery.parse(query);
		rebuildDisplayedState();
		clearLayoutCache();
		stateListener.onStateChange();
		updateCurrentNavigationEntry();
	}

	@Override
	public void clearRecipeResultSnapshot() {
		this.snapshot = null;
		this.snapshotPreferenceRules = null;
		this.searchTextMatcher = IRecipeSearchTextMatcher.DEFAULT;
		this.state = unfilteredState;
		clearLayoutCache();
	}

	@Override
	public boolean hasRecipeResults() {
		return !state.getFocusedRecipes().getRecipes().isEmpty();
	}

	@Override
	public boolean showAllRecipes() {
		IRecipeCategory<?> recipeCategory = getSelectedRecipeCategory();

		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.get()
			.toList();
		final ILookupState state = IngredientLookupState.create(
			recipeManager,
			focusFactory.getEmptyFocusGroup(),
			recipeCategories,
			recipeTransferManager
		);
		state.moveToRecipeCategory(recipeCategory);
		setState(state, true);

		return true;
	}

	@Override
	public boolean showCategories(List<RecipeType<?>> recipeTypes) {
		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.limitTypes(recipeTypes)
			.get()
			.toList();

		final ILookupState state = IngredientLookupState.create(
			recipeManager,
			focusFactory.getEmptyFocusGroup(),
			recipeCategories,
			recipeTransferManager
		);
		if (state.getRecipeCategories().isEmpty()) {
			return false;
		}

		setState(state, true);

		return true;
	}

	@Override
	public Stream<ITypedIngredient<?>> getRecipeCatalysts() {
		if (!hasRecipeResults()) {
			return Stream.empty();
		}
		IRecipeCategory<?> category = getSelectedRecipeCategory();
		return getRecipeCatalysts(category);
	}

	@Override
	public Stream<ITypedIngredient<?>> getRecipeCatalysts(IRecipeCategory<?> recipeCategory) {
		RecipeType<?> recipeType = recipeCategory.getRecipeType();
		return recipeManager.createRecipeCatalystLookup(recipeType)
			.get();
	}

	@Override
	public IRecipeCategory<?> getSelectedRecipeCategory() {
		return state.getFocusedRecipes().getRecipeCategory();
	}

	@Override
	@Unmodifiable
	public List<IRecipeCategory<?>> getRecipeCategories() {
		return state.getRecipeCategories();
	}

	@Override
	public List<IRecipeLayoutWithButtons<?>> getVisibleRecipeLayoutsWithButtons(
		int availableHeight,
		int minRecipePadding,
		@Nullable AbstractContainerMenu container
	) {
		IRecipeCategory<?> recipeCategory = getSelectedRecipeCategory();

		IJeiClientConfigs jeiClientConfigs = Internal.getJeiClientConfigs();
		IClientConfig clientConfig = jeiClientConfigs.getClientConfig();
		Set<RecipeSorterStage> recipeSorterStages = clientConfig.getRecipeSorterStages();

		int containerId = container == null ? -1 : container.containerId;
		if (!recipeSorterStages.equals(cachedSorterStages) ||
			this.cachedRecipeLayoutsWithButtons == null ||
			this.cachedRecipeCategory != recipeCategory ||
			this.cachedContainerId != containerId
		) {
			IFocusedRecipes<?> focusedRecipes = this.state.getFocusedRecipes();

			this.cachedRecipeLayoutsWithButtons = IRecipeLayoutList.create(
				recipeSorterStages,
				focusedRecipes,
				state.getFocuses(),
				bookmarks,
				recipeManager,
				recipeLayoutFactory
			);
			this.cachedRecipeCategory = recipeCategory;
			this.cachedSorterStages = Set.copyOf(recipeSorterStages);
			this.cachedContainerId = containerId;
		}

		final int recipeHeight =
			this.cachedRecipeLayoutsWithButtons.findFirst(container)
				.map(IRecipeLayoutWithButtons::getRecipeLayout)
				.map(IRecipeLayoutDrawable::getRectWithBorder)
				.map(Rect2i::getHeight)
				.orElseGet(recipeCategory::getHeight);

		final int recipesPerPage = Math.max(1, 1 + ((availableHeight - recipeHeight) / (recipeHeight + minRecipePadding)));
		this.state.setRecipesPerPage(recipesPerPage);

		return this.state.getVisible(this.cachedRecipeLayoutsWithButtons, container);
	}

	@Override
	public int getRecipesPerPage() {
		return this.state.getRecipesPerPage();
	}

	@Override
	public void nextRecipeCategory() {
		state.nextRecipeCategory();
		stateListener.onStateChange();
	}

	@Override
	public void setRecipeCategory(IRecipeCategory<?> category) {
		if (state.moveToRecipeCategory(category)) {
			stateListener.onStateChange();
		}
	}

	@Override
	public boolean hasMultiplePages() {
		List<?> recipes = state.getFocusedRecipes().getRecipes();
		return recipes.size() > state.getRecipesPerPage();
	}

	@Override
	public void previousRecipeCategory() {
		state.previousRecipeCategory();
		stateListener.onStateChange();
	}

	@Override
	public void goToFirstPage() {
		state.goToFirstPage();
		stateListener.onStateChange();
	}

	@Override
	public void nextPage() {
		state.nextPage();
		stateListener.onStateChange();
	}

	@Override
	public void previousPage() {
		state.previousPage();
		stateListener.onStateChange();
	}

	@Override
	public String getPageString() {
		if (!hasRecipeResults()) {
			return "0/0";
		}
		int pageIndex = MathUtil.divideCeil(state.getRecipeIndex() + 1, state.getRecipesPerPage());
		return pageIndex + "/" + state.pageCount();
	}

	@Override
	public boolean hasMultipleCategories() {
		return state.getRecipeCategories().size() > 1;
	}

	@Override
	public boolean hasAllCategories() {
		long categoryCount = recipeManager.createRecipeCategoryLookup()
			.get()
			.count();

		return state.getRecipeCategories().size() == categoryCount;
	}

}
