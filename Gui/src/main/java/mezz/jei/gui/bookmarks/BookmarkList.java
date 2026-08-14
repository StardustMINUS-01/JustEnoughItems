package mezz.jei.gui.bookmarks;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.favorites.FavoriteTreeBookmarkEntryResolver;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class BookmarkList implements IIngredientGridSource {
	private final List<IBookmark> bookmarksList = new LinkedList<>();
	private final Set<IBookmark> bookmarksSet = new HashSet<>();

	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IIngredientManager ingredientManager;
	private final RegistryAccess registryAccess;
	private final IBookmarkConfig bookmarkConfig;
	private final IClientConfig clientConfig;
	private final IGuiHelper guiHelper;
	private final List<SourceListChangedListener> listeners = new ArrayList<>();

	public BookmarkList(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess,
		IBookmarkConfig bookmarkConfig,
		IClientConfig clientConfig,
		IGuiHelper guiHelper
	) {
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.ingredientManager = ingredientManager;
		this.registryAccess = registryAccess;
		this.bookmarkConfig = bookmarkConfig;
		this.clientConfig = clientConfig;
		this.guiHelper = guiHelper;
	}

	public boolean add(IBookmark value) {
		if (!addToListWithoutNotifying(value, clientConfig.isAddingBookmarksToFrontEnabled())) {
			return false;
		}
		notifyListenersOfChange();
		bookmarkConfig.saveBookmarks(recipeManager, focusFactory, guiHelper, ingredientManager, registryAccess, bookmarksList);
		return true;
	}

	public void moveBookmark(IBookmark previousBookmark, IBookmark newBookmark, int offset) {
		if (!bookmarksSet.contains(newBookmark) || !bookmarksSet.contains(previousBookmark)) {
			return;
		}
		int i = bookmarksList.indexOf(previousBookmark);
		int j = bookmarksList.indexOf(newBookmark);
		int newIndex = i + offset;
		if (newIndex == j) {
			return;
		}

		if (newIndex < 0) {
			newIndex += bookmarksList.size();
		}
		newIndex %= bookmarksList.size();

		bookmarksList.remove(newBookmark);
		bookmarksList.add(newIndex, newBookmark);

		notifyListenersOfChange();
		bookmarkConfig.saveBookmarks(recipeManager, focusFactory, guiHelper, ingredientManager, registryAccess, bookmarksList);
	}

	public boolean contains(IBookmark value) {
		return this.bookmarksSet.contains(value);
	}

	public <T> boolean onElementBookmarked(IElement<T> element, UserInput input, BookmarkOverlay bookmarkOverlay) {
		if (bookmarkOverlay.isMouseOver(input.getMouseX(), input.getMouseY())) {
			return element.getBookmark()
				.map(this::remove)
				.orElse(false);
		}

		ITypedIngredient<T> ingredient = element.getTypedIngredient();
		IBookmark bookmark = IngredientBookmark.create(ingredient, ingredientManager);
		return add(bookmark);
	}

	public <T> boolean addIngredientBookmark(ITypedIngredient<T> ingredient) {
		IBookmark bookmark = IngredientBookmark.create(ingredient, ingredientManager);
		return add(bookmark);
	}

	public void toggleBookmark(IBookmark bookmark) {
		if (remove(bookmark)) {
			return;
		}
		add(bookmark);
	}

	public boolean remove(IBookmark ingredient) {
		if (!bookmarksSet.remove(ingredient)) {
			return false;
		}
		bookmarksList.remove(ingredient);

		notifyListenersOfChange();
		bookmarkConfig.saveBookmarks(recipeManager, focusFactory, guiHelper, ingredientManager, registryAccess, bookmarksList);
		return true;
	}

	public boolean addToListWithoutNotifying(IBookmark value, boolean addToFront) {
		if (contains(value)) {
			return false;
		}
		if (addToFront) {
			bookmarksList.add(0, value);
			bookmarksSet.add(value);
		} else {
			bookmarksList.add(value);
			bookmarksSet.add(value);
		}
		return true;
	}

	/**
	 * The 1.20.1 port of the JEI 1.21.1 favorite-tree bookmark writer. The
	 * tree's recipe layouts are extracted into slot ingredients (mirroring
	 * 1.21.1 {@code createRecipeBookmarkEntries}), resolved into one group per
	 * recipe by {@link FavoriteTreeBookmarkEntryResolver}, and written
	 * contiguously as {@link RecipeBookmark}s whose amounts are aggregated per
	 * unique ingredient across all slots of the recipe (1.21.1
	 * {@code mergeRecipeInputs} semantics). A non-empty {@link Optional} is
	 * returned as a success sentinel so the caller knows the save succeeded.
	 */
	public Optional<String> addRecipeLayoutProjectionBookmarkGroup(
		List<RecipeLayoutProjection> recipeLayouts,
		boolean preserveAmount
	) {
		if (recipeLayouts.isEmpty()) {
			return Optional.empty();
		}
		List<RecipeTreeSlotIngredient> slots = new ArrayList<>();
		Map<ResourceLocation, IRecipeLayoutDrawable<?>> layoutByRecipeUid = new LinkedHashMap<>();
		for (RecipeLayoutProjection projection : recipeLayouts) {
			IRecipeLayoutDrawable<?> layout = projection.layout();
			ResourceLocation recipeUid = getRecipeUid(layout);
			if (recipeUid == null) {
				continue;
			}
			layoutByRecipeUid.putIfAbsent(recipeUid, layout);
			addSlotIngredients(slots, projection, recipeUid);
		}
		List<RecipeTreeBookmarkGroup> groups = FavoriteTreeBookmarkEntryResolver.resolve(slots, ingredientManager);
		boolean added = false;
		for (RecipeTreeBookmarkGroup group : groups) {
			for (RecipeTreeBookmarkEntry entry : group.entries()) {
				IRecipeLayoutDrawable<?> layout = layoutByRecipeUid.get(entry.recipeUid());
				if (layout == null) {
					continue;
				}
				RecipeBookmark<?, ?> recipeBookmark = createRecipeBookmark(layout, entry, preserveAmount);
				if (recipeBookmark != null && addToListWithoutNotifying(recipeBookmark, clientConfig.isAddingBookmarksToFrontEnabled())) {
					added = true;
				}
			}
		}
		if (!added) {
			return Optional.empty();
		}
		notifyListenersOfChange();
		bookmarkConfig.saveBookmarks(recipeManager, focusFactory, guiHelper, ingredientManager, registryAccess, bookmarksList);
		return Optional.of("favorite-tree");
	}

	@Nullable
	private static <R> ResourceLocation getRecipeUid(IRecipeLayoutDrawable<R> layout) {
		IRecipeCategory<R> recipeCategory = layout.getRecipeCategory();
		R recipe = layout.getRecipe();
		return recipeCategory.getRegistryName(recipe);
	}

	/**
	 * Extracts one {@link RecipeTreeSlotIngredient} per recipe slot, matching
	 * the 1.21.1 {@code addSlotBookmarks} slot selection: output slots use the
	 * projected output key, input slots prefer the projected input key and
	 * fall back to the slot's first ingredient. Output slots come first, then
	 * input slots, matching 1.21.1 {@code createRecipeBookmarkEntries}.
	 */
	private void addSlotIngredients(
		List<RecipeTreeSlotIngredient> slots,
		RecipeLayoutProjection projection,
		ResourceLocation recipeUid
	) {
		IRecipeLayoutDrawable<?> layout = projection.layout();
		IRecipeSlotsView recipeSlotsView = layout.getRecipeSlotsView();
		addSlotIngredients(slots, recipeSlotsView, projection, recipeUid, RecipeIngredientRole.OUTPUT);
		addSlotIngredients(slots, recipeSlotsView, projection, recipeUid, RecipeIngredientRole.INPUT);
	}

	private void addSlotIngredients(
		List<RecipeTreeSlotIngredient> slots,
		IRecipeSlotsView recipeSlotsView,
		RecipeLayoutProjection projection,
		ResourceLocation recipeUid,
		RecipeIngredientRole role
	) {
		List<IRecipeSlotView> roleSlots = recipeSlotsView.getSlotViews(role);
		for (int i = 0; i < roleSlots.size(); i++) {
			IRecipeSlotView slotView = roleSlots.get(i);
			Optional<ITypedIngredient<?>> ingredient;
			if (role == RecipeIngredientRole.OUTPUT && projection.selectedOutputKey().isPresent()) {
				ingredient = getSelectedIngredient(slotView, projection.selectedOutputKey().get());
			} else if (role == RecipeIngredientRole.INPUT) {
				ingredient = projection.selectedInputKey(i)
					.flatMap(key -> getSelectedIngredient(slotView, key))
					.or(() -> slotView.getAllIngredients().findFirst());
			} else {
				ingredient = slotView.getAllIngredients().findFirst();
			}
			ingredient.ifPresent(value -> slots.add(new RecipeTreeSlotIngredient(recipeUid, role, value)));
		}
	}

	private Optional<ITypedIngredient<?>> getSelectedIngredient(
		IRecipeSlotView slotView,
		BookmarkIngredientKey selectedKey
	) {
		if (selectedKey == null) {
			return Optional.empty();
		}
		return slotView.getAllIngredients()
			.filter(ingredient -> selectedKey.equals(createPermutationKey(ingredient)))
			.findFirst();
	}

	private BookmarkIngredientKey createPermutationKey(ITypedIngredient<?> ingredient) {
		@SuppressWarnings("unchecked")
		IIngredientHelper<Object> ingredientHelper = (IIngredientHelper<Object>) ingredientManager.getIngredientHelper(ingredient.getType());
		String uniqueId = ingredientHelper.getUniqueId(ingredient.getIngredient(), UidContext.Ingredient);
		return BookmarkIngredientKey.of(ingredient.getType().getUid(), uniqueId);
	}

	@Nullable
	@SuppressWarnings({"unchecked", "rawtypes"})
	private <R> RecipeBookmark<R, ?> createRecipeBookmark(
		IRecipeLayoutDrawable<R> layout,
		RecipeTreeBookmarkEntry entry,
		boolean preserveAmount
	) {
		IRecipeCategory<R> recipeCategory = layout.getRecipeCategory();
		R recipe = layout.getRecipe();
		ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
		if (recipeUid == null) {
			return null;
		}
		ITypedIngredient<?> bookmarkIngredient = preserveAmount ?
			entry.ingredient() :
			ingredientManager.normalizeTypedIngredient((ITypedIngredient) entry.ingredient());
		return new RecipeBookmark<>(
			recipeCategory,
			recipe,
			recipeUid,
			(ITypedIngredient) bookmarkIngredient,
			entry.role(),
			entry.amount()
		);
	}

	@Override
	public List<IElement<?>> getElements() {
		return bookmarksList.stream()
			.<IElement<?>>map(IBookmark::getElement)
			.toList();
	}

	@Nullable
	public <R> RecipeBookmark<R, ?> getMatchingBookmark(RecipeType<R> recipeType, R recipe) {
		for (IBookmark bookmark : bookmarksList) {
			if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark && recipeBookmark.isRecipe(recipeType, recipe)) {
				@SuppressWarnings("unchecked")
				RecipeBookmark<R, ?> castBookmark = (RecipeBookmark<R, ?>) recipeBookmark;
				return castBookmark;
			}
		}
		return null;
	}

	public boolean isEmpty() {
		return bookmarksSet.isEmpty();
	}

	@Override
	public void addSourceListChangedListener(SourceListChangedListener listener) {
		listeners.add(listener);
	}

	public void notifyListenersOfChange() {
		for (SourceListChangedListener listener : listeners) {
			listener.onSourceListChanged();
		}
	}
}
