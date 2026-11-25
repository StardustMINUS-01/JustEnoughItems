package mezz.jei.gui.recipes;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.recipes.IRecipeVisibility;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.search.ISearchStorageBuilderFactory;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientConfigs;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.common.search.LuceneSearchStorageBuilder;
import mezz.jei.common.config.RecipeGuiNavigationMode;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.common.util.ImmutableSize2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistory;
import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.recipes.filtering.RecipeSearchSession;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.IngredientLookupState;
import mezz.jei.gui.recipes.lookups.LookupStatePositionUtil;
import mezz.jei.gui.recipes.lookups.ProjectedLookupState;
import mezz.jei.gui.recipes.lookups.SingleCategoryLookupState;
import mezz.jei.gui.recipes.navigation.RecipeNavigationEntry;
import mezz.jei.gui.recipes.navigation.RecipeNavigationDirection;
import mezz.jei.gui.recipes.navigation.RecipeNavigationHistory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public class RecipeGuiLogic implements IRecipeGuiLogic {
	private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
	private final IRecipeManager recipeManager;
	private final IIngredientManager ingredientManager;
	private final RecipeTransferService recipeTransferService;
	private final IRecipeLogicStateListener stateListener;
	private final Supplier<RecipePreferenceRules> preferenceRulesSupplier;

	private ILookupState state;
	private ILookupState unfilteredState;
	private final RecipeNavigationHistory<RecipeNavigationEntry> navigationHistory = new RecipeNavigationHistory<>();
	private boolean scrolling;
	private RecipeGuiGrid recipeGuiGrid = new RecipeGuiGrid(1, 1);
	private final LookupHistory lookupHistory;
	private final IFocusFactory focusFactory;
	private final BookmarkFactory bookmarkFactory;
	private @Nullable IRecipeCategory<?> cachedRecipeCategory;
	private @Nullable IRecipeLayoutList cachedRecipeLayoutsWithButtons;
	private int cachedContainerId = -1;
	private Set<RecipeSorterStage> cachedSorterStages = Set.of();
	private RecipeFilterMode filterMode = RecipeFilterMode.DEFAULT;
	private mezz.jei.gui.recipes.filtering.RecipeSearchScope searchScope = mezz.jei.gui.recipes.filtering.RecipeSearchScope.NONE;

	public void setSearchScope(mezz.jei.gui.recipes.filtering.RecipeSearchScope scope) {
		searchScope = scope;
	}

	public mezz.jei.gui.recipes.filtering.RecipeSearchScope getSearchScope() { return searchScope; }
	private String searchQueryText = "";
	private RecipeSearchQuery searchQuery = RecipeSearchQuery.parse("");
	private BiPredicate<ITypedIngredient<?>, String> searchIngredientMatcher = (ingredient, token) -> true;
	private final RecipeSearchSession searchSession;
	private RecipeSearchQuery displayedSearchQuery = searchQuery;
	private long displayedSearchRevision = -1;
	private @Nullable RecipeNavigationEntry pendingSearchPosition;
	private @Nullable Double pendingScrollAnchor;
	private @Nullable Boolean searchBlockTags;

	public RecipeGuiLogic(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		LookupHistory lookupHistory,
		RecipeTransferService recipeTransferService,
		IRecipeLogicStateListener stateListener,
		IFocusFactory focusFactory,
		BookmarkFactory bookmarkFactory
	) {
		this(
			recipeManager,
			ingredientManager,
			lookupHistory,
			recipeTransferService,
			stateListener,
			focusFactory,
			bookmarkFactory,
			() -> RecipePreferenceRules.EMPTY
		);
	}

	public RecipeGuiLogic(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		LookupHistory lookupHistory,
		RecipeTransferService recipeTransferService,
		IRecipeLogicStateListener stateListener,
		IFocusFactory focusFactory,
		BookmarkFactory bookmarkFactory,
		Supplier<RecipePreferenceRules> preferenceRulesSupplier
	) {
		this(
			recipeManager,
			ingredientManager,
			lookupHistory,
			recipeTransferService,
			stateListener,
			focusFactory,
			bookmarkFactory,
			preferenceRulesSupplier,
			LuceneSearchStorageBuilder.FACTORY
		);
	}

	public RecipeGuiLogic(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		LookupHistory lookupHistory,
		RecipeTransferService recipeTransferService,
		IRecipeLogicStateListener stateListener,
		IFocusFactory focusFactory,
		BookmarkFactory bookmarkFactory,
		Supplier<RecipePreferenceRules> preferenceRulesSupplier,
		ISearchStorageBuilderFactory searchStorageBuilderFactory
	) {
		this.recipeManager = recipeManager;
		this.ingredientManager = ingredientManager;
		this.lookupHistory = lookupHistory;
		this.recipeTransferService = recipeTransferService;
		this.stateListener = stateListener;
		this.preferenceRulesSupplier = preferenceRulesSupplier;
		this.searchSession = new RecipeSearchSession(recipeManager, searchStorageBuilderFactory);
		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.get()
			.toList();
		this.unfilteredState = IngredientLookupState.create(
			recipeManager,
			focusFactory.getEmptyFocusGroup(),
			recipeCategories,
			recipeTransferService
		);
		this.state = unfilteredState;
		this.focusFactory = focusFactory;
		this.bookmarkFactory = bookmarkFactory;
	}

	@Override
	public void tick() {
		if (cachedRecipeLayoutsWithButtons != null) {
			cachedRecipeLayoutsWithButtons.tick();
		}
		Internal.getOptionalClientConfigs().ifPresent(configs -> {
			boolean blockTags = configs.getClientConfig().lookupBlockTagsEnabled().get();
			if (searchBlockTags != null && searchBlockTags != blockTags) {
				searchSession.clear();
				rebuildDisplayedState();
			}
			searchBlockTags = blockTags;
		});
		RecipePreferenceRules rules = preferenceRulesSupplier.get();
		if (searchSession.needsPreferenceRefresh(rules) || searchSession.needsIngredientRefresh()) {
			rebuildDisplayedState();
		}
		Optional<RecipeSearchSession.Result> completed;
		try {
			completed = searchSession.tick(rules);
		} catch (RuntimeException | LinkageError e) {
			searchSession.clear();
			LOGGER.error("Recipe search failed for query '{}' in mode {}", searchQueryText, filterMode, e);
			net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("gui.jei.recipe_filter.error"));
			return;
		}
		completed.ifPresent(result -> {
			IRecipeCategory<?> selected = state.getFocusedRecipes().getRecipeCategory();
			int recipeIndex = state.getRecipeIndex();
			int recipesPerPage = state.getRecipesPerPage();
			double scrollAnchor = state.getScrollState().getRecipeAnchor();
			this.searchIngredientMatcher = result.ingredientMatcher();
			this.displayedSearchQuery = result.query();
			this.state = new ProjectedLookupState(unfilteredState, result.recipes());
			this.state.moveToRecipeCategory(selected);
			this.state.setRecipesPerPage(recipesPerPage);
			if (displayedSearchRevision == result.revision() && state.getFocusedRecipes().getRecipeCategory() == selected) {
				LookupStatePositionUtil.restoreRecipeIndex(state, recipeIndex);
				pendingScrollAnchor = scrollAnchor;
			}
			this.displayedSearchRevision = result.revision();
			if (pendingSearchPosition != null && !searchSession.isSearching()) {
				restorePosition(pendingSearchPosition);
				pendingSearchPosition = null;
			}
			clearLayoutCache();
			stateListener.onStateChange();
			if (pendingSearchPosition == null)
				updateCurrentNavigationEntry();
		});
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
			recipeTransferService
		);

		for (IFocus<?> focus : allFocuses) {
			IngredientBookmark<?> ingredientBookmark = bookmarkFactory.create(focus.getTypedValue());
			this.lookupHistory.add(ingredientBookmark);
		}

		return setState(state, true);
	}

	@Override
	public boolean showRecipes(IFocusedRecipes<?> focusedRecipes, IFocusGroup focuses) {
		focusedRecipes = filterVisibleRecipes(focusedRecipes, focuses);
		if (focusedRecipes.getRecipes().isEmpty()) {
			return false;
		}
		var recipeBookmark = createRecipeBookmark(recipeManager, ingredientManager, recipeTransferService, focusedRecipes, focuses);
		if (recipeBookmark != null) {
			this.lookupHistory.add(recipeBookmark);
		} else {
			for (IFocus<?> focus : focuses.getAllFocuses()) {
				IngredientBookmark<?> ingredientBookmark = bookmarkFactory.create(focus.getTypedValue());
				this.lookupHistory.add(ingredientBookmark);
			}
		}
		ILookupState state = new SingleCategoryLookupState(focusedRecipes, focuses);
		return setState(state, true);
	}

	private <T> IFocusedRecipes<T> filterVisibleRecipes(IFocusedRecipes<T> focusedRecipes, IFocusGroup focuses) {
		if (!(recipeManager instanceof IRecipeVisibility recipeVisibility)) {
			return focusedRecipes;
		}
		IRecipeCategory<T> recipeCategory = focusedRecipes.getRecipeCategory();
		List<T> visibleRecipes = focusedRecipes.getRecipes().stream()
			.filter(recipe -> recipeVisibility.isRecipeVisible(recipeCategory, recipe, focuses))
			.toList();
		return new StaticFocusedRecipes<>(recipeCategory, visibleRecipes);
	}

	private static <T> @Nullable RecipeBookmark<T, ?> createRecipeBookmark(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		RecipeTransferService recipeTransferService,
		IFocusedRecipes<T> focusedRecipes,
		IFocusGroup focusGroup
	) {
		IRecipeCategory<T> recipeCategory = focusedRecipes.getRecipeCategory();
		List<T> recipes = focusedRecipes.getRecipes();
		if (recipes.size() != 1) {
			return null;
		}
		T recipe = recipes.getFirst();
		return recipeManager.createRecipeLayoutDrawable(recipeCategory, recipe, focusGroup)
			.map(drawable -> RecipeBookmark.create(drawable, ingredientManager, recipeTransferService))
			.orElse(null);
	}

	@Override
	public boolean back() {
		return navigate(RecipeNavigationDirection.BACK, false);
	}

	public boolean navigate(RecipeNavigationDirection direction, boolean jumpToEnd) {
		updateCurrentNavigationEntry();
		return navigationHistory.navigate(direction, jumpToEnd)
			.map(this::restoreNavigationEntry)
			.orElse(false);
	}

	@Override
	public boolean forward() {
		return navigate(RecipeNavigationDirection.FORWARD, false);
	}

	public boolean canNavigate(RecipeNavigationDirection direction) {
		return navigationHistory.canNavigate(direction);
	}

	public Optional<Component> getNavigationTargetTitle(RecipeNavigationDirection direction) {
		return navigationHistory.peek(direction)
			.map(RecipeNavigationEntry::getTitle);
	}

	public void updateCurrentInputSelections(Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> inputSelections) {
		navigationHistory.current()
			.ifPresent(entry -> entry.updateInputSelections(inputSelections));
	}

	public Map<FocusedRecipe, Map<Integer, BookmarkIngredientKey>> getCurrentInputSelections() {
		return navigationHistory.current()
			.map(RecipeNavigationEntry::getInputSelections)
			.orElseGet(Map::of);
	}

	public RecipeFilterMode getFilterMode() {
		return filterMode;
	}

	public String getSearchQueryText() {
		return searchQueryText;
	}

	boolean hasInputSearchTerms() {
		return displayedSearchQuery.hasInputTerms();
	}

	boolean matchesInputCandidate(ITypedIngredient<?> ingredient) {
		return displayedSearchQuery.matchesInputCandidate(ingredient, searchIngredientMatcher);
	}

	void setIngredientSearch(IngredientFilter ingredientSearch) {
		searchSession.setIngredientSearch(ingredientSearch);
	}

	@Override
	public void clearHistory() {
		navigationHistory.clear();
	}

	private boolean setState(ILookupState state, boolean saveHistory) {
		List<IRecipeCategory<?>> recipeCategories = state.getRecipeCategories();
		if (recipeCategories.isEmpty()) {
			return false;
		}

		if (saveHistory) {
			updateCurrentNavigationEntry();
		}
		this.unfilteredState = state;
		this.state = state;
		this.pendingScrollAnchor = null;
		this.searchSession.clear();
		this.pendingSearchPosition = null;
		this.displayedSearchQuery = RecipeSearchQuery.parse("");
		this.searchIngredientMatcher = (ingredient, token) -> true;
		rebuildDisplayedState();
		clearLayoutCache();
		if (saveHistory) {
			navigationHistory.push(createNavigationEntry());
		}
		stateListener.onStateChange();
		if (saveHistory) {
			updateCurrentNavigationEntry();
		}
		return true;
	}

	private RecipeNavigationEntry createNavigationEntry() {
		RecipeNavigationEntry entry = new RecipeNavigationEntry(
			unfilteredState,
			createNavigationTitle(unfilteredState),
			filterMode,
			searchQueryText,
			state
		);
		entry.setSearchScope(searchScope);
		return entry;
	}

	private void updateCurrentNavigationEntry() {
		navigationHistory.current()
			.ifPresent(entry -> {
				entry.updateView(filterMode, searchQueryText, state);
				entry.setSearchScope(searchScope);
			});
	}

	private boolean restoreNavigationEntry(RecipeNavigationEntry entry) {
		this.searchScope = entry.getSearchScope();
		this.filterMode = entry.getFilterMode();
		this.searchQueryText = entry.getSearchQuery();
		this.searchQuery = RecipeSearchQuery.parse(searchQueryText).withDefaultScope(searchScope);
		this.unfilteredState = entry.getLookupState();
		this.state = unfilteredState;
		this.searchSession.clear();
		this.displayedSearchQuery = RecipeSearchQuery.parse("");
		this.searchIngredientMatcher = (ingredient, token) -> true;
		rebuildDisplayedState();
		restorePosition(entry);
		this.pendingSearchPosition = searchSession.isSearching() ? entry : null;
		clearLayoutCache();
		stateListener.onStateChange();
		return true;
	}

	private void restorePosition(RecipeNavigationEntry entry) {
		int recipesPerPage = Math.max(1, entry.getRecipesPerPage());
		state.setRecipesPerPage(recipesPerPage);
		state.moveToRecipeCategory(entry.getRecipeCategory());
		LookupStatePositionUtil.restoreRecipeIndex(state, entry.getRecipeIndex());
		pendingScrollAnchor = entry.getScrollAnchor();
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
		searchSession.request(unfilteredState, filterMode, searchQuery);
		if (filterMode.isUnrestricted() && searchQuery.isEmpty()) {
			this.displayedSearchQuery = searchQuery;
			this.pendingSearchPosition = null;
			this.searchIngredientMatcher = (ingredient, token) -> true;
			this.state = unfilteredState;
			this.state.moveToRecipeCategory(selectedCategory);
			return;
		}

	}

	boolean isRecipeSearchPending() {
		return searchSession.shouldShowProgress();
	}

	private void clearLayoutCache() {
		this.cachedRecipeCategory = null;
		this.cachedRecipeLayoutsWithButtons = null;
		this.cachedContainerId = -1;
	}

	@Override
	public void applyRecipeResultFilter(RecipeFilterMode mode, String query) {
		this.pendingSearchPosition = null;
		this.pendingScrollAnchor = null;
		this.filterMode = mode;
		this.searchQueryText = query;
		this.searchQuery = RecipeSearchQuery.parse(query).withDefaultScope(searchScope);
		rebuildDisplayedState();
		clearLayoutCache();
		stateListener.onStateChange();
		updateCurrentNavigationEntry();
	}

	@Override
	public void clearRecipeResultSnapshot() {
		this.searchSession.clear();
		this.pendingSearchPosition = null;
		this.pendingScrollAnchor = null;
		this.displayedSearchQuery = RecipeSearchQuery.parse("");
		this.searchIngredientMatcher = (ingredient, token) -> true;
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
			recipeTransferService
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
			recipeTransferService
		);
		if (state.getRecipeCategories().isEmpty()) {
			return false;
		}

		setState(state, true);

		return true;
	}

	@Override
	public Stream<Consumer<IIngredientAcceptor<?>>> getRecipeCatalystGroups() {
		if (!hasRecipeResults()) {
			return Stream.empty();
		}
		IRecipeCategory<?> category = getSelectedRecipeCategory();
		RecipeType<?> recipeType = category.getRecipeType();
		return recipeManager.createRecipeCatalystLookup(recipeType)
			.getGroups();
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
		ImmutableSize2i availableSize,
		@Nullable AbstractContainerMenu container,
		BookmarkList bookmarkList,
		RecipesGui recipesGui
	) {
		IRecipeCategory<?> recipeCategory = getSelectedRecipeCategory();

		IClientConfigs jeiClientConfigs = Internal.getClientConfigs();
		IClientConfig clientConfig = jeiClientConfigs.getClientConfig();
		Set<RecipeSorterStage> recipeSorterStages = RecipeSorterStage.getEnabled(clientConfig);

		int containerId = -1;
		if (container != null) {
			containerId = container.containerId;
		}
		if (!recipeSorterStages.equals(cachedSorterStages) ||
			this.cachedRecipeLayoutsWithButtons == null ||
			this.cachedRecipeCategory != recipeCategory ||
			this.cachedContainerId != containerId
		) {
			IFocusedRecipes<?> focusedRecipes = this.state.getFocusedRecipes();

			this.cachedRecipeLayoutsWithButtons = IRecipeLayoutList.create(
				recipeSorterStages,
				container,
				focusedRecipes,
				state.getFocuses(),
				bookmarkList,
				recipeManager,
				recipeTransferService,
				recipesGui
			);
			this.cachedRecipeCategory = recipeCategory;
			this.cachedSorterStages = Set.copyOf(recipeSorterStages);
			this.cachedContainerId = containerId;
		}

		ImmutableSize2i recipeSizeWithButtons = this.cachedRecipeLayoutsWithButtons.findFirst()
			.map(layout -> new ImmutableSize2i(layout.totalWidth(), layout.getRecipeLayout().getRectWithBorder().getHeight()))
			.orElseGet(() -> new ImmutableSize2i(recipeCategory.getWidth(), recipeCategory.getHeight()));

		this.recipeGuiGrid = RecipeGuiGrid.calculate(availableSize, recipeSizeWithButtons, clientConfig.maxRecipeGuiColumns().get());
		this.state.setRecipesPerPage(recipeGuiGrid.recipesPerPage());

		boolean scrolling = isScrolling();
		RecipeGuiScrollState scrollState = getScrollState();
		scrollState.update(recipeGuiGrid.columns(), recipeSizeWithButtons.height(), cachedRecipeLayoutsWithButtons.size(), availableSize.height());
		if (this.scrolling != scrolling) {
			if (scrolling) {
				int pageStart = state.getRecipeIndex() - state.getRecipeIndex() % state.getRecipesPerPage();
				scrollState.scrollToRecipe(pageStart);
			} else {
				state.setRecipeIndex(scrollState.getFirstRecipeIndex());
			}
			this.scrolling = scrolling;
		}
		if (pendingScrollAnchor != null) {
			if (scrolling) {
				scrollState.scrollToRecipeAnchor(pendingScrollAnchor);
			}
			pendingScrollAnchor = null;
		}
		if (scrolling) {
			return this.cachedRecipeLayoutsWithButtons.subList(scrollState.getFirstRecipeIndex(), scrollState.getEndRecipeIndex());
		}
		return this.state.getVisible(this.cachedRecipeLayoutsWithButtons);
	}

	@Override
	public boolean isScrolling() {
		return Internal.getClientConfigs().getClientConfig().recipeGuiNavigationMode().get() == RecipeGuiNavigationMode.SCROLLING;
	}

	@Override
	public RecipeGuiScrollState getScrollState() {
		return state.getScrollState();
	}

	@Override
	public boolean scrollRecipes(double pixels) {
		if (getScrollState().scroll(pixels)) {
			stateListener.onStateChange();
			return true;
		}
		return false;
	}

	@Override
	public int getVisibleScrollAmount() {
		return getScrollState().getVisibleHeight();
	}

	@Override
	public int getHiddenScrollAmount() {
		return getScrollState().getMaxScroll();
	}

	@Override
	public float getScrollOffsetY() {
		return getScrollState().getScrollOffset();
	}

	@Override
	public void setScrollOffsetY(float scrollOffsetY) {
		if (getScrollState().setScrollOffset(scrollOffsetY)) {
			stateListener.onStateChange();
		}
	}

	@Override
	public RecipeGuiGrid getRecipeGuiGrid() {
		return recipeGuiGrid;
	}

	@Override
	public boolean nextRecipeCategory() {
		if (state.nextRecipeCategory()) {
			stateListener.onStateChange();
			return true;
		}
		return false;
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
	public boolean previousRecipeCategory() {
		if (state.previousRecipeCategory()) {
			stateListener.onStateChange();
			return true;
		}
		return false;
	}

	@Override
	public void goToFirstPage() {
		state.goToFirstPage();
		stateListener.onStateChange();
	}

	@Override
	public boolean nextPage() {
		if (isScrolling()) {
			return scrollRecipes(getScrollState().getVisibleHeight());
		}
		if (state.nextPage()) {
			stateListener.onStateChange();
			return true;
		}
		return false;
	}

	@Override
	public boolean previousPage() {
		if (isScrolling()) {
			return scrollRecipes(-getScrollState().getVisibleHeight());
		}
		if (state.previousPage()) {
			stateListener.onStateChange();
			return true;
		}
		return false;
	}

	@Override
	public String getPageString() {
		return (getPageIndex() + 1) + "/" + getPageCount();
	}

	@Override
	public int getPageIndex() {
		int firstRecipeIndex;
		if (scrolling) {
			firstRecipeIndex = state.getScrollState().getFirstRecipeIndex();
		} else {
			firstRecipeIndex = state.getRecipeIndex();
		}
		return firstRecipeIndex / state.getRecipesPerPage();
	}

	@Override
	public int getPageCount() {
		return state.pageCount();
	}

	@Override
	public List<IFocus<?>> getFocuses() {
		return List.copyOf(state.getFocuses().getAllFocuses());
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

		return unfilteredState.getRecipeCategories().size() == categoryCount;
	}

}
