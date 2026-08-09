package mezz.jei.gui.bookmarks;

import com.mojang.serialization.Codec;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import mezz.jei.gui.compat.gtm.GtmVirtualCircuitCompat;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.LayoutPlaceholderElement;
import mezz.jei.gui.overlay.elements.ProjectedBookmarkElement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class BookmarkList implements IIngredientGridSource {
	private final List<IBookmark> bookmarksList = new LinkedList<>();
	private final Set<IBookmark> bookmarksSet = new HashSet<>();
	private final BookmarkGroupManager<IBookmark> bookmarkGroups = new BookmarkGroupManager<>();

	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IIngredientManager ingredientManager;
	private final RegistryAccess registryAccess;
	private final IBookmarkConfig bookmarkConfig;
	private final IClientConfig clientConfig;
	private final IGuiHelper guiHelper;
	private final @Nullable ICodecHelper codecHelper;
	private final @Nullable Codec<IBookmark> bookmarkCodec;
	private final @Nullable BookmarkFactory bookmarkFactory;
	private final FocusedRecipeLayoutResolver focusedRecipeLayoutResolver;
	private final Function<BookmarkIngredientKey, Optional<FocusedRecipe>> preferredRecipeLookup;
	private final List<SourceListChangedListener> listeners = new ArrayList<>();
	private long changeVersion;
	private long cachedDisplaySlotsVersion = -1;
	private int cachedDisplaySlotsColumns = -1;
	private List<Integer> cachedDisplaySlotsPerRow = List.of();
	private List<BookmarkDisplaySlot<IBookmark>> cachedDisplaySlots = List.of();
	private int latestDisplaySlotsColumns = 0;

	public BookmarkList(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess,
		IBookmarkConfig bookmarkConfig,
		IClientConfig clientConfig,
		IGuiHelper guiHelper
	) {
		this(
			recipeManager,
			focusFactory,
			ingredientManager,
			registryAccess,
			bookmarkConfig,
			clientConfig,
			guiHelper,
			key -> Optional.empty(),
			null,
			null,
			null
		);
	}

	public BookmarkList(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess,
		IBookmarkConfig bookmarkConfig,
		IClientConfig clientConfig,
		IGuiHelper guiHelper,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> preferredRecipeLookup
	) {
		this(
			recipeManager,
			focusFactory,
			ingredientManager,
			registryAccess,
			bookmarkConfig,
			clientConfig,
			guiHelper,
			preferredRecipeLookup,
			null,
			null,
			null
		);
	}

	public BookmarkList(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager,
		RegistryAccess registryAccess,
		IBookmarkConfig bookmarkConfig,
		IClientConfig clientConfig,
		IGuiHelper guiHelper,
		Function<BookmarkIngredientKey, Optional<FocusedRecipe>> preferredRecipeLookup,
		@Nullable ICodecHelper codecHelper,
		@Nullable Codec<IBookmark> bookmarkCodec,
		@Nullable BookmarkFactory bookmarkFactory
	) {
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.ingredientManager = ingredientManager;
		this.registryAccess = registryAccess;
		this.bookmarkConfig = bookmarkConfig;
		this.clientConfig = clientConfig;
		this.guiHelper = guiHelper;
		this.codecHelper = codecHelper;
		this.bookmarkCodec = bookmarkCodec;
		this.bookmarkFactory = bookmarkFactory;
		this.focusedRecipeLayoutResolver = new FocusedRecipeLayoutResolver(recipeManager);
		this.preferredRecipeLookup = preferredRecipeLookup;
	}

	public boolean add(IBookmark value) {
		if (!addToListWithoutNotifying(value, clientConfig.addBookmarksToFrontEnabled().getValue())) {
			return false;
		}
		notifyListenersOfChange();
		saveBookmarks();
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
		bookmarkGroups.moveItemToGroup(newBookmark, bookmarkGroups.getGroupId(previousBookmark));
		keepRecipeBlocksContiguous(bookmarkGroups.getGroupId(newBookmark));

		notifyListenersOfChange();
		saveBookmarks();
	}

	void moveBookmarks(List<IBookmark> bookmarks, IBookmark targetBookmark, String targetGroupId, int offset) {
		List<IBookmark> movingBookmarks = bookmarks.stream()
			.filter(bookmarksSet::contains)
			.distinct()
			.toList();
		if (movingBookmarks.isEmpty() ||
			movingBookmarks.contains(targetBookmark) ||
			!bookmarksSet.contains(targetBookmark)) {
			return;
		}

		Set<String> sourceGroupIds = collectSourceGroupIds(movingBookmarks);
		bookmarksList.removeAll(movingBookmarks);
		int targetIndex = bookmarksList.indexOf(targetBookmark);
		List<IBookmark> placedBookmarks = new ArrayList<>();
		boolean changed = false;
		for (IBookmark bookmark : movingBookmarks) {
			String previousGroupId = bookmarkGroups.getGroupId(bookmark);
			if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(targetGroupId)) {
				if (releaseBookmarkToDefault(bookmark)) {
					placedBookmarks.add(bookmark);
				}
				changed = changed || !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(previousGroupId);
			} else if (!isDuplicateInGroup(bookmark, targetGroupId)) {
				bookmarkGroups.moveItemToGroup(bookmark, targetGroupId);
				ensureRecipeBookmarkScope(bookmark);
				placedBookmarks.add(bookmark);
				changed = true;
			}
		}
		if (targetIndex < 0) {
			bookmarksList.addAll(placedBookmarks);
		} else {
			int insertionIndex = Math.max(0, Math.min(bookmarksList.size(), targetIndex + offset));
			bookmarksList.addAll(insertionIndex, placedBookmarks);
		}
		keepRecipeBlocksContiguous(targetGroupId);
		if (changed) {
			cleanupAfterGroupChange(sourceGroupIds);
		}

		notifyListenersOfChange();
		saveBookmarks();
	}

	public boolean contains(IBookmark value) {
		return this.bookmarksSet.contains(value);
	}

	public <T> boolean onElementBookmarked(IElement<T> element, UserInput input, BookmarkOverlay bookmarkOverlay) {
		if (bookmarkOverlay.isMouseOver(input.getMouseX(), input.getMouseY())) {
			return element.getBookmark()
				.map(this::removeBookmarkFromOverlay)
				.orElse(false);
		}

		if (InputModifiers.hasShift(input)) {
			BookmarkHotkeyAction action = InputModifiers.hasControl(input) ?
				BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK_WITH_COUNT :
				BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK;
			return onElementBookmarked(element, action);
		}

		BookmarkHotkeyAction action = InputModifiers.hasControl(input) ?
			BookmarkHotkeyAction.ADD_BOOKMARK_WITH_COUNT :
			BookmarkHotkeyAction.ADD_BOOKMARK;
		return onElementBookmarked(element, action);
	}

	private boolean removeBookmarkFromOverlay(IBookmark bookmark) {
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		if (metadata.recipeUid() != null && metadata.type().isRecipeAssociated()) {
			return removeRecipeBookmark(bookmark, false);
		}
		return remove(bookmark);
	}

	public <T> boolean onElementBookmarked(IElement<T> element, BookmarkHotkeyAction action) {
		return switch (action) {
			case ADD_BOOKMARK -> addIngredientBookmark(element, false);
			case ADD_BOOKMARK_WITH_COUNT -> addIngredientBookmark(element, true);
			case ADD_RECIPE_BOOKMARK -> addRecipeBookmarkForElement(element, false);
			case ADD_RECIPE_BOOKMARK_WITH_COUNT -> addRecipeBookmarkForElement(element, true);
			case REMOVE_ITEM_BOOKMARK -> element.getBookmark()
				.map(this::remove)
				.orElse(false);
			case REMOVE_RECIPE_BOOKMARK -> element.getBookmark()
				.map(bookmark -> removeRecipeBookmark(bookmark, false))
				.orElse(false);
			default -> false;
		};
	}

	private <T> boolean addIngredientBookmark(IElement<T> element, boolean preserveAmount) {
		ITypedIngredient<T> ingredient = element.getTypedIngredient();
		return addIngredientBookmark(ingredient, preserveAmount);
	}

	public <T> boolean addIngredientBookmark(ITypedIngredient<T> ingredient, boolean preserveAmount) {
		if (ingredientManager == null) {
			return false;
		}
		IBookmark bookmark = preserveAmount ?
			IngredientBookmark.createPreservingAmount(ingredient, ingredientManager) :
			IngredientBookmark.create(ingredient, ingredientManager);
		return add(bookmark);
	}

	public <T> boolean addRecipeBookmarkForElement(IElement<T> element) {
		return addRecipeBookmarkForElement(element, false);
	}

	public <T> boolean addRecipeBookmarkForElement(IElement<T> element, boolean preserveAmount) {
		if (recipeManager == null || focusFactory == null || ingredientManager == null) {
			return false;
		}

		ITypedIngredient<T> ingredient = element.getTypedIngredient();
		IFocus<T> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, ingredient);
		List<IFocus<?>> focuses = List.of(focus);
		if (addPreferredRecipeBookmarks(ingredient, focuses, preserveAmount)) {
			return true;
		}
		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.limitFocus(focuses)
			.get()
			.toList();

		for (IRecipeCategory<?> recipeCategory : recipeCategories) {
			if (addFirstRecipeBookmarks(recipeCategory, focuses, preserveAmount)) {
				return true;
			}
		}
		return false;
	}

	private <T> boolean addPreferredRecipeBookmarks(ITypedIngredient<T> ingredient, List<IFocus<?>> focuses, boolean preserveAmount) {
		BookmarkIngredientKey target = BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager);
		return preferredRecipeLookup.apply(target)
			.flatMap(recipe -> createRecipeLayoutDrawable(recipe, focuses))
			.map(layout -> addRecipeBookmarks(layout, preserveAmount))
			.orElse(false);
	}

	private Optional<IRecipeLayoutDrawable<?>> createRecipeLayoutDrawable(FocusedRecipe focusedRecipe, List<IFocus<?>> focuses) {
		if (focusFactory == null) {
			return Optional.empty();
		}
		return focusedRecipeLayoutResolver.resolve(focusedRecipe, focusFactory.createFocusGroup(focuses));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private boolean addFirstRecipeBookmarks(IRecipeCategory recipeCategory, List<IFocus<?>> focuses, boolean preserveAmount) {
		RecipeType recipeType = recipeCategory.getRecipeType();
		Optional<?> recipe = recipeManager.createRecipeLookup(recipeType)
			.limitFocus(focuses)
			.get()
			.findFirst();
		if (recipe.isEmpty()) {
			return false;
		}

		IFocusGroup focusGroup = focusFactory.createFocusGroup(focuses);
		Optional<IRecipeLayoutDrawable> recipeLayout = recipeManager.createRecipeLayoutDrawable(recipeCategory, recipe.get(), focusGroup)
			.map(layout -> (IRecipeLayoutDrawable) layout);
		return recipeLayout
			.map(layout -> addRecipeBookmarks(layout, preserveAmount))
			.orElse(false);
	}

	public <T> boolean addIngredientBookmark(ITypedIngredient<T> ingredient) {
		return addIngredientBookmark(ingredient, false);
	}

	public void toggleBookmark(IBookmark bookmark) {
		if (remove(bookmark)) {
			return;
		}
		add(bookmark);
	}

	public boolean toggleRecipeBookmark(IRecipeLayoutDrawable<?> recipeLayout, boolean preserveAmount) {
		if (ingredientManager == null) {
			return false;
		}
		Optional<RecipeBookmarkEntry> entry = createPrimaryRecipeBookmarkEntry(recipeLayout, preserveAmount);
		if (entry.isEmpty()) {
			return false;
		}

		Optional<IBookmark> existingRecipeBookmark = findRecipeBookmark(entry.get().metadata());
		if (existingRecipeBookmark.isPresent()) {
			return removeRecipeBookmark(existingRecipeBookmark.get(), true);
		}

		return addRecipeBookmarks(recipeLayout, preserveAmount);
	}

	public boolean remove(IBookmark ingredient) {
		if (!bookmarksSet.remove(ingredient)) {
			return false;
		}
		removeBookmarkWithoutNotifying(ingredient);
		removeEmptyGroupsWithoutNotifying();

		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean removeRecipeBookmark(IBookmark bookmark) {
		return removeRecipeBookmark(bookmark, true);
	}

	public boolean removeRecipeBookmark(IBookmark bookmark, boolean removeFullRecipe) {
		if (!bookmarksSet.contains(bookmark)) {
			return false;
		}
		BookmarkItemMetadata targetMetadata = bookmarkGroups.getItemMetadata(bookmark);
		ResourceLocation recipeUid = targetMetadata.recipeUid();
		if (recipeUid == null || !targetMetadata.type().isRecipeAssociated()) {
			return remove(bookmark);
		}
		String groupId = targetMetadata.groupId();
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.filter(BookmarkGroup::craftingMode).isPresent()) {
			Set<ResourceLocation> relatedRecipes = getRelatedRecipeIds(groupId, recipeUid);
			if (!relatedRecipes.isEmpty()) {
				if (!removeFullRecipe) {
					return true;
				}
				return removeRecipes(groupId, relatedRecipes);
			}
		}
		if (!removeFullRecipe &&
			targetMetadata.type().isGraphOutput() &&
			isLastResultForRecipe(bookmark, targetMetadata)) {
			removeFullRecipe = true;
		}
		if (!removeFullRecipe) {
			removeBookmarkWithoutNotifying(bookmark);
			normalizeIncompleteRecipes(groupId);
			removeEmptyGroupsWithoutNotifying();
			pruneCollapsedRecipeIds(groupId);
			notifyListenersOfChange();
			saveBookmarks();
			return true;
		}
		return removeRecipes(groupId, Set.of(recipeUid));
	}

	private boolean removeRecipes(String groupId, Set<ResourceLocation> recipeUids) {
		List<IBookmark> removedBookmarks = bookmarksList.stream()
			.filter(candidate -> {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(candidate);
				return groupId.equals(metadata.groupId()) &&
					metadata.recipeUid() != null &&
					recipeUids.contains(metadata.recipeUid());
			})
			.toList();
		if (removedBookmarks.isEmpty()) {
			return false;
		}
		for (IBookmark removedBookmark : removedBookmarks) {
			removeBookmarkWithoutNotifying(removedBookmark);
		}
		removeEmptyGroupsWithoutNotifying();
		pruneCollapsedRecipeIds(groupId);

		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	private Set<ResourceLocation> getRelatedRecipeIds(String groupId, ResourceLocation recipeUid) {
		return bookmarkGroups.getRecipeChainDetails(groupId)
			.map(details -> {
				Set<ResourceLocation> directRelations = details.recipeRelations().get(recipeUid);
				if (directRelations != null && !directRelations.isEmpty()) {
					return directRelations;
				}
				return details.recipeRelations().values().stream()
					.filter(relations -> relations.contains(recipeUid))
					.findFirst()
					.orElse(Set.of());
			})
			.orElse(Set.of());
	}

	private boolean isLastResultForRecipe(IBookmark targetBookmark, BookmarkItemMetadata targetMetadata) {
		return bookmarksList.stream()
			.filter(candidate -> candidate != targetBookmark)
			.map(bookmarkGroups::getItemMetadata)
			.noneMatch(metadata ->
				metadata.type().isGraphOutput() &&
				metadata.equalsRecipe(targetMetadata)
			);
	}

	private void normalizeIncompleteRecipes(String groupId) {
		Map<ResourceLocation, Integer> recipeStates = new HashMap<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (!groupId.equals(metadata.groupId()) || recipeUid == null || !metadata.type().isGraphMember()) {
				continue;
			}
			int bit = metadata.type().isGraphInput() ? 1 : 2;
			recipeStates.merge(recipeUid, bit, (first, second) -> first | second);
		}
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (!groupId.equals(metadata.groupId()) || recipeUid == null || !metadata.type().isGraphMember()) {
				continue;
			}
			if (recipeStates.getOrDefault(recipeUid, 0) != 3) {
				bookmarkGroups.setItemMetadata(bookmark, demoteToItem(metadata));
			}
		}
	}

	private void removeBookmarkWithoutNotifying(IBookmark bookmark) {
		bookmarksSet.remove(bookmark);
		bookmarksList.remove(bookmark);
		bookmarkGroups.removeItem(bookmark);
	}

	private void removeEmptyGroupsWithoutNotifying() {
		List<BookmarkGroup> emptyGroups = bookmarkGroups.getGroups().stream()
			.filter(group -> !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(group.id()))
			.filter(group -> bookmarksList.stream()
				.map(bookmarkGroups::getGroupId)
				.noneMatch(group.id()::equals))
			.toList();
		for (BookmarkGroup emptyGroup : emptyGroups) {
			bookmarkGroups.removeGroup(emptyGroup.id());
		}
	}

	private static BookmarkItemMetadata demoteToItem(BookmarkItemMetadata metadata) {
		return new BookmarkItemMetadata(
			metadata.groupId(),
			BookmarkItemType.ITEM,
			metadata.multiplier(),
			metadata.factor(),
			metadata.chance(),
			null,
			null,
			metadata.permutations()
		);
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
		bookmarkGroups.addItem(value, addToFront);
		bookmarkGroups.setItemMetadata(value, createDefaultMetadata(value, BookmarkGroupManager.DEFAULT_GROUP_ID));
		return true;
	}

	public void setFromConfigFile(List<IBookmark> bookmarks) {
		bookmarksList.clear();
		bookmarksSet.clear();
		bookmarkGroups.clear();
		for (IBookmark bookmark : bookmarks) {
			addToListWithoutNotifying(bookmark, false);
		}
		notifyListenersOfChange();
	}

	public boolean addRecipeBookmarks(IRecipeLayoutDrawable<?> recipeLayout, boolean preserveAmount) {
		return addRecipeBookmarks(new RecipeLayoutProjection(recipeLayout), preserveAmount);
	}

	public boolean addRecipeBookmarks(
		IRecipeLayoutDrawable<?> recipeLayout,
		boolean preserveAmount,
		Map<Integer, BookmarkIngredientKey> selectedInputKeys
	) {
		return addRecipeBookmarks(new RecipeLayoutProjection(recipeLayout, selectedInputKeys), preserveAmount);
	}

	private boolean addRecipeBookmarks(RecipeLayoutProjection projection, boolean preserveAmount) {
		List<RecipeBookmarkEntry> recipeBookmarks = createRecipeBookmarkEntries(projection, preserveAmount, null);
		if (recipeBookmarks.isEmpty()) {
			return false;
		}
		addRecipeBookmarkEntries(recipeBookmarks);
		return true;
	}

	public Optional<String> addRecipeLayoutProjectionBookmarkGroup(
		List<RecipeLayoutProjection> recipeLayouts,
		boolean preserveAmount
	) {
		if (recipeLayouts.isEmpty()) {
			return Optional.empty();
		}
		String title = getRecipeBookmarkGroupTitle(recipeLayouts.get(0));
		return addRecipeLayoutProjectionBookmarkGroup(title, recipeLayouts, preserveAmount);
	}

	private Optional<String> addRecipeLayoutProjectionBookmarkGroup(
		String title,
		List<RecipeLayoutProjection> recipeLayouts,
		boolean preserveAmount
	) {
		Object recipeTreeScope = new Object();
		List<RecipeBookmarkEntry> recipeBookmarks = new ArrayList<>();
		for (RecipeLayoutProjection recipeLayout : recipeLayouts) {
			recipeBookmarks.addAll(createRecipeBookmarkEntries(recipeLayout, preserveAmount, recipeTreeScope));
		}
		if (recipeBookmarks.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(addRecipeBookmarkEntryGroup(title, recipeBookmarks));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public Optional<IRecipeLayoutDrawable<?>> createRecipeLayoutDrawable(String groupId, ResourceLocation recipeUid) {
		if (recipeManager == null || focusFactory == null) {
			return Optional.empty();
		}
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			if (!groupId.equals(metadata.groupId()) || !recipeUid.equals(metadata.recipeUid())) {
				continue;
			}
			if (bookmark instanceof RecipeBookmark recipeBookmark) {
				return recipeManager.createRecipeLayoutDrawable(
						recipeBookmark.getRecipeCategory(),
						recipeBookmark.getRecipe(),
						focusFactory.getEmptyFocusGroup()
					)
					.map(layout -> (IRecipeLayoutDrawable<?>) layout);
			}
		}
		return Optional.empty();
	}

	public Optional<IRecipeLayoutDrawable<?>> createRecipeLayoutDrawable(RecipeBookmark<?, ?> recipeBookmark) {
		return createRecipeLayoutDrawableForBookmark(recipeBookmark);
	}

	private <R> Optional<IRecipeLayoutDrawable<?>> createRecipeLayoutDrawableForBookmark(RecipeBookmark<R, ?> recipeBookmark) {
		if (recipeManager == null || focusFactory == null) {
			return Optional.empty();
		}
		return recipeManager.createRecipeLayoutDrawable(
				recipeBookmark.getRecipeCategory(),
				recipeBookmark.getRecipe(),
				focusFactory.getEmptyFocusGroup()
			)
			.map(layout -> (IRecipeLayoutDrawable<?>) layout);
	}

	public Optional<IRecipeLayoutDrawable<?>> createUniqueRecipeLayoutDrawable(ITypedIngredient<?> ingredient) {
		if (recipeManager == null || focusFactory == null) {
			return Optional.empty();
		}
		IFocus<?> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, ingredient);
		List<IFocus<?>> focuses = List.of(focus);
		IFocusGroup focusGroup = focusFactory.createFocusGroup(focuses);
		List<IRecipeLayoutDrawable<?>> layouts = new ArrayList<>(1);
		List<IRecipeCategory<?>> recipeCategories = recipeManager.createRecipeCategoryLookup()
			.limitFocus(focuses)
			.get()
			.toList();
		for (IRecipeCategory<?> recipeCategory : recipeCategories) {
			addRecipeLayoutsForFocus(layouts, recipeCategory, focuses, focusGroup);
			if (layouts.size() > 1) {
				return Optional.empty();
			}
		}
		if (layouts.size() != 1) {
			return Optional.empty();
		}
		return Optional.of(layouts.getFirst());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void addRecipeLayoutsForFocus(
		List<IRecipeLayoutDrawable<?>> layouts,
		IRecipeCategory recipeCategory,
		List<IFocus<?>> focuses,
		IFocusGroup focusGroup
	) {
		List<?> recipes = recipeManager.createRecipeLookup(recipeCategory.getRecipeType())
			.limitFocus(focuses)
			.get()
			.limit(2)
			.toList();
		for (Object recipe : recipes) {
			recipeManager.createRecipeLayoutDrawable(recipeCategory, recipe, focusGroup)
				.map(layout -> (IRecipeLayoutDrawable<?>) layout)
				.ifPresent(layout -> layouts.add((IRecipeLayoutDrawable<?>) layout));
		}
	}

	public void addRecipeBookmarks(List<IBookmark> recipeBookmarks) {
		boolean addToFront = clientConfig != null && clientConfig.addBookmarksToFrontEnabled().getValue();
		for (IBookmark recipeBookmark : recipeBookmarks) {
			addToListWithoutNotifying(recipeBookmark, addToFront);
		}
		notifyListenersOfChange();
		saveBookmarks();
	}

	private void addRecipeBookmarkEntries(List<RecipeBookmarkEntry> recipeBookmarks) {
		boolean addToFront = clientConfig != null && clientConfig.addBookmarksToFrontEnabled().getValue();
		for (RecipeBookmarkEntry recipeBookmark : recipeBookmarks) {
			addRecipeBookmarkEntryWithoutNotifying(recipeBookmark, addToFront);
		}
		keepRecipeBlocksContiguous(recipeBookmarks);
		notifyListenersOfChange();
		saveBookmarks();
	}

	private void addRecipeBookmarkEntryWithoutNotifying(RecipeBookmarkEntry recipeBookmark, boolean addToFront) {
		BookmarkItemMetadata recipeMetadata = recipeBookmark.metadata();
		int existingIndex = findExistingRecipeBookmarkEntry(recipeBookmark);
		if (existingIndex >= 0) {
			IBookmark existingBookmark = bookmarksList.get(existingIndex);
			BookmarkItemMetadata existingMetadata = bookmarkGroups.getItemMetadata(existingBookmark);
			if (existingMetadata.type() == BookmarkItemType.ITEM && recipeMetadata.recipeUid() != null) {
				bookmarkGroups.setItemMetadata(existingBookmark, recipeMetadata.withGroupId(existingMetadata.groupId()));
			}
			return;
		}
		if (addToListWithoutNotifying(recipeBookmark.bookmark(), addToFront)) {
			bookmarkGroups.setItemMetadata(recipeBookmark.bookmark(), recipeMetadata);
		}
	}

	private int findExistingRecipeBookmarkEntry(RecipeBookmarkEntry recipeBookmark) {
		int existingIndex = bookmarksList.indexOf(recipeBookmark.bookmark());
		if (existingIndex >= 0) {
			return existingIndex;
		}
		BookmarkItemMetadata metadata = recipeBookmark.metadata();
		for (int index = 0; index < bookmarksList.size(); index++) {
			BookmarkItemMetadata existingMetadata = bookmarkGroups.getItemMetadata(bookmarksList.get(index));
			if (existingMetadata.type() == metadata.type() &&
				existingMetadata.groupId().equals(metadata.groupId()) &&
				Objects.equals(existingMetadata.recipeTypeUid(), metadata.recipeTypeUid()) &&
				Objects.equals(existingMetadata.recipeUid(), metadata.recipeUid()) &&
				existingMetadata.permutations().equals(metadata.permutations())) {
				return index;
			}
		}
		return -1;
	}

	private void keepRecipeBlocksContiguous(List<RecipeBookmarkEntry> recipeBookmarks) {
		Set<RecipeBlockKey> keys = new LinkedHashSet<>();
		for (RecipeBookmarkEntry recipeBookmark : recipeBookmarks) {
			createRecipeBlockKey(recipeBookmark.metadata()).ifPresent(keys::add);
		}
		for (RecipeBlockKey key : keys) {
			keepRecipeBlockContiguous(key);
		}
	}

	private void keepRecipeBlocksContiguous(String groupId) {
		Set<RecipeBlockKey> keys = new LinkedHashSet<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			if (groupId.equals(metadata.groupId())) {
				createRecipeBlockKey(metadata).ifPresent(keys::add);
			}
		}
		for (RecipeBlockKey key : keys) {
			keepRecipeBlockContiguous(key);
		}
	}

	private Optional<RecipeBlockKey> createRecipeBlockKey(BookmarkItemMetadata metadata) {
		ResourceLocation recipeUid = metadata.recipeUid();
		ResourceLocation recipeTypeUid = metadata.recipeTypeUid();
		if (!metadata.type().isRecipeAssociated() || recipeUid == null || recipeTypeUid == null) {
			return Optional.empty();
		}
		return Optional.of(new RecipeBlockKey(metadata.groupId(), recipeTypeUid, recipeUid));
	}

	private void keepRecipeBlockContiguous(RecipeBlockKey key) {
		List<IBookmark> recipeBookmarks = bookmarksList.stream()
			.filter(bookmark -> matchesRecipeBlock(bookmarkGroups.getItemMetadata(bookmark), key))
			.toList();
		if (recipeBookmarks.size() < 2) {
			return;
		}

		int firstIndex = bookmarksList.indexOf(recipeBookmarks.get(0));
		bookmarksList.removeAll(recipeBookmarks);
		int insertionIndex = Math.max(0, Math.min(firstIndex, bookmarksList.size()));
		bookmarksList.addAll(insertionIndex, recipeBookmarks);
	}

	private static boolean matchesRecipeBlock(BookmarkItemMetadata metadata, RecipeBlockKey key) {
		return metadata.type().isRecipeAssociated() &&
			key.groupId().equals(metadata.groupId()) &&
			key.recipeTypeUid().equals(metadata.recipeTypeUid()) &&
			key.recipeUid().equals(metadata.recipeUid());
	}

	private Optional<IBookmark> findRecipeBookmark(BookmarkItemMetadata targetMetadata) {
		ResourceLocation recipeUid = targetMetadata.recipeUid();
		ResourceLocation recipeTypeUid = targetMetadata.recipeTypeUid();
		if (recipeUid == null || recipeTypeUid == null) {
			return Optional.empty();
		}
		return bookmarksList.stream()
			.filter(bookmark -> {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
				return metadata.type().isRecipeAssociated() &&
					targetMetadata.groupId().equals(metadata.groupId()) &&
					recipeUid.equals(metadata.recipeUid()) &&
					recipeTypeUid.equals(metadata.recipeTypeUid());
			})
			.findFirst();
	}

	public String addRecipeBookmarkGroup(String title, List<IBookmark> recipeBookmarks) {
		if (recipeBookmarks.isEmpty()) {
			return BookmarkGroupManager.DEFAULT_GROUP_ID;
		}

		boolean addToFront = clientConfig != null && clientConfig.addBookmarksToFrontEnabled().getValue();
		List<IBookmark> addedOrExistingBookmarks = new ArrayList<>();
		for (IBookmark recipeBookmark : recipeBookmarks) {
			IBookmark bookmark = addOrGetWithoutNotifying(recipeBookmark, addToFront);
			if (!addedOrExistingBookmarks.contains(bookmark)) {
				addedOrExistingBookmarks.add(bookmark);
			}
		}

		String groupId = bookmarkGroups.createGroup(title);
		for (IBookmark bookmark : addedOrExistingBookmarks) {
			bookmarkGroups.moveItemToGroup(bookmark, groupId);
		}
		bookmarksList.removeAll(addedOrExistingBookmarks);
		if (addToFront) {
			bookmarksList.addAll(0, addedOrExistingBookmarks);
		} else {
			bookmarksList.addAll(addedOrExistingBookmarks);
		}
		for (IBookmark bookmark : addedOrExistingBookmarks) {
			ensureRecipeBookmarkScope(bookmark);
		}
		notifyListenersOfChange();
		saveBookmarks();
		return groupId;
	}

	private String addRecipeBookmarkEntryGroup(String title, List<RecipeBookmarkEntry> recipeBookmarks) {
		boolean addToFront = clientConfig != null && clientConfig.addBookmarksToFrontEnabled().getValue();
		List<RecipeBookmarkEntry> addedOrExistingBookmarks = new ArrayList<>();
		for (RecipeBookmarkEntry recipeBookmark : recipeBookmarks) {
			IBookmark bookmark = addOrGetWithoutNotifying(recipeBookmark.bookmark(), addToFront);
			if (addedOrExistingBookmarks.stream().noneMatch(entry -> entry.bookmark().equals(bookmark))) {
				addedOrExistingBookmarks.add(new RecipeBookmarkEntry(bookmark, recipeBookmark.metadata()));
			}
		}

		String groupId = bookmarkGroups.createGroup(title);
		for (RecipeBookmarkEntry recipeBookmark : addedOrExistingBookmarks) {
			IBookmark bookmark = recipeBookmark.bookmark();
			bookmarkGroups.moveItemToGroup(bookmark, groupId);
			bookmarkGroups.setItemMetadata(bookmark, recipeBookmark.metadata().withGroupId(groupId));
		}
		bookmarksList.removeAll(addedOrExistingBookmarks.stream()
			.map(RecipeBookmarkEntry::bookmark)
			.toList());
		List<IBookmark> orderedBookmarks = addedOrExistingBookmarks.stream()
			.map(RecipeBookmarkEntry::bookmark)
			.toList();
		if (addToFront) {
			bookmarksList.addAll(0, orderedBookmarks);
		} else {
			bookmarksList.addAll(orderedBookmarks);
		}
		for (RecipeBookmarkEntry recipeBookmark : addedOrExistingBookmarks) {
			ensureRecipeBookmarkScope(recipeBookmark.bookmark());
		}
		bookmarkGroups.setViewMode(groupId, BookmarkViewMode.TODO_LIST);
		bookmarkGroups.setCraftingMode(groupId, true);
		notifyListenersOfChange();
		saveBookmarks();
		return groupId;
	}

	private IBookmark addOrGetWithoutNotifying(IBookmark bookmark, boolean addToFront) {
		int index = bookmarksList.indexOf(bookmark);
		if (index >= 0) {
			return bookmarksList.get(index);
		}
		addToListWithoutNotifying(bookmark, addToFront);
		return bookmark;
	}

	private List<RecipeBookmarkEntry> createRecipeBookmarkEntries(
		RecipeLayoutProjection projection,
		boolean preserveAmount,
		Object equalityScope
	) {
		IRecipeLayoutDrawable<?> recipeLayout = projection.layout();
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs = GtmVirtualCircuitCompat.projectVirtualInputs(recipeLayout.getRecipe(), ingredientManager);
		List<RecipeBookmarkEntry> bookmarks = new ArrayList<>();
		addSlotBookmarks(bookmarks, recipeLayout, RecipeIngredientRole.OUTPUT, preserveAmount, projection, virtualInputs, equalityScope);
		addSlotBookmarks(bookmarks, recipeLayout, RecipeIngredientRole.INPUT, preserveAmount, projection, virtualInputs, equalityScope);
		addSyntheticNonConsumableBookmarks(bookmarks, recipeLayout, preserveAmount, virtualInputs, equalityScope);
		return bookmarks;
	}

	private <R> Optional<RecipeBookmarkEntry> createPrimaryRecipeBookmarkEntry(IRecipeLayoutDrawable<R> recipeLayout, boolean preserveAmount) {
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs = GtmVirtualCircuitCompat.projectVirtualInputs(recipeLayout.getRecipe(), ingredientManager);
		IRecipeSlotsView recipeSlotsView = recipeLayout.getRecipeSlotsView();
		Optional<RecipeBookmarkEntry> output = createFirstSlotBookmark(recipeLayout, recipeSlotsView, RecipeIngredientRole.OUTPUT, preserveAmount, virtualInputs);
		if (output.isPresent()) {
			return output;
		}
		return createFirstSlotBookmark(recipeLayout, recipeSlotsView, RecipeIngredientRole.INPUT, preserveAmount, virtualInputs);
	}

	private <R> Optional<RecipeBookmarkEntry> createFirstSlotBookmark(
		IRecipeLayoutDrawable<R> recipeLayout,
		IRecipeSlotsView recipeSlotsView,
		RecipeIngredientRole role,
		boolean preserveAmount,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs
	) {
		List<IRecipeSlotView> roleSlots = recipeSlotsView.getSlotViews(role);
		for (IRecipeSlotView slotView : roleSlots) {
			Optional<ITypedIngredient<?>> ingredient = slotView.getAllIngredients().findFirst();
			if (ingredient.isPresent()) {
				return Optional.of(createRecipeBookmark(recipeLayout, slotView, roleSlots, ingredient.get(), role, preserveAmount, virtualInputs, null, null));
			}
		}
		return Optional.empty();
	}

	private <R> void addSlotBookmarks(
		List<RecipeBookmarkEntry> bookmarks,
		IRecipeLayoutDrawable<R> recipeLayout,
		RecipeIngredientRole role,
		boolean preserveAmount,
		RecipeLayoutProjection projection,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		Object equalityScope
	) {
		IRecipeSlotsView recipeSlotsView = recipeLayout.getRecipeSlotsView();
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
			BookmarkIngredientKey lockedInputPermutation = role == RecipeIngredientRole.INPUT ?
				projection.selectedInputKey(i).orElse(null) :
				null;
			ingredient
				.map(selected -> createRecipeBookmark(recipeLayout, slotView, roleSlots, selected, role, preserveAmount, virtualInputs, equalityScope, lockedInputPermutation))
				.ifPresent(entry -> {
					if (bookmarks.stream().noneMatch(bookmark -> bookmark.bookmark().equals(entry.bookmark()))) {
						bookmarks.add(entry);
					}
			});
		}
	}

	private <R> void addSyntheticNonConsumableBookmarks(
		List<RecipeBookmarkEntry> bookmarks,
		IRecipeLayoutDrawable<R> recipeLayout,
		boolean preserveAmount,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		Object equalityScope
	) {
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
		for (GtmVirtualCircuitCompat.VirtualInput virtualInput : virtualInputs.inputs()) {
			ITypedIngredient<?> ingredient = virtualInput.ingredient();
			boolean displayed = inputSlots.stream()
				.flatMap(IRecipeSlotView::getAllIngredients)
				.anyMatch(candidate -> sameIngredient(candidate, ingredient));
			if (displayed) {
				continue;
			}
			RecipeBookmarkEntry entry = createSyntheticRecipeInputBookmark(recipeLayout, virtualInput, preserveAmount, equalityScope);
			if (bookmarks.stream().noneMatch(bookmark -> bookmark.bookmark().equals(entry.bookmark()))) {
				bookmarks.add(entry);
			}
		}
	}

	private <R, T> RecipeBookmarkEntry createSyntheticRecipeInputBookmark(
		IRecipeLayoutDrawable<R> recipeLayout,
		GtmVirtualCircuitCompat.VirtualInput virtualInput,
		boolean preserveAmount,
		Object equalityScope
	) {
		@SuppressWarnings("unchecked")
		ITypedIngredient<T> ingredient = (ITypedIngredient<T>) virtualInput.ingredient();
		IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
		R recipe = recipeLayout.getRecipe();
		ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
		if (recipeUid == null) {
			IBookmark bookmark = preserveAmount ?
				IngredientBookmark.createPreservingAmount(ingredient, ingredientManager) :
				IngredientBookmark.create(ingredient, ingredientManager);
			return new RecipeBookmarkEntry(bookmark, BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID));
		}
		ITypedIngredient<T> bookmarkIngredient = preserveAmount ? ingredient : ingredientManager.normalizeTypedIngredient(ingredient);
		IBookmark bookmark = new RecipeBookmark<>(recipeCategory, recipe, recipeUid, bookmarkIngredient, RecipeIngredientRole.INPUT, equalityScope);
		long factor = virtualInput.programmedCircuit() ?
			0 : BookmarkIngredientAmountResolver.getAmount(ingredient, ingredientManager);
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForSyntheticRecipeInput(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			recipeCategory.getRecipeType().getUid(),
			recipeUid,
			BookmarkItemType.CATALYST,
			ingredient,
			ingredientManager,
			factor
		);
		return new RecipeBookmarkEntry(bookmark, metadata);
	}

	private Optional<ITypedIngredient<?>> getSelectedIngredient(
		IRecipeSlotView slotView,
		BookmarkIngredientKey selectedKey
	) {
		if (selectedKey == null || ingredientManager == null) {
			return Optional.empty();
		}
		return slotView.getAllIngredients()
			.filter(ingredient -> selectedKey.equals(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)))
			.findFirst();
	}

	private <R, T> RecipeBookmarkEntry createRecipeBookmark(
		IRecipeLayoutDrawable<R> recipeLayout,
		IRecipeSlotView slotView,
		List<IRecipeSlotView> roleSlots,
		ITypedIngredient<T> ingredient,
		RecipeIngredientRole role,
		boolean preserveAmount,
		GtmVirtualCircuitCompat.VirtualInputProjection virtualInputs,
		Object equalityScope,
		@Nullable BookmarkIngredientKey lockedInputPermutation
	) {
		IRecipeCategory<R> recipeCategory = recipeLayout.getRecipeCategory();
		R recipe = recipeLayout.getRecipe();
		ITypedIngredient<T> bookmarkIngredient = preserveAmount ? ingredient : ingredientManager.normalizeTypedIngredient(ingredient);
		ResourceLocation recipeUid = recipeCategory.getRegistryName(recipe);
		if (recipeUid == null) {
			IBookmark bookmark = preserveAmount ?
				IngredientBookmark.createPreservingAmount(ingredient, ingredientManager) :
				IngredientBookmark.create(ingredient, ingredientManager);
			return new RecipeBookmarkEntry(bookmark, BookmarkItemMetadata.defaultForGroup(BookmarkGroupManager.DEFAULT_GROUP_ID));
		}
		IBookmark bookmark = new RecipeBookmark<>(recipeCategory, recipe, recipeUid, bookmarkIngredient, role, equalityScope);
		boolean virtualInput = role == RecipeIngredientRole.INPUT && virtualInputs.inputs().stream()
			.map(GtmVirtualCircuitCompat.VirtualInput::ingredient)
			.anyMatch(candidate -> sameIngredient(ingredient, candidate));
		BookmarkItemType type = virtualInput ?
			BookmarkItemType.CATALYST : BookmarkItemType.fromRecipeRole(role);
		BookmarkItemMetadata metadata = BookmarkItemMetadataFactory.createForRecipeSlot(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			recipeCategory,
			recipeUid,
			type,
			slotView,
			roleSlots,
			ingredient,
			ingredientManager
		);
		if (lockedInputPermutation != null) {
			metadata = metadata.withPermutations(Set.of(lockedInputPermutation));
		}
		return new RecipeBookmarkEntry(bookmark, metadata);
	}

	private boolean sameIngredient(ITypedIngredient<?> first, ITypedIngredient<?> second) {
		return BookmarkItemMetadataFactory.createPermutationKey(first, ingredientManager)
			.equals(BookmarkItemMetadataFactory.createPermutationKey(second, ingredientManager));
	}

	private String getRecipeBookmarkGroupTitle(RecipeLayoutProjection projection) {
		IRecipeSlotsView recipeSlotsView = projection.layout().getRecipeSlotsView();
		return findSelectedOutputIngredient(recipeSlotsView, projection.selectedOutputKey())
			.or(() -> findFirstIngredient(recipeSlotsView, RecipeIngredientRole.OUTPUT))
			.or(() -> findFirstIngredient(recipeSlotsView, RecipeIngredientRole.INPUT))
			.map(this::getIngredientDisplayName)
			.orElse("Recipe");
	}

	private Optional<ITypedIngredient<?>> findSelectedOutputIngredient(
		IRecipeSlotsView recipeSlotsView,
		Optional<BookmarkIngredientKey> selectedOutputKey
	) {
		if (selectedOutputKey.isEmpty() || ingredientManager == null) {
			return Optional.empty();
		}
		return recipeSlotsView.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
			.map(slot -> slot.getAllIngredients()
				.filter(ingredient -> selectedOutputKey.get().equals(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)))
				.findFirst())
			.flatMap(Optional::stream)
			.findFirst();
	}

	private Optional<ITypedIngredient<?>> findFirstIngredient(IRecipeSlotsView recipeSlotsView, RecipeIngredientRole role) {
		return recipeSlotsView.getSlotViews(role).stream()
			.map(slot -> slot.getAllIngredients().findFirst())
			.flatMap(Optional::stream)
			.findFirst();
	}

	private <T> String getIngredientDisplayName(ITypedIngredient<T> ingredient) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredient.getType());
		return ingredientHelper.getDisplayName(ingredient.getIngredient());
	}

	@Override
	public List<IElement<?>> getElements() {
		return getDisplayEntries().stream()
			.map(this::createDisplayElement)
			.toList();
	}

	@Override
	public List<IElement<?>> getElements(int columns) {
		return getElements(columns, List.of());
	}

	@Override
	public List<IElement<?>> getElements(int columns, List<Integer> usableColumnsPerRow) {
		if (columns <= 0) {
			return getElements();
		}
		List<BookmarkDisplaySlot<IBookmark>> displaySlots = getDisplaySlots(columns, usableColumnsPerRow);
		if (displaySlots.isEmpty()) {
			return List.of();
		}
		Map<Integer, BookmarkDisplaySlot<IBookmark>> slotByIndex = new HashMap<>();
		int maxSlotIndex = 0;
		for (BookmarkDisplaySlot<IBookmark> displaySlot : displaySlots) {
			slotByIndex.put(displaySlot.slotIndex(), displaySlot);
			maxSlotIndex = Math.max(maxSlotIndex, displaySlot.slotIndex());
		}
		List<IElement<?>> elements = new ArrayList<>(maxSlotIndex + 1);
		for (int slotIndex = 0; slotIndex <= maxSlotIndex; slotIndex++) {
			BookmarkDisplaySlot<IBookmark> displaySlot = slotByIndex.get(slotIndex);
			if (displaySlot == null) {
				elements.add(LayoutPlaceholderElement.INSTANCE);
			} else {
				elements.add(createDisplayElement(displaySlot.entry()));
			}
		}
		return List.copyOf(elements);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private IElement<?> createDisplayElement(BookmarkDisplayEntry<IBookmark> entry) {
		IElement<?> element = entry.item().getElement();
		if (!needsProjectedElement(entry)) {
			return element;
		}
		return new ProjectedBookmarkElement((IElement) element, entry);
	}

	static boolean needsProjectedElement(BookmarkDisplayEntry<?> entry) {
		return entry.recipeChainItem().isPresent() ||
			entry.metadata().recipeUid() != null ||
			!entry.metadata().isDefault();
	}

	public List<BookmarkDisplayEntry<IBookmark>> getDisplayEntries() {
		return bookmarkGroups.getDisplayEntries(bookmarksList);
	}

	public List<BookmarkDisplaySlot<IBookmark>> getDisplaySlots() {
		return bookmarkGroups.getDisplaySlots(bookmarksList);
	}

	public List<BookmarkDisplaySlot<IBookmark>> getDisplaySlots(int columns) {
		return getDisplaySlots(columns, List.of());
	}

	public List<BookmarkDisplaySlot<IBookmark>> getDisplaySlots(int columns, List<Integer> usableColumnsPerRow) {
		if (columns > 0) {
			latestDisplaySlotsColumns = columns;
		}
		if (cachedDisplaySlotsVersion != changeVersion ||
			cachedDisplaySlotsColumns != columns ||
			!cachedDisplaySlotsPerRow.equals(usableColumnsPerRow)) {
			cachedDisplaySlotsVersion = changeVersion;
			cachedDisplaySlotsColumns = columns;
			cachedDisplaySlotsPerRow = List.copyOf(usableColumnsPerRow);
			cachedDisplaySlots = bookmarkGroups.getDisplaySlots(bookmarksList, columns, usableColumnsPerRow);
		}
		return cachedDisplaySlots;
	}

	public <R> RecipeBookmark<R, ?> getMatchingBookmark(RecipeType<R> recipeType, R recipe) {
		for (IBookmark bookmark : bookmarksList) {
			if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
				if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(bookmarkGroups.getGroupId(bookmark)) &&
					recipeBookmark.getRecipeCategory().getRecipeType().equals(recipeType) &&
					recipeBookmark.getRecipe().equals(recipe)) {
					@SuppressWarnings("unchecked")
					RecipeBookmark<R, ?> castBookmark = (RecipeBookmark<R, ?>) recipeBookmark;
					return castBookmark;
				}
			}
		}
		return null;
	}

	public Optional<BookmarkDisplayEntry<IBookmark>> getDisplayEntry(IBookmark bookmark) {
		List<BookmarkDisplaySlot<IBookmark>> displaySlots = latestDisplaySlotsColumns > 0 ?
			getDisplaySlots(latestDisplaySlotsColumns) :
			getDisplaySlots();
		return displaySlots.stream()
			.map(BookmarkDisplaySlot::entry)
			.filter(entry -> entry.item().equals(bookmark))
			.findFirst();
	}

	public List<IBookmark> getBookmarks() {
		return List.copyOf(bookmarksList);
	}

	public List<BookmarkGroup> getBookmarkGroups() {
		return bookmarkGroups.getGroups();
	}

	public Optional<RecipeChainDetails> getRecipeChainDetails(String groupId) {
		return bookmarkGroups.getRecipeChainDetails(groupId);
	}

	public List<RecipeChainInput> getRecipeChainInputs(String groupId) {
		return hydrateRecipeInputs(bookmarkGroups.getRecipeChainInputs(bookmarksList, groupId));
	}

	/**
	 * A display-only chain snapshot. Normal bracket hover uses the details that
	 * are already refreshed with the bookmark group, so only legacy entries that
	 * have no saved permutations need recipe-layout hydration here.
	 */
	public List<RecipeChainInput> getRecipeChainTooltipInputs(String groupId) {
		List<RecipeChainInput> recipeInputs = bookmarkGroups.getRecipeChainInputs(bookmarksList, groupId);
		List<RecipeChainInput> inputs = new ArrayList<>(recipeInputs.size());
		for (RecipeChainInput input : recipeInputs) {
			IBookmark bookmark = bookmarksList.get(input.index());
			BookmarkItemMetadata metadata = input.metadata();
			if (metadata.permutations().isEmpty()) {
				metadata = hydrateRecipeChainMetadata(bookmark, metadata);
			}
			inputs.add(new RecipeChainInput(
				input.index(),
				metadata,
				getSelectedKey(bookmark, metadata).orElse(null),
				bookmark.getElement().getTypedIngredient()
			));
		}
		return List.copyOf(inputs);
	}

	public Map<BookmarkIngredientKey, ITypedIngredient<?>> getRecipeChainTooltipIngredients(String groupId) {
		Map<BookmarkIngredientKey, ITypedIngredient<?>> ingredients = new LinkedHashMap<>();
		for (RecipeChainInput input : bookmarkGroups.getRecipeChainInputs(bookmarksList, groupId)) {
			IBookmark bookmark = bookmarksList.get(input.index());
			ITypedIngredient<?> typedIngredient = bookmark.getElement().getTypedIngredient();
			if (typedIngredient != null) {
				BookmarkIngredientKey key = BookmarkItemMetadataFactory.createPermutationKey(typedIngredient, ingredientManager);
				ingredients.putIfAbsent(key, typedIngredient);
			}
		}
		return Map.copyOf(ingredients);
	}

	public List<RecipeChainInput> getGroupRecipeInputs(String groupId) {
		return hydrateRecipeInputs(bookmarkGroups.getGroupRecipeInputs(bookmarksList, groupId));
	}

	public List<RecipeChainInput> getRecipeInputs(IBookmark bookmark) {
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		ResourceLocation recipeUid = metadata.recipeUid();
		if (recipeUid == null) {
			return List.of();
		}
		return getGroupRecipeInputs(metadata.groupId()).stream()
			.filter(input -> recipeUid.equals(input.metadata().recipeUid()))
			.toList();
	}

	private List<RecipeChainInput> hydrateRecipeInputs(List<RecipeChainInput> recipeInputs) {
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (RecipeChainInput input : recipeInputs) {
			IBookmark bookmark = bookmarksList.get(input.index());
			BookmarkItemMetadata metadata = hydrateRecipeChainMetadata(bookmark, input.metadata());
			inputs.add(new RecipeChainInput(
				input.index(),
				metadata,
				getSelectedKey(bookmark, metadata).orElse(null),
				bookmark.getElement().getTypedIngredient()
			));
		}
		return List.copyOf(inputs);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private BookmarkItemMetadata hydrateRecipeChainMetadata(IBookmark bookmark, BookmarkItemMetadata metadata) {
		if (
			recipeManager == null ||
				focusFactory == null ||
				ingredientManager == null ||
				metadata.recipeUid() == null ||
				!metadata.type().isRecipeAssociated() ||
				!(bookmark instanceof RecipeBookmark recipeBookmark)
		) {
			return metadata;
		}
		Optional<IRecipeLayoutDrawable<?>> layout = recipeManager.createRecipeLayoutDrawable(
				recipeBookmark.getRecipeCategory(),
				recipeBookmark.getRecipe(),
				focusFactory.getEmptyFocusGroup()
			)
			.map(recipeLayout -> (IRecipeLayoutDrawable<?>) recipeLayout);
		if (layout.isEmpty()) {
			return metadata;
		}
		Set<BookmarkIngredientKey> hydratedPermutations = hydratePermutations(bookmark, metadata, layout.get());
		if (hydratedPermutations.equals(metadata.permutations())) {
			return metadata;
		}
		return metadata.withPermutations(hydratedPermutations);
	}

	private Set<BookmarkIngredientKey> hydratePermutations(IBookmark bookmark, BookmarkItemMetadata metadata, IRecipeLayoutDrawable<?> layout) {
		RecipeIngredientRole role = metadata.type().recipeRole();
		if (role == null) {
			return metadata.permutations();
		}

		Set<BookmarkIngredientKey> seedKeys = new LinkedHashSet<>(metadata.permutations());
		if (seedKeys.isEmpty()) {
			ITypedIngredient<?> currentIngredient = bookmark.getElement().getTypedIngredient();
			if (currentIngredient == null || ingredientManager == null) {
				return metadata.permutations();
			}
			seedKeys.add(BookmarkItemMetadataFactory.createPermutationKey(currentIngredient, ingredientManager));
		}

		Set<BookmarkIngredientKey> hydrated = new LinkedHashSet<>(seedKeys);
		List<IRecipeSlotView> slots = layout.getRecipeSlotsView().getSlotViews(role);
		for (IRecipeSlotView slot : slots) {
			Set<BookmarkIngredientKey> slotKeys = slot.getAllIngredients()
				.map(ingredient -> BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager))
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
			if (slotKeys.stream().anyMatch(seedKeys::contains)) {
				hydrated.addAll(slotKeys);
			}
		}
		return Set.copyOf(hydrated);
	}

	private Optional<BookmarkIngredientKey> getSelectedKey(IBookmark bookmark, BookmarkItemMetadata metadata) {
		ITypedIngredient<?> currentIngredient = bookmark.getElement().getTypedIngredient();
		if (currentIngredient == null || ingredientManager == null) {
			return Optional.empty();
		}
		BookmarkIngredientKey key = BookmarkItemMetadataFactory.createPermutationKey(currentIngredient, ingredientManager);
		if (metadata.permutations().contains(key)) {
			return Optional.of(key);
		}
		return Optional.empty();
	}

	public Set<ResourceLocation> getCollapsedRecipeIds(String groupId) {
		return bookmarkGroups.getCollapsedRecipeIds(groupId);
	}

	public Optional<RecipeChainItem> getRecipeChainItem(IBookmark bookmark) {
		int index = bookmarksList.indexOf(bookmark);
		if (index < 0) {
			return Optional.empty();
		}
		String groupId = bookmarkGroups.getGroupId(bookmark);
		return bookmarkGroups.getRecipeChainDetails(groupId)
			.map(RecipeChainDetails::calculatedItems)
			.map(items -> items.get(index));
	}

	public boolean isGroupCraftingMode(String groupId) {
		return bookmarkGroups.isCraftingMode(groupId);
	}

	public String createGroup(String title) {
		String groupId = bookmarkGroups.createGroup(title);
		notifyListenersOfChange();
		saveBookmarks();
		return groupId;
	}

	public String createGroupForBookmarks(String title, List<IBookmark> bookmarks) {
		String groupId = bookmarkGroups.createGroup(title);
		for (IBookmark bookmark : bookmarks) {
			if (bookmarksSet.contains(bookmark)) {
				bookmarkGroups.moveItemToGroup(bookmark, groupId);
				ensureRecipeBookmarkScope(bookmark);
			}
		}
		notifyListenersOfChange();
		saveBookmarks();
		return groupId;
	}

	public void moveBookmarkToGroup(IBookmark bookmark, String groupId) {
		if (bookmarksSet.contains(bookmark)) {
			Set<String> sourceGroupIds = collectSourceGroupIds(List.of(bookmark));
			boolean changed = false;
			if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
				String previousGroupId = bookmarkGroups.getGroupId(bookmark);
				if (!BookmarkGroupManager.DEFAULT_GROUP_ID.equals(previousGroupId)) {
					releaseBookmarkToDefault(bookmark);
					changed = true;
				}
			} else if (!isDuplicateInGroup(bookmark, groupId)) {
				if (!groupId.equals(bookmarkGroups.getGroupId(bookmark))) {
					bookmarkGroups.moveItemToGroup(bookmark, groupId);
					ensureRecipeBookmarkScope(bookmark);
					changed = true;
				}
			}
			if (changed) {
				cleanupAfterGroupChange(sourceGroupIds);
			}
			notifyListenersOfChange();
			saveBookmarks();
		}
	}

	public boolean moveBookmarksToGroup(List<IBookmark> bookmarks, String groupId) {
		List<IBookmark> movingBookmarks = bookmarks.stream()
			.filter(bookmarksSet::contains)
			.distinct()
			.toList();
		if (movingBookmarks.isEmpty()) {
			return false;
		}
		Set<String> sourceGroupIds = collectSourceGroupIds(movingBookmarks);
		boolean changed = false;
		for (IBookmark bookmark : movingBookmarks) {
			if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
				String previousGroupId = bookmarkGroups.getGroupId(bookmark);
				releaseBookmarkToDefault(bookmark);
				changed = changed || !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(previousGroupId);
			} else if (!isDuplicateInGroup(bookmark, groupId)) {
				bookmarkGroups.moveItemToGroup(bookmark, groupId);
				ensureRecipeBookmarkScope(bookmark);
				changed = true;
			}
		}
		if (changed) {
			cleanupAfterGroupChange(sourceGroupIds);
			notifyListenersOfChange();
			saveBookmarks();
		}
		return changed;
	}

	public void addGroupFromConfig(BookmarkGroup group) {
		bookmarkGroups.addGroup(group);
	}

	public void moveBookmarkToGroupFromConfig(IBookmark bookmark, String groupId) {
		moveBookmarkMetadataFromConfig(bookmark, BookmarkItemMetadata.defaultForGroup(groupId));
	}

	public void moveBookmarkMetadataFromConfig(IBookmark bookmark, BookmarkItemMetadata metadata) {
		if (bookmarksSet.contains(bookmark)) {
			bookmarkGroups.setItemMetadata(bookmark, metadata);
			ensureRecipeBookmarkScope(bookmark);
		}
	}

	public BookmarkItemMetadata getBookmarkMetadata(IBookmark bookmark) {
		return bookmarkGroups.getItemMetadata(bookmark);
	}

	public String getBookmarkGroupId(IBookmark bookmark) {
		return bookmarkGroups.getGroupId(bookmark);
	}

	public void setBookmarkMetadata(IBookmark bookmark, BookmarkItemMetadata metadata) {
		if (bookmarksSet.contains(bookmark)) {
			bookmarkGroups.setItemMetadata(bookmark, metadata);
			ensureRecipeBookmarkScope(bookmark);
			notifyListenersOfChange();
			saveBookmarks();
		}
	}

	public List<IBookmark> expandToRecipeBlocks(List<IBookmark> bookmarks) {
		Set<IBookmark> expanded = new LinkedHashSet<>();
		for (IBookmark bookmark : bookmarks) {
			if (!bookmarksSet.contains(bookmark)) {
				expanded.add(bookmark);
				continue;
			}
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (recipeUid == null || !metadata.type().isRecipeAssociated()) {
				expanded.add(bookmark);
				continue;
			}
			String groupId = metadata.groupId();
			ResourceLocation recipeTypeUid = metadata.recipeTypeUid();
			for (IBookmark candidate : bookmarksList) {
				BookmarkItemMetadata candidateMetadata = bookmarkGroups.getItemMetadata(candidate);
				if (groupId.equals(candidateMetadata.groupId()) &&
					Objects.equals(recipeTypeUid, candidateMetadata.recipeTypeUid()) &&
					recipeUid.equals(candidateMetadata.recipeUid())) {
					expanded.add(candidate);
				}
			}
		}
		return List.copyOf(expanded);
	}

	private Set<String> collectSourceGroupIds(List<IBookmark> bookmarks) {
		Set<String> sourceGroupIds = new HashSet<>();
		for (IBookmark bookmark : bookmarks) {
			String groupId = bookmarkGroups.getGroupId(bookmark);
			if (!BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
				sourceGroupIds.add(groupId);
			}
		}
		return sourceGroupIds;
	}

	private void cleanupAfterGroupChange(Set<String> sourceGroupIds) {
		for (String groupId : sourceGroupIds) {
			normalizeIncompleteRecipes(groupId);
			pruneCollapsedRecipeIds(groupId);
		}
		removeEmptyGroupsWithoutNotifying();
	}

	private void pruneCollapsedRecipeIds(String groupId) {
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty() || group.get().collapsedRecipeIds().isEmpty()) {
			return;
		}
		Set<ResourceLocation> remaining = new HashSet<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (groupId.equals(metadata.groupId()) &&
				recipeUid != null &&
				group.get().collapsedRecipeIds().contains(recipeUid)) {
				remaining.add(recipeUid);
			}
		}
		if (!remaining.equals(group.get().collapsedRecipeIds())) {
			bookmarkGroups.setCollapsedRecipeIds(groupId, remaining);
		}
	}

	private boolean releaseBookmarkToDefault(IBookmark bookmark) {
		String currentGroupId = bookmarkGroups.getGroupId(bookmark);
		if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(currentGroupId)) {
			return true;
		}
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		if (metadata.recipeUid() != null && metadata.type().isRecipeAssociated()) {
			Optional<RecipeMergeKey> mergeKey = createMergeKey(bookmark, metadata);
			if (mergeKey.isPresent()) {
				Optional<IBookmark> mergeTarget = findDefaultMergeTarget(bookmark, mergeKey.get());
				if (mergeTarget.isPresent()) {
					IBookmark target = mergeTarget.get();
					BookmarkItemMetadata targetMetadata = bookmarkGroups.getItemMetadata(target);
					bookmarkGroups.setItemMetadata(target, mergeMetadata(targetMetadata, metadata));
					removeBookmarkWithoutNotifying(bookmark);
					return false;
				}
			}
			bookmarkGroups.moveItemToGroup(bookmark, BookmarkGroupManager.DEFAULT_GROUP_ID);
			ensureRecipeBookmarkScope(bookmark);
			return true;
		}
		Optional<IBookmark> identicalDefaultBookmark = findIdenticalDefaultBookmark(bookmark);
		if (identicalDefaultBookmark.isPresent()) {
			removeBookmarkWithoutNotifying(bookmark);
			return false;
		}
		bookmarkGroups.moveItemToGroup(bookmark, BookmarkGroupManager.DEFAULT_GROUP_ID);
		return true;
	}

	private Optional<IBookmark> findIdenticalDefaultBookmark(IBookmark bookmark) {
		return bookmarksList.stream()
			.filter(candidate -> candidate != bookmark)
			.filter(candidate -> BookmarkGroupManager.DEFAULT_GROUP_ID.equals(bookmarkGroups.getGroupId(candidate)))
			.filter(candidate -> candidate.equals(bookmark))
			.findFirst();
	}

	private Optional<IBookmark> findDefaultMergeTarget(IBookmark source, RecipeMergeKey mergeKey) {
		return bookmarksList.stream()
			.filter(candidate -> candidate != source)
			.filter(candidate -> BookmarkGroupManager.DEFAULT_GROUP_ID.equals(bookmarkGroups.getGroupId(candidate)))
			.filter(candidate -> {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(candidate);
				return createMergeKey(candidate, metadata)
					.map(mergeKey::equals)
					.orElse(false);
			})
			.findFirst();
	}

	private boolean isDuplicateInGroup(IBookmark bookmark, String groupId) {
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		Optional<RecipeMergeKey> mergeKey = createMergeKey(bookmark, metadata);
		if (mergeKey.isEmpty()) {
			return false;
		}
		RecipeMergeKey key = mergeKey.get();
		return bookmarksList.stream()
			.filter(candidate -> candidate != bookmark)
			.filter(candidate -> groupId.equals(bookmarkGroups.getGroupId(candidate)))
			.anyMatch(candidate -> createMergeKey(candidate, bookmarkGroups.getItemMetadata(candidate))
				.map(key::equals)
				.orElse(false));
	}

	private Optional<RecipeMergeKey> createMergeKey(IBookmark bookmark, BookmarkItemMetadata metadata) {
		ResourceLocation recipeUid = metadata.recipeUid();
		if (recipeUid == null || !metadata.type().isRecipeAssociated()) {
			return Optional.empty();
		}
		Set<BookmarkIngredientKey> slotKey = getDisplayedIngredientKey(bookmark)
			.map(Set::of)
			.orElse(metadata.permutations());
		return Optional.of(new RecipeMergeKey(metadata.type(), metadata.recipeTypeUid(), recipeUid, Set.copyOf(slotKey)));
	}

	private Optional<BookmarkIngredientKey> getDisplayedIngredientKey(IBookmark bookmark) {
		if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
			ITypedIngredient<?> ingredient = recipeBookmark.getRecipeOutput();
			if (ingredientManager != null) {
				return Optional.of(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager));
			}
			Object value = ingredient.getIngredient();
			if (value instanceof ItemStack stack && !stack.isEmpty()) {
				return stack.getItemHolder()
					.unwrapKey()
					.map(key -> new BookmarkIngredientKey(VanillaTypes.ITEM_STACK.getUid(), key.location().toString(), null));
			}
		}
		return Optional.empty();
	}

	private static BookmarkItemMetadata mergeMetadata(BookmarkItemMetadata existing, BookmarkItemMetadata released) {
		long chance = Math.min(existing.chance(), released.chance());
		Set<BookmarkIngredientKey> permutations = new LinkedHashSet<>(existing.permutations());
		permutations.addAll(released.permutations());
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			existing.type(),
			1,
			released.factor(),
			chance,
			existing.recipeTypeUid(),
			existing.recipeUid(),
			Set.copyOf(permutations),
			existing.containerItem(),
			existing.containerItemCraftingUses(),
			existing.brokenContainerItem()
		);
	}

	private void ensureRecipeBookmarkScope(IBookmark bookmark) {
		if (!(bookmark instanceof RecipeBookmark<?, ?> recipeBookmark)) {
			return;
		}
		String groupId = bookmarkGroups.getGroupId(bookmark);
		Object desiredScope = BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId) ? null : groupId;
		if (Objects.equals(recipeBookmark.getEqualityScope(), desiredScope)) {
			return;
		}
		replaceBookmarkInstance(bookmark, recipeBookmark.withEqualityScope(desiredScope));
	}

	private void replaceBookmarkInstance(IBookmark oldBookmark, IBookmark replacement) {
		int index = identityIndexOf(oldBookmark);
		if (index < 0 || !bookmarksSet.remove(oldBookmark)) {
			return;
		}
		if (!bookmarksSet.add(replacement)) {
			bookmarkGroups.removeItem(oldBookmark);
			bookmarksList.remove(index);
			return;
		}
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(oldBookmark);
		bookmarkGroups.removeItem(oldBookmark);
		bookmarksList.set(index, replacement);
		bookmarkGroups.setItemMetadata(replacement, metadata);
	}

	private int identityIndexOf(IBookmark bookmark) {
		for (int i = 0; i < bookmarksList.size(); i++) {
			if (bookmarksList.get(i) == bookmark) {
				return i;
			}
		}
		return -1;
	}

	public boolean shiftBookmarkAmount(IBookmark bookmark, long shift) {
		if (!bookmarksSet.contains(bookmark) || shift == 0) {
			return false;
		}
		String groupId = bookmarkGroups.getGroupId(bookmark);
		boolean groupCollapsed = getBookmarkGroups().stream()
			.filter(group -> group.id().equals(groupId))
			.findFirst()
			.map(group -> group.viewMode() == BookmarkViewMode.COLLAPSED)
			.orElse(false);
		if (groupCollapsed && !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
			return shiftGroupAmount(groupId, shift);
		}

		BookmarkItemMetadata targetMetadata = bookmarkGroups.getItemMetadata(bookmark);
		if (targetMetadata.type().isCatalyst()) {
			return false;
		}
		ResourceLocation recipeUid = targetMetadata.recipeUid();
		boolean changed = false;
		if (recipeUid != null && targetMetadata.type().isGraphMember()) {
			long multiplier = getShiftedRecipeMultiplier(groupId, recipeUid, shift);
			for (IBookmark candidate : bookmarksList) {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(candidate);
				if (groupId.equals(metadata.groupId()) &&
					recipeUid.equals(metadata.recipeUid()) &&
					metadata.type().isGraphMember() &&
					metadata.multiplier() != multiplier) {
					bookmarkGroups.setItemMetadata(candidate, metadata.withMultiplier(multiplier));
					changed = true;
				}
			}
		} else {
			long multiplier = shiftMultiplier(targetMetadata.multiplier(), shift, 0);
			if (targetMetadata.multiplier() != multiplier) {
				bookmarkGroups.setItemMetadata(bookmark, targetMetadata.withMultiplier(multiplier));
				changed = true;
			}
		}
		if (changed) {
			notifyListenersOfChange();
			saveBookmarks();
		}
		return changed;
	}

	public boolean shiftGroupAmount(String groupId, long shift) {
		if (shift == 0) {
			return false;
		}
		Optional<RecipeChainDetails> chainDetails = bookmarkGroups.getRecipeChainDetails(groupId);
		if (chainDetails.isPresent()) {
			return shiftCraftingGroupAmount(groupId, shift, chainDetails.get());
		}
		boolean changed = false;
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			if (groupId.equals(metadata.groupId()) && metadata.type().scalesWithMultiplier() && metadata.factor() > 0) {
				long multiplier = shiftMultiplier(metadata.multiplier(), shift, 0);
				if (metadata.multiplier() != multiplier) {
					bookmarkGroups.setItemMetadata(bookmark, metadata.withMultiplier(multiplier));
					changed = true;
				}
			}
		}
		if (changed) {
			notifyListenersOfChange();
			saveBookmarks();
		}
		return changed;
	}

	private boolean shiftCraftingGroupAmount(String groupId, long shift, RecipeChainDetails chainDetails) {
		Map<ResourceLocation, Long> recipeMultipliers = new HashMap<>();
		List<IBookmark> shiftedBookmarks = new ArrayList<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (groupId.equals(metadata.groupId()) &&
				recipeUid != null &&
				metadata.type().isGraphMember() &&
				metadata.factor() > 0 &&
				chainDetails.outputRecipes().contains(recipeUid)) {
				long minMultiplier = chainDetails.middleRecipes().contains(recipeUid) ? 1 : 0;
				long multiplier = shiftMultiplier(Math.max(minMultiplier, metadata.multiplier()), shift, minMultiplier);
				recipeMultipliers.merge(recipeUid, multiplier, Math::min);
				shiftedBookmarks.add(bookmark);
			}
		}
		boolean changed = false;
		for (IBookmark bookmark : shiftedBookmarks) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			Long multiplier = recipeMultipliers.get(metadata.recipeUid());
			if (multiplier != null && metadata.multiplier() != multiplier) {
				bookmarkGroups.setItemMetadata(bookmark, metadata.withMultiplier(multiplier));
				changed = true;
			}
		}
		if (changed) {
			notifyListenersOfChange();
			saveBookmarks();
		}
		return changed;
	}

	public boolean cycleBookmarkPermutation(IBookmark bookmark, long shift) {
		if (!bookmarksSet.contains(bookmark) || shift == 0 || ingredientManager == null) {
			return false;
		}
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		List<BookmarkIngredientKey> permutations = new ArrayList<>(metadata.permutations());
		if (permutations.size() <= 1) {
			return false;
		}
		ITypedIngredient<?> currentIngredient = bookmark.getElement().getTypedIngredient();
		if (currentIngredient == null) {
			return false;
		}
		BookmarkIngredientKey currentKey = BookmarkItemMetadataFactory.createPermutationKey(currentIngredient, ingredientManager);
		int currentIndex = permutations.indexOf(currentKey);
		if (currentIndex < 0) {
			currentIndex = 0;
		}
		int nextIndex = Math.floorMod(currentIndex - (int) Math.signum(shift), permutations.size());
		Optional<ITypedIngredient<?>> nextIngredient = resolvePermutation(permutations.get(nextIndex));
		if (nextIngredient.isEmpty()) {
			return false;
		}
		IBookmark replacement = createPermutationBookmark(bookmark, nextIngredient.get());
		return replaceBookmark(bookmark, replacement, metadata);
	}

	public boolean toggleBookmarkInputCatalyst(IBookmark bookmark) {
		if (!bookmarksSet.contains(bookmark)) {
			return false;
		}
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		BookmarkItemType type = metadata.type();
		BookmarkItemType toggledType;
		if (type == BookmarkItemType.INGREDIENT) {
			toggledType = BookmarkItemType.CATALYST;
		} else if (type == BookmarkItemType.CATALYST) {
			toggledType = BookmarkItemType.INGREDIENT;
		} else {
			return false;
		}
		if (metadata.recipeUid() == null) {
			return false;
		}
		bookmarkGroups.setItemMetadata(bookmark, metadata.withType(toggledType));
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Optional<ITypedIngredient<?>> resolvePermutation(BookmarkIngredientKey key) {
		return ingredientManager.getIngredientTypeForUid(key.ingredientTypeUid())
			.flatMap(type -> resolvePermutation((IIngredientType) type, key.ingredientUid()))
			.map(typedIngredient -> (ITypedIngredient<?>) typedIngredient);
	}

	private <T> Optional<ITypedIngredient<T>> resolvePermutation(IIngredientType<T> type, String ingredientUid) {
		return ingredientManager.getTypedIngredientByUid(type, ingredientUid);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private IBookmark createPermutationBookmark(IBookmark bookmark, ITypedIngredient<?> typedIngredient) {
		if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
			return new RecipeBookmark(
				recipeBookmark.getRecipeCategory(),
				recipeBookmark.getRecipe(),
				recipeBookmark.getRecipeUid(),
				typedIngredient,
				recipeBookmark.getDisplayRole(),
				recipeBookmark.getEqualityScope()
			);
		}
		return createIngredientBookmark((ITypedIngredient) typedIngredient);
	}

	private <T> IngredientBookmark<T> createIngredientBookmark(ITypedIngredient<T> typedIngredient) {
		return IngredientBookmark.createPreservingAmount(typedIngredient, ingredientManager);
	}

	private boolean replaceBookmark(IBookmark bookmark, IBookmark replacement, BookmarkItemMetadata metadata) {
		int index = bookmarksList.indexOf(bookmark);
		if (index < 0) {
			return false;
		}
		bookmarksSet.remove(bookmark);
		if (!bookmarksSet.add(replacement)) {
			bookmarksSet.add(bookmark);
			return false;
		}
		bookmarksList.set(index, replacement);
		bookmarkGroups.removeItem(bookmark);
		bookmarkGroups.setItemMetadata(replacement, metadata);
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	private long getShiftedRecipeMultiplier(String groupId, ResourceLocation recipeUid, long shift) {
		boolean recipeInMiddle = bookmarkGroups.getRecipeChainDetails(groupId)
			.map(details -> details.middleRecipes().contains(recipeUid))
			.orElse(false);
		long minMultiplier = recipeInMiddle ? 1 : 0;
		long multiplier = Long.MAX_VALUE;
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			if (groupId.equals(metadata.groupId()) &&
				recipeUid.equals(metadata.recipeUid()) &&
				metadata.type().isGraphMember() &&
				metadata.factor() > 0) {
				long currentMultiplier = Math.max(minMultiplier, metadata.multiplier());
				multiplier = Math.min(multiplier, shiftMultiplier(currentMultiplier, shift, minMultiplier));
			}
		}
		return multiplier == Long.MAX_VALUE ? 0 : multiplier;
	}

	private static long shiftMultiplier(long multiplier, long shift, long minMultiplier) {
		if (shift > 0 && multiplier > Integer.MAX_VALUE - shift) {
			return Integer.MAX_VALUE;
		}
		return Math.min(Integer.MAX_VALUE, Math.max(minMultiplier, multiplier + shift));
	}

	public void setGroupCraftingMode(String groupId, boolean craftingMode) {
		bookmarkGroups.setCraftingMode(groupId, craftingMode);
		notifyListenersOfChange();
		saveBookmarks();
	}

	public void setGroupViewMode(String groupId, BookmarkViewMode viewMode) {
		bookmarkGroups.setViewMode(groupId, viewMode);
		notifyListenersOfChange();
		saveBookmarks();
	}

	public void setGroupCollapsedRecipeIds(String groupId, Set<ResourceLocation> collapsedRecipeIds) {
		bookmarkGroups.setCollapsedRecipeIds(groupId, collapsedRecipeIds);
		notifyListenersOfChange();
		saveBookmarks();
	}

	public boolean toggleGroupCollapsedRecipeId(String groupId, ResourceLocation recipeUid) {
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty()) {
			return false;
		}
		ResourceLocation collapsedRecipeId = bookmarkGroups.getRecipeChainDetails(groupId)
			.map(details -> details.recipeRelations().entrySet().stream()
				.filter(entry -> entry.getValue().contains(recipeUid))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(recipeUid))
			.orElse(recipeUid);
		Set<ResourceLocation> collapsedRecipeIds = new HashSet<>(group.get().collapsedRecipeIds());
		boolean collapsed = !collapsedRecipeIds.contains(collapsedRecipeId);
		if (collapsed) {
			collapsedRecipeIds.add(collapsedRecipeId);
		} else {
			collapsedRecipeIds.remove(collapsedRecipeId);
		}
		boolean middleRecipe = bookmarkGroups.getRecipeChainDetails(groupId)
			.map(details -> details.middleRecipes().contains(collapsedRecipeId))
			.orElse(false);
		if (middleRecipe) {
			long fromMultiplier = collapsed ? 1 : 0;
			long toMultiplier = collapsed ? 0 : 1;
			for (IBookmark bookmark : bookmarksList) {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
				if (groupId.equals(metadata.groupId()) &&
					collapsedRecipeId.equals(metadata.recipeUid()) &&
					metadata.multiplier() == fromMultiplier) {
					bookmarkGroups.setItemMetadata(bookmark, metadata.withMultiplier(toMultiplier));
				}
			}
		}
		bookmarkGroups.setCollapsedRecipeIds(groupId, collapsedRecipeIds);
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean toggleGroupViewMode(String groupId) {
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty()) {
			return false;
		}
		bookmarkGroups.toggleViewMode(groupId);
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean toggleGroupCollapsed(String groupId) {
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty()) {
			return false;
		}
		bookmarkGroups.toggleCollapsed(groupId);
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean removeGroup(String groupId) {
		List<IBookmark> removedBookmarks = bookmarksList.stream()
			.filter(bookmark -> groupId.equals(bookmarkGroups.getGroupId(bookmark)))
			.toList();
		if (!bookmarkGroups.removeGroup(groupId)) {
			return false;
		}
		for (IBookmark bookmark : removedBookmarks) {
			removeBookmarkWithoutNotifying(bookmark);
		}
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean moveGroupToBookmark(String groupId, IBookmark targetBookmark) {
		return moveGroupToBookmark(groupId, targetBookmark, 0);
	}

	public boolean moveGroupToEnd(String groupId) {
		if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
			return false;
		}

		List<IBookmark> groupBookmarks = bookmarksList.stream()
			.filter(bookmark -> groupId.equals(bookmarkGroups.getGroupId(bookmark)))
			.toList();
		if (groupBookmarks.isEmpty()) {
			return false;
		}

		bookmarksList.removeAll(groupBookmarks);
		bookmarksList.addAll(groupBookmarks);

		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean moveGroupToBookmark(String groupId, IBookmark targetBookmark, int offset) {
		if (BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId) || !bookmarksSet.contains(targetBookmark)) {
			return false;
		}

		List<IBookmark> groupBookmarks = bookmarksList.stream()
			.filter(bookmark -> groupId.equals(bookmarkGroups.getGroupId(bookmark)))
			.toList();
		if (groupBookmarks.isEmpty() || groupBookmarks.contains(targetBookmark)) {
			return false;
		}

		bookmarksList.removeAll(groupBookmarks);
		int targetIndex = bookmarksList.indexOf(targetBookmark);
		if (targetIndex < 0) {
			bookmarksList.addAll(groupBookmarks);
		} else {
			int insertionIndex = Math.max(0, Math.min(bookmarksList.size(), targetIndex + offset));
			bookmarksList.addAll(insertionIndex, groupBookmarks);
		}

		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean isEmpty() {
		return bookmarksSet.isEmpty();
	}

	public long getChangeVersion() {
		return changeVersion;
	}

	@Override
	public void addSourceListChangedListener(SourceListChangedListener listener) {
		listeners.add(listener);
	}

	public void notifyListenersOfChange() {
		changeVersion++;
		bookmarkGroups.markRecipeChainDetailsDirty(bookmarksList);
		for (SourceListChangedListener listener : listeners) {
			listener.onSourceListChanged();
		}
	}

	private void saveBookmarks() {
		if (bookmarkConfig != null && codecHelper != null && bookmarkCodec != null) {
			bookmarkConfig.saveBookmarks(
				recipeManager,
				focusFactory,
				guiHelper,
				ingredientManager,
				registryAccess,
				codecHelper,
				getBookmarks(),
				bookmarkCodec
			);
		}
	}

	private static BookmarkItemMetadata createDefaultMetadata(IBookmark bookmark, String groupId) {
		if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
			return recipeBookmark.createDefaultMetadata(groupId);
		}
		return BookmarkItemMetadata.defaultForGroup(groupId);
	}

	private record RecipeBookmarkEntry(IBookmark bookmark, BookmarkItemMetadata metadata) {
	}

	private record RecipeBlockKey(String groupId, ResourceLocation recipeTypeUid, ResourceLocation recipeUid) {
	}

	private record RecipeMergeKey(
		BookmarkItemType type,
		ResourceLocation recipeTypeUid,
		ResourceLocation recipeUid,
		Set<BookmarkIngredientKey> slotKey
	) {
	}
}
