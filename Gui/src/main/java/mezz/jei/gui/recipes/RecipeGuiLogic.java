package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IJeiClientConfigs;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.favorites.preferences.RecipePreferenceRules;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistory;
import mezz.jei.gui.recipes.filtering.RecipeFilterMode;
import mezz.jei.gui.recipes.filtering.RecipeLookupSnapshot;
import mezz.jei.gui.recipes.filtering.RecipeLookupSnapshotFactory;
import mezz.jei.gui.recipes.filtering.RecipeSearchQuery;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.IngredientLookupState;
import mezz.jei.gui.recipes.lookups.ProjectedLookupState;
import mezz.jei.gui.recipes.lookups.SingleCategoryLookupState;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RecipeGuiLogic implements IRecipeGuiLogic {
	private final IRecipeManager recipeManager;
	private final IIngredientManager ingredientManager;
	private final IRecipeTransferManager recipeTransferManager;
	private final IRecipeLogicStateListener stateListener;
	private final Supplier<RecipePreferenceRules> preferenceRulesSupplier;
	private final RecipeLookupSnapshotFactory snapshotFactory;

	private boolean initialState = true;
	private ILookupState state;
	private ILookupState unfilteredState;
	private final Stack<ILookupState> stateHistory = new Stack<>();
	private final LookupHistory lookupHistory;
	private final IFocusFactory focusFactory;
	private final BookmarkFactory bookmarkFactory;
	private @Nullable IRecipeCategory<?> cachedRecipeCategory;
	private @Nullable IRecipeLayoutList cachedRecipeLayoutsWithButtons;
	private int cachedContainerId = -1;
	private Set<RecipeSorterStage> cachedSorterStages = Set.of();
	private RecipeFilterMode filterMode = RecipeFilterMode.ALL;
	private RecipeSearchQuery searchQuery = RecipeSearchQuery.parse("");
	private @Nullable RecipeLookupSnapshot snapshot;
	private @Nullable RecipePreferenceRules snapshotPreferenceRules;

	public RecipeGuiLogic(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		LookupHistory lookupHistory,
		IRecipeTransferManager recipeTransferManager,
		IRecipeLogicStateListener stateListener,
		IFocusFactory focusFactory,
		BookmarkFactory bookmarkFactory
	) {
		this(
			recipeManager,
			ingredientManager,
			lookupHistory,
			recipeTransferManager,
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
		IRecipeTransferManager recipeTransferManager,
		IRecipeLogicStateListener stateListener,
		IFocusFactory focusFactory,
		BookmarkFactory bookmarkFactory,
		Supplier<RecipePreferenceRules> preferenceRulesSupplier
	) {
		this.recipeManager = recipeManager;
		this.ingredientManager = ingredientManager;
		this.lookupHistory = lookupHistory;
		this.recipeTransferManager = recipeTransferManager;
		this.stateListener = stateListener;
		this.preferenceRulesSupplier = preferenceRulesSupplier;
		this.snapshotFactory = new RecipeLookupSnapshotFactory(recipeManager, ingredientManager);
		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.get()
			.toList();
		this.unfilteredState = IngredientLookupState.create(
			recipeManager,
			focusFactory.getEmptyFocusGroup(),
			recipeCategories,
			recipeTransferManager
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
			IngredientBookmark<?> ingredientBookmark = bookmarkFactory.create(focus.getTypedValue());
			this.lookupHistory.add(ingredientBookmark);
		}

		return setState(state, true);
	}

	@Override
	public boolean showRecipes(IFocusedRecipes<?> focusedRecipes, IFocusGroup focuses) {
		var recipeBookmark = createRecipeBookmark(recipeManager, ingredientManager, focusedRecipes, focuses);
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
		T recipe = recipes.getFirst();
		return recipeManager.createRecipeLayoutDrawable(recipeCategory, recipe, focusGroup)
			.map(drawable -> RecipeBookmark.create(drawable, ingredientManager))
			.orElse(null);
	}

	@Override
	public boolean back() {
		if (stateHistory.empty()) {
			return false;
		}
		final ILookupState state = stateHistory.pop();
		setState(state, false);
		return true;
	}

	@Override
	public void clearHistory() {
		while (!stateHistory.empty()) {
			stateHistory.pop();
		}
	}

	private boolean setState(ILookupState state, boolean saveHistory) {
		List<IRecipeCategory<?>> recipeCategories = state.getRecipeCategories();
		if (recipeCategories.isEmpty()) {
			return false;
		}

		if (saveHistory && !initialState) {
			stateHistory.push(this.unfilteredState);
		}
		this.unfilteredState = state;
		this.initialState = false;
		this.snapshot = null;
		this.snapshotPreferenceRules = null;
		rebuildDisplayedState();
		clearLayoutCache();
		stateListener.onStateChange();
		return true;
	}

	private void rebuildDisplayedState() {
		IRecipeCategory<?> selectedCategory = this.state.getFocusedRecipes().getRecipeCategory();
		if (filterMode == RecipeFilterMode.ALL && searchQuery.isEmpty()) {
			this.snapshot = null;
			this.snapshotPreferenceRules = null;
			this.state = unfilteredState;
			this.state.moveToRecipeCategory(selectedCategory);
			return;
		}

		RecipePreferenceRules preferenceRules = filterMode == RecipeFilterMode.ALL ?
			null :
			preferenceRulesSupplier.get();
		if (snapshot == null || snapshotPreferenceRules != preferenceRules) {
			this.snapshot = preferenceRules == null ?
				snapshotFactory.create(unfilteredState) :
				snapshotFactory.create(unfilteredState, preferenceRules);
			this.snapshotPreferenceRules = preferenceRules;
		}
		this.state = new ProjectedLookupState(
			unfilteredState,
			snapshot.project(filterMode, searchQuery)
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
		this.searchQuery = RecipeSearchQuery.parse(query);
		rebuildDisplayedState();
		clearLayoutCache();
		stateListener.onStateChange();
	}

	@Override
	public void clearRecipeResultSnapshot() {
		this.snapshot = null;
		this.snapshotPreferenceRules = null;
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
		@Nullable AbstractContainerMenu container,
		BookmarkList bookmarkList,
		RecipesGui recipesGui
	) {
		IRecipeCategory<?> recipeCategory = getSelectedRecipeCategory();

		IJeiClientConfigs jeiClientConfigs = Internal.getJeiClientConfigs();
		IClientConfig clientConfig = jeiClientConfigs.getClientConfig();
		Set<RecipeSorterStage> recipeSorterStages = RecipeSorterStage.getEnabled(clientConfig);

		int containerId = container == null ? -1 : container.containerId;
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
				recipesGui
			);
			this.cachedRecipeCategory = recipeCategory;
			this.cachedSorterStages = Set.copyOf(recipeSorterStages);
			this.cachedContainerId = containerId;
		}

		final int recipeHeight =
			this.cachedRecipeLayoutsWithButtons.findFirst()
				.map(IRecipeLayoutWithButtons::getRecipeLayout)
				.map(IRecipeLayoutDrawable::getRectWithBorder)
				.map(Rect2i::getHeight)
				.orElseGet(recipeCategory::getHeight);

		final int recipesPerPage = Math.max(1, 1 + ((availableHeight - recipeHeight) / (recipeHeight + minRecipePadding)));
		this.state.setRecipesPerPage(recipesPerPage);

		return this.state.getVisible(this.cachedRecipeLayoutsWithButtons);
	}

	@Override
	public int getRecipesPerPage() {
		return this.state.getRecipesPerPage();
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
		if (state.nextPage()) {
			stateListener.onStateChange();
			return true;
		}
		return false;
	}

	@Override
	public boolean previousPage() {
		if (state.previousPage()) {
			stateListener.onStateChange();
			return true;
		}
		return false;
	}

	@Override
	public String getPageString() {
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

		return unfilteredState.getRecipeCategories().size() == categoryCount;
	}

}
