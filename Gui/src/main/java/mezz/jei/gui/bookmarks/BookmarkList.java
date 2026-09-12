package mezz.jei.gui.bookmarks;

import mezz.jei.gui.bookmarks.tree.RecipeTreeViewState;

import com.mojang.serialization.Codec;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IBookmarkManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.bookmarks.RecipeBookmarkEntryFactory.RecipeBookmarkEntry;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.input.InputModifiers;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.input.FocusedRecipe;
import mezz.jei.gui.recipes.FocusedRecipeLayoutResolver;
import mezz.jei.common.util.SaturatedMath;
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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class BookmarkList implements IIngredientGridSource, IBookmarkManager {
	private final List<IBookmark> bookmarksList = new LinkedList<>();
	private final Set<IBookmark> bookmarksSet = new HashSet<>();
	private final BookmarkGroupManager<IBookmark> bookmarkGroups = new BookmarkGroupManager<>();

	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final IIngredientManager ingredientManager;
	private final RecipeBookmarkEntryFactory recipeBookmarkEntryFactory;
	private final RegistryAccess registryAccess;
	private final IBookmarkConfig bookmarkConfig;
	private final IClientConfig clientConfig;
	private final IGuiHelper guiHelper;
	private final @Nullable ICodecHelper codecHelper;
	private final @Nullable Codec<IBookmark> bookmarkCodec;
	private final @Nullable BookmarkFactory bookmarkFactory;
	private final FocusedRecipeLayoutResolver focusedRecipeLayoutResolver;
	private final Function<BookmarkIngredientKey, Optional<FocusedRecipe>> preferredRecipeLookup;
	private final BookmarkCandidateTooltipState candidateTooltipState = new BookmarkCandidateTooltipState();
	private java.lang.ref.WeakReference<BookmarkCandidateSource> candidateSource = new java.lang.ref.WeakReference<>(null);
	private final List<SourceListChangedListener> listeners = new ArrayList<>();
	private long changeVersion;
	private long cachedDisplaySlotsVersion = -1;
	private int cachedDisplaySlotsColumns = -1;
	private List<Integer> cachedDisplaySlotsPerRow = List.of();
	private List<BookmarkDisplaySlot<IBookmark>> cachedDisplaySlots = List.of();
	private int latestDisplaySlotsColumns = 0;
	private List<Integer> latestDisplaySlotsPerRow = List.of();

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
		this.recipeBookmarkEntryFactory = new RecipeBookmarkEntryFactory(ingredientManager);
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

	void moveBookmarks(List<IBookmark> bookmarks, IBookmark targetBookmark, int targetGroupId, int offset) {
		List<IBookmark> movingBookmarks = bookmarks.stream()
			.filter(bookmarksSet::contains)
			.distinct()
			.toList();
		if (movingBookmarks.isEmpty() ||
			movingBookmarks.contains(targetBookmark) ||
			!bookmarksSet.contains(targetBookmark)
		) {
			return;
		}

		Set<Integer> sourceGroupIds = collectSourceGroupIds(movingBookmarks);
		bookmarksList.removeAll(movingBookmarks);
		int targetIndex = bookmarksList.indexOf(targetBookmark);
		List<IBookmark> placedBookmarks = new ArrayList<>();
		boolean changed = false;
		for (IBookmark bookmark : movingBookmarks) {
			int previousGroupId = bookmarkGroups.getGroupId(bookmark);
			if ((targetGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
				if (releaseBookmarkToDefault(bookmark)) {
					placedBookmarks.add(bookmark);
				}
				changed = changed || !(previousGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID);
			} else if (!isDuplicateInGroup(bookmark, targetGroupId)) {
				bookmarkGroups.moveItemToGroup(bookmark, targetGroupId);
				ensureBookmarkScope(bookmark);
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

	@Override
	public boolean contains(ITypedIngredient<?> ingredient) {
		return contains(bookmarkFactory.create(ingredient));
	}

	public <T> boolean onElementBookmarked(IElement<T> element, UserInput input, BookmarkOverlay bookmarkOverlay) {
		if (bookmarkOverlay.isBookmarkElementUnderMouse(element, input.getMouseX(), input.getMouseY())) {
			return element.getBookmark()
				.map(this::removeBookmarkFromOverlay)
				.orElse(false);
		}

		if (InputModifiers.hasShift(input)) {
			BookmarkHotkeyAction action = InputModifiers.hasControl(input) ? BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK_WITH_COUNT : BookmarkHotkeyAction.ADD_RECIPE_BOOKMARK;
			return onElementBookmarked(element, action);
		}

		BookmarkHotkeyAction action = InputModifiers.hasControl(input) ? BookmarkHotkeyAction.ADD_BOOKMARK_WITH_COUNT : BookmarkHotkeyAction.ADD_BOOKMARK;
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
		IBookmark bookmark = preserveAmount ? IngredientBookmark.createPreservingAmount(ingredient, ingredientManager) : IngredientBookmark.create(ingredient, ingredientManager);
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

	@Override
	public boolean add(ITypedIngredient<?> ingredient) {
		IBookmark bookmark = bookmarkFactory.create(ingredient);
		return add(bookmark);
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
		Optional<RecipeBookmarkEntry> entry = recipeBookmarkEntryFactory.createPrimaryRecipeBookmarkEntry(recipeLayout, preserveAmount);
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
		return removeRecipeBookmark(bookmark, removeFullRecipe, true);
	}

	public boolean removeExpandedRecipeBookmark(IBookmark bookmark) {
		return removeRecipeBookmark(bookmark, false, false);
	}

	private boolean removeRecipeBookmark(IBookmark bookmark, boolean removeFullRecipe, boolean respectCollapsedClosure) {
		if (!bookmarksSet.contains(bookmark)) {
			return false;
		}
		BookmarkItemMetadata targetMetadata = bookmarkGroups.getItemMetadata(bookmark);
		ResourceLocation recipeUid = targetMetadata.recipeUid();
		if (recipeUid == null || !targetMetadata.type().isRecipeAssociated()) {
			return remove(bookmark);
		}
		int groupId = targetMetadata.groupId();
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (respectCollapsedClosure && group.filter(BookmarkGroup::craftingMode).isPresent()) {
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
			isLastResultForRecipe(bookmark, targetMetadata)
		) {
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

	private boolean removeRecipes(int groupId, Set<ResourceLocation> recipeUids) {
		List<IBookmark> removedBookmarks = bookmarksList.stream()
			.filter(candidate -> {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(candidate);
				return groupId == metadata.groupId() &&
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

	private Set<ResourceLocation> getRelatedRecipeIds(int groupId, ResourceLocation recipeUid) {
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
			.noneMatch(metadata -> metadata.type().isGraphOutput() &&
				metadata.equalsRecipe(targetMetadata)
			);
	}

	private void normalizeIncompleteRecipes(int groupId) {
		Map<ResourceLocation, Integer> recipeStates = new HashMap<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (groupId != metadata.groupId() || recipeUid == null || !metadata.type().isGraphMember()) {
				continue;
			}
			int bit = metadata.type().isGraphInput() ? 1 : 2;
			recipeStates.merge(recipeUid, bit, (first, second) -> first | second);
		}
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (groupId != metadata.groupId() || recipeUid == null || !metadata.type().isGraphMember()) {
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
			.filter(group -> !(group.id() == BookmarkGroupManager.DEFAULT_GROUP_ID))
			.filter(group -> bookmarksList.stream()
				.map(bookmarkGroups::getGroupId)
				.noneMatch(groupId -> group.id() == groupId))
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

	@Override
	public boolean remove(ITypedIngredient<?> ingredient) {
		return remove(bookmarkFactory.create(ingredient));
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

	public boolean addRecipeBookmarks(
		IRecipeLayoutDrawable<?> recipeLayout,
		boolean preserveAmount,
		Map<Integer, BookmarkIngredientKey> selectedInputKeys,
		Map<Integer, List<ITypedIngredient<?>>> filteredInputCandidates
	) {
		return addRecipeBookmarks(new RecipeLayoutProjection(recipeLayout, selectedInputKeys, filteredInputCandidates), preserveAmount);
	}

	private boolean addRecipeBookmarks(RecipeLayoutProjection projection, boolean preserveAmount) {
		List<RecipeBookmarkEntry> recipeBookmarks = recipeBookmarkEntryFactory.createRecipeBookmarkEntries(projection, preserveAmount, null);
		if (recipeBookmarks.isEmpty()) {
			return false;
		}
		addRecipeBookmarkEntries(recipeBookmarks);
		return true;
	}

	public boolean addRecipeToGroup(int groupId, RecipeLayoutProjection projection) {
		if (bookmarkGroups.getGroup(groupId).isEmpty()) {
			return false;
		}
		List<RecipeBookmarkEntry> entries = recipeBookmarkEntryFactory.createRecipeBookmarkEntries(projection, false, groupId == BookmarkGroupManager.DEFAULT_GROUP_ID ? null : groupId);
		if (entries.isEmpty() || entries.getFirst().metadata().recipeUid() == null) {
			return false;
		}
		var recipe = entries.getFirst().metadata();
		if (bookmarksList.stream().map(bookmarkGroups::getItemMetadata).anyMatch(metadata -> metadata.groupId() == groupId && Objects.equals(metadata.recipeTypeUid(), recipe.recipeTypeUid()) &&
			Objects.equals(metadata.recipeUid(), recipe.recipeUid()))
		) {
			return false;
		}
		addRecipeBookmarkEntries(entries.stream().map(entry -> new RecipeBookmarkEntry(entry.bookmark(), entry.metadata().withGroupId(groupId))).toList());
		return true;
	}

	public Optional<Integer> addRecipeLayoutProjectionBookmarkGroup(
		List<RecipeLayoutProjection> recipeLayouts,
		boolean preserveAmount
	) {
		if (recipeLayouts.isEmpty()) {
			return Optional.empty();
		}
		String title = recipeBookmarkEntryFactory.getRecipeBookmarkGroupTitle(recipeLayouts.get(0));
		return addRecipeLayoutProjectionBookmarkGroup(title, recipeLayouts, preserveAmount);
	}

	private Optional<Integer> addRecipeLayoutProjectionBookmarkGroup(
		String title,
		List<RecipeLayoutProjection> recipeLayouts,
		boolean preserveAmount
	) {
		Object recipeTreeScope = new Object();
		List<RecipeBookmarkEntry> recipeBookmarks = new ArrayList<>();
		for (RecipeLayoutProjection recipeLayout : recipeLayouts) {
			recipeBookmarks.addAll(recipeBookmarkEntryFactory.createRecipeBookmarkEntries(recipeLayout, preserveAmount, recipeTreeScope));
		}
		if (recipeBookmarks.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(addRecipeBookmarkEntryGroup(title, recipeBookmarks));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public Optional<IRecipeLayoutDrawable<?>> createRecipeLayoutDrawable(int groupId, ResourceLocation recipeUid) {
		if (recipeManager == null || focusFactory == null) {
			return Optional.empty();
		}
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			if (groupId != metadata.groupId() || !recipeUid.equals(metadata.recipeUid())) {
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
		for (var iterator = bookmarksList.listIterator(); iterator.hasNext();) {
			int index = iterator.nextIndex();
			BookmarkItemMetadata existingMetadata = bookmarkGroups.getItemMetadata(iterator.next());
			if (existingMetadata.type() == metadata.type() &&
				existingMetadata.groupId() == metadata.groupId() &&
				Objects.equals(existingMetadata.recipeTypeUid(), metadata.recipeTypeUid()) &&
				Objects.equals(existingMetadata.recipeUid(), metadata.recipeUid()) &&
				existingMetadata.permutations().equals(metadata.permutations())
			) {
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

	private void keepRecipeBlocksContiguous(int groupId) {
		Set<RecipeBlockKey> keys = new LinkedHashSet<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			if (groupId == metadata.groupId()) {
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
			key.groupId() == metadata.groupId() &&
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
					targetMetadata.groupId() == metadata.groupId() &&
					recipeUid.equals(metadata.recipeUid()) &&
					recipeTypeUid.equals(metadata.recipeTypeUid());
			})
			.findFirst();
	}

	public int addRecipeBookmarkGroup(String title, List<IBookmark> recipeBookmarks) {
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

		int groupId = bookmarkGroups.createGroup(title);
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
			ensureBookmarkScope(bookmark);
		}
		notifyListenersOfChange();
		saveBookmarks();
		return groupId;
	}

	private int addRecipeBookmarkEntryGroup(String title, List<RecipeBookmarkEntry> recipeBookmarks) {
		boolean addToFront = clientConfig != null && clientConfig.addBookmarksToFrontEnabled().getValue();
		List<RecipeBookmarkEntry> addedOrExistingBookmarks = new ArrayList<>();
		for (RecipeBookmarkEntry recipeBookmark : recipeBookmarks) {
			IBookmark bookmark = addOrGetWithoutNotifying(recipeBookmark.bookmark(), addToFront);
			if (addedOrExistingBookmarks.stream().noneMatch(entry -> entry.bookmark().equals(bookmark))) {
				addedOrExistingBookmarks.add(new RecipeBookmarkEntry(bookmark, recipeBookmark.metadata()));
			}
		}

		int groupId = bookmarkGroups.createGroup(title);
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
			ensureBookmarkScope(recipeBookmark.bookmark());
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

	@Override
	public List<IElement<?>> getElements() {
		return getDisplayEntries().stream()
			.map(this::createDisplayElement)
			.toList();
	}

	@Override
	public boolean containsElement(IElement<?> element) {
		return bookmarksList.stream()
			.anyMatch(bookmark -> bookmark.getElement() == element);
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
		List<IElement<?>> elements = new ArrayList<>(displaySlots.getLast().slotIndex() + 1);
		for (BookmarkDisplaySlot<IBookmark> displaySlot : displaySlots) {
			while (elements.size() < displaySlot.slotIndex()) {
				elements.add(LayoutPlaceholderElement.INSTANCE);
			}
			elements.add(createDisplayElement(displaySlot.entry()));
		}
		return List.copyOf(elements);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public IElement<?> createDisplayElement(BookmarkDisplayEntry<IBookmark> entry) {
		IElement<?> element = entry.item().getElement();
		if (!needsProjectedElement(entry)) {
			return element;
		}
		return new ProjectedBookmarkElement((IElement) element, entry, this);
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
		List<Integer> immutableUsableColumnsPerRow = cachedDisplaySlotsPerRow.equals(usableColumnsPerRow) ? cachedDisplaySlotsPerRow : List.copyOf(usableColumnsPerRow);
		if (columns > 0) {
			latestDisplaySlotsColumns = columns;
			latestDisplaySlotsPerRow = immutableUsableColumnsPerRow;
		}
		if (cachedDisplaySlotsVersion != changeVersion ||
			cachedDisplaySlotsColumns != columns ||
			cachedDisplaySlotsPerRow != immutableUsableColumnsPerRow
		) {
			cachedDisplaySlotsVersion = changeVersion;
			cachedDisplaySlotsColumns = columns;
			cachedDisplaySlotsPerRow = immutableUsableColumnsPerRow;
			cachedDisplaySlots = bookmarkGroups.getDisplaySlots(bookmarksList, columns, immutableUsableColumnsPerRow);
		}
		return cachedDisplaySlots;
	}

	@Nullable
	public <R> RecipeBookmark<R, ?> getMatchingBookmark(RecipeType<R> recipeType, R recipe) {
		for (IBookmark bookmark : bookmarksList) {
			if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
				if ((bookmarkGroups.getGroupId(bookmark) == BookmarkGroupManager.DEFAULT_GROUP_ID) &&
					recipeBookmark.isRecipe(recipeType, recipe)
				) {
					@SuppressWarnings("unchecked")
					RecipeBookmark<R, ?> castBookmark = (RecipeBookmark<R, ?>) recipeBookmark;
					return castBookmark;
				}
			}
		}
		return null;
	}

	public Optional<BookmarkDisplayEntry<IBookmark>> getDisplayEntry(IBookmark bookmark) {
		List<BookmarkDisplaySlot<IBookmark>> displaySlots = latestDisplaySlotsColumns > 0 ? getDisplaySlots(latestDisplaySlotsColumns, latestDisplaySlotsPerRow) : getDisplaySlots();
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

	public Optional<RecipeTreeViewState> getTreeViewState(int groupId) {
		return bookmarkGroups.getTreeViewState(groupId);
	}

	public void cacheTreeViewState(int groupId, RecipeTreeViewState state) {
		bookmarkGroups.cacheTreeViewState(groupId, state);
	}

	public Optional<RecipeChainDetails> getRecipeChainDetails(int groupId) {
		return bookmarkGroups.getRecipeChainDetails(groupId);
	}

	public List<RecipeChainInput> getRecipeChainInputs(int groupId) {
		return hydrateRecipeInputs(bookmarkGroups.getRecipeChainInputs(bookmarksList, groupId), true);
	}

	/**
	 * A display-only chain snapshot. Normal bracket hover uses the details that
	 * are already refreshed with the bookmark group, so only legacy entries that
	 * have no saved permutations need recipe-layout hydration here.
	 */
	public List<RecipeChainInput> getRecipeChainTooltipInputs(int groupId) {
		return hydrateRecipeInputs(bookmarkGroups.getRecipeChainInputs(bookmarksList, groupId), false);
	}

	public List<RecipeChainInput> getGroupRecipeInputs(int groupId) {
		return hydrateRecipeInputs(bookmarkGroups.getGroupRecipeInputs(bookmarksList, groupId), true);
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

	private List<RecipeChainInput> hydrateRecipeInputs(List<RecipeChainInput> recipeInputs, boolean hydrateSavedPermutations) {
		List<RecipeChainInput> inputs = new ArrayList<>(recipeInputs.size());
		var iterator = bookmarksList.listIterator();
		for (RecipeChainInput input : recipeInputs) {
			while (iterator.nextIndex() < input.index()) {
				iterator.next();
			}
			IBookmark bookmark = iterator.next();
			BookmarkItemMetadata metadata = input.metadata();
			if (hydrateSavedPermutations || metadata.permutations().isEmpty()) {
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

	@SuppressWarnings({"rawtypes", "unchecked"})
	private BookmarkItemMetadata hydrateRecipeChainMetadata(IBookmark bookmark, BookmarkItemMetadata metadata) {
		if (metadata.type() == BookmarkItemType.ITEM && metadata.permutations().isEmpty() && ingredientManager != null) {
			ITypedIngredient<?> ingredient = bookmark.getElement().getTypedIngredient();
			if (ingredient != null) {
				return metadata.withPermutations(Set.of(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager)));
			}
		}
		if (recipeManager == null ||
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

	public Set<ResourceLocation> getCollapsedRecipeIds(int groupId) {
		return bookmarkGroups.getCollapsedRecipeIds(groupId);
	}

	public Optional<RecipeChainItem> getRecipeChainItem(IBookmark bookmark) {
		int index = bookmarksList.indexOf(bookmark);
		if (index < 0) {
			return Optional.empty();
		}
		int groupId = bookmarkGroups.getGroupId(bookmark);
		return bookmarkGroups.getRecipeChainDetails(groupId)
			.map(RecipeChainDetails::calculatedItems)
			.map(items -> items.get(index));
	}

	public boolean isGroupCraftingMode(int groupId) {
		return bookmarkGroups.isCraftingMode(groupId);
	}

	public int createGroup(String title) {
		int groupId = bookmarkGroups.createGroup(title);
		notifyListenersOfChange();
		saveBookmarks();
		return groupId;
	}

	public void addMissingRecipeChainGroup(
		int sourceGroupId,
		List<RecipeChainTooltipModel.Item> missingItems
	) {
		BookmarkGroup sourceGroup = bookmarkGroups.getGroup(sourceGroupId).orElseThrow();
		int groupId = bookmarkGroups.createGroup(sourceGroup.title());
		for (RecipeChainTooltipModel.Item item : missingItems) {
			ITypedIngredient<?> ingredient = item.ingredient();
			IBookmark bookmark = IngredientBookmark.createWithAmount(ingredient, 1, ingredientManager)
				.withEqualityScope(groupId);
			if (addToListWithoutNotifying(bookmark, false)) {
				BookmarkItemMetadata metadata = BookmarkItemMetadata.defaultForGroup(groupId)
					.withMultiplier(item.amount())
					.withPermutations(Set.of(item.key()));
				bookmarkGroups.setItemMetadata(bookmark, metadata);
			}
		}
		bookmarkGroups.setCraftingMode(groupId, true);
		notifyListenersOfChange();
		saveBookmarks();
	}

	public int createGroupForBookmarks(String title, List<IBookmark> bookmarks) {
		int groupId = bookmarkGroups.createGroup(title);
		for (IBookmark bookmark : bookmarks) {
			if (bookmarksSet.contains(bookmark)) {
				bookmarkGroups.moveItemToGroup(bookmark, groupId);
				ensureBookmarkScope(bookmark);
			}
		}
		notifyListenersOfChange();
		saveBookmarks();
		return groupId;
	}

	public void moveBookmarkToGroup(IBookmark bookmark, int groupId) {
		if (bookmarksSet.contains(bookmark)) {
			Set<Integer> sourceGroupIds = collectSourceGroupIds(List.of(bookmark));
			boolean changed = false;
			if ((groupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
				int previousGroupId = bookmarkGroups.getGroupId(bookmark);
				if (!(previousGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
					releaseBookmarkToDefault(bookmark);
					changed = true;
				}
			} else if (!isDuplicateInGroup(bookmark, groupId)) {
				if (groupId != bookmarkGroups.getGroupId(bookmark)) {
					bookmarkGroups.moveItemToGroup(bookmark, groupId);
					ensureBookmarkScope(bookmark);
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

	public boolean moveBookmarksToGroup(List<IBookmark> bookmarks, int groupId) {
		List<IBookmark> movingBookmarks = bookmarks.stream()
			.filter(bookmarksSet::contains)
			.distinct()
			.toList();
		if (movingBookmarks.isEmpty()) {
			return false;
		}
		Set<Integer> sourceGroupIds = collectSourceGroupIds(movingBookmarks);
		boolean changed = false;
		for (IBookmark bookmark : movingBookmarks) {
			if ((groupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
				int previousGroupId = bookmarkGroups.getGroupId(bookmark);
				releaseBookmarkToDefault(bookmark);
				changed = changed || !(previousGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID);
			} else if (!isDuplicateInGroup(bookmark, groupId)) {
				bookmarkGroups.moveItemToGroup(bookmark, groupId);
				ensureBookmarkScope(bookmark);
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

	public void moveBookmarkToGroupFromConfig(IBookmark bookmark, int groupId) {
		moveBookmarkMetadataFromConfig(bookmark, BookmarkItemMetadata.defaultForGroup(groupId));
	}

	public void moveBookmarkMetadataFromConfig(IBookmark bookmark, BookmarkItemMetadata metadata) {
		if (bookmarksSet.contains(bookmark)) {
			bookmarkGroups.setItemMetadata(bookmark, metadata);
			ensureBookmarkScope(bookmark);
		}
	}

	public BookmarkItemMetadata getBookmarkMetadata(IBookmark bookmark) {
		return bookmarkGroups.getItemMetadata(bookmark);
	}

	public int getBookmarkGroupId(IBookmark bookmark) {
		return bookmarkGroups.getGroupId(bookmark);
	}

	public void setBookmarkMetadata(IBookmark bookmark, BookmarkItemMetadata metadata) {
		if (bookmarksSet.contains(bookmark)) {
			bookmarkGroups.setItemMetadata(bookmark, metadata);
			ensureBookmarkScope(bookmark);
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
			int groupId = metadata.groupId();
			ResourceLocation recipeTypeUid = metadata.recipeTypeUid();
			for (IBookmark candidate : bookmarksList) {
				BookmarkItemMetadata candidateMetadata = bookmarkGroups.getItemMetadata(candidate);
				if (groupId == candidateMetadata.groupId() &&
					Objects.equals(recipeTypeUid, candidateMetadata.recipeTypeUid()) &&
					recipeUid.equals(candidateMetadata.recipeUid())
				) {
					expanded.add(candidate);
				}
			}
		}
		return List.copyOf(expanded);
	}

	private Set<Integer> collectSourceGroupIds(List<IBookmark> bookmarks) {
		Set<Integer> sourceGroupIds = new HashSet<>();
		for (IBookmark bookmark : bookmarks) {
			int groupId = bookmarkGroups.getGroupId(bookmark);
			if (!(groupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
				sourceGroupIds.add(groupId);
			}
		}
		return sourceGroupIds;
	}

	private void cleanupAfterGroupChange(Set<Integer> sourceGroupIds) {
		for (int groupId : sourceGroupIds) {
			normalizeIncompleteRecipes(groupId);
			pruneCollapsedRecipeIds(groupId);
		}
		removeEmptyGroupsWithoutNotifying();
	}

	private void pruneCollapsedRecipeIds(int groupId) {
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty() || group.get().collapsedRecipeIds().isEmpty()) {
			return;
		}
		Set<ResourceLocation> remaining = new HashSet<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (groupId == metadata.groupId() &&
				recipeUid != null &&
				group.get().collapsedRecipeIds().contains(recipeUid)
			) {
				remaining.add(recipeUid);
			}
		}
		if (!remaining.equals(group.get().collapsedRecipeIds())) {
			bookmarkGroups.setCollapsedRecipeIds(groupId, remaining);
		}
	}

	private boolean releaseBookmarkToDefault(IBookmark bookmark) {
		int currentGroupId = bookmarkGroups.getGroupId(bookmark);
		if ((currentGroupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
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
			ensureBookmarkScope(bookmark);
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
			.filter(candidate -> (bookmarkGroups.getGroupId(candidate) == BookmarkGroupManager.DEFAULT_GROUP_ID))
			.filter(candidate -> candidate.equals(bookmark))
			.findFirst();
	}

	private Optional<IBookmark> findDefaultMergeTarget(IBookmark source, RecipeMergeKey mergeKey) {
		return bookmarksList.stream()
			.filter(candidate -> candidate != source)
			.filter(candidate -> (bookmarkGroups.getGroupId(candidate) == BookmarkGroupManager.DEFAULT_GROUP_ID))
			.filter(candidate -> {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(candidate);
				return createMergeKey(candidate, metadata)
					.map(mergeKey::equals)
					.orElse(false);
			})
			.findFirst();
	}

	private boolean isDuplicateInGroup(IBookmark bookmark, int groupId) {
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		Optional<RecipeMergeKey> mergeKey = createMergeKey(bookmark, metadata);
		if (mergeKey.isEmpty()) {
			return false;
		}
		RecipeMergeKey key = mergeKey.get();
		return bookmarksList.stream()
			.filter(candidate -> candidate != bookmark)
			.filter(candidate -> groupId == bookmarkGroups.getGroupId(candidate))
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
			ITypedIngredient<?> ingredient = recipeBookmark.getDisplayIngredient();
			if (ingredientManager != null) {
				return Optional.of(BookmarkItemMetadataFactory.createPermutationKey(ingredient, ingredientManager));
			}
			Object value = ingredient.getIngredient();
			if (value instanceof ItemStack stack && !stack.isEmpty()) {
				return stack.getItemHolder()
					.unwrapKey()
					.map(key -> new BookmarkIngredientKey(VanillaTypes.ITEM_STACK.getUid(), key.location().toString()));
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

	private void ensureBookmarkScope(IBookmark bookmark) {
		int groupId = bookmarkGroups.getGroupId(bookmark);
		Object desiredScope = (groupId == BookmarkGroupManager.DEFAULT_GROUP_ID) ? null : groupId;
		if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
			if (!Objects.equals(recipeBookmark.getEqualityScope(), desiredScope)) {
				replaceBookmarkInstance(bookmark, recipeBookmark.withEqualityScope(desiredScope));
			}
		} else if (bookmark instanceof IngredientBookmark<?> ingredientBookmark &&
			!Objects.equals(ingredientBookmark.getEqualityScope(), desiredScope)
		) {
			replaceBookmarkInstance(bookmark, ingredientBookmark.withEqualityScope(desiredScope));
		}
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
		for (var iterator = bookmarksList.listIterator(); iterator.hasNext();) {
			int index = iterator.nextIndex();
			if (iterator.next() == bookmark) {
				return index;
			}
		}
		return -1;
	}

	public boolean shiftBookmarkAmount(IBookmark bookmark, long shift) {
		if (!bookmarksSet.contains(bookmark) || shift == 0) {
			return false;
		}
		int groupId = bookmarkGroups.getGroupId(bookmark);
		boolean groupCollapsed = bookmarkGroups.getGroup(groupId)
			.map(BookmarkGroup::collapsed)
			.orElse(false);
		if (groupCollapsed && !(groupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
			return shiftGroupAmount(groupId, shift);
		}
		return shiftRecipeAmount(bookmark, shift);
	}

	/** Adjust this recipe independently of how its group is displayed. */
	public boolean shiftRecipeAmount(IBookmark bookmark, long shift) {
		if (!bookmarksSet.contains(bookmark) || shift == 0) {
			return false;
		}
		int groupId = bookmarkGroups.getGroupId(bookmark);
		BookmarkItemMetadata targetMetadata = bookmarkGroups.getItemMetadata(bookmark);
		if (targetMetadata.type().isNonConsumable()) {
			return false;
		}
		ResourceLocation recipeUid = targetMetadata.recipeUid();
		boolean changed = false;
		if (recipeUid != null && targetMetadata.type().isGraphMember()) {
			long multiplier = getShiftedRecipeMultiplier(groupId, recipeUid, shift);
			for (IBookmark candidate : bookmarksList) {
				BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(candidate);
				if (groupId == metadata.groupId() &&
					recipeUid.equals(metadata.recipeUid()) &&
					metadata.type().isGraphMember() &&
					metadata.multiplier() != multiplier
				) {
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

	public boolean shiftGroupAmount(int groupId, long shift) {
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
			if (groupId == metadata.groupId() && metadata.type().scalesWithMultiplier() && metadata.factor() > 0) {
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

	private boolean shiftCraftingGroupAmount(int groupId, long shift, RecipeChainDetails chainDetails) {
		Map<ResourceLocation, Long> recipeMultipliers = new HashMap<>();
		List<IBookmark> shiftedBookmarks = new ArrayList<>();
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			ResourceLocation recipeUid = metadata.recipeUid();
			if (groupId == metadata.groupId() &&
				recipeUid != null &&
				metadata.type().isGraphMember() &&
				metadata.factor() > 0 &&
				chainDetails.outputRecipes().contains(recipeUid)
			) {
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

	public BookmarkCandidateTooltipState getCandidateTooltipState() { return candidateTooltipState; }

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
		return selectBookmarkPermutation(bookmark, permutations.get(nextIndex), false).isPresent();
	}

	public Optional<IBookmark> selectBookmarkPermutation(IBookmark bookmark, BookmarkIngredientKey selected, boolean synchronize) {
		var replacement = applyBookmarkPermutation(bookmark, selected, synchronize);
		replacement.ifPresent(value -> Optional.ofNullable(candidateSource.get()).ifPresent(source -> source.replaceBookmark(bookmark, value)));
		return replacement;
	}

	public BookmarkCandidateSource getCandidateSource(IBookmark bookmark) {
		var source = candidateSource.get();
		if (source == null || !source.isFor(bookmark) || !source.isValid()) {
			source = new BookmarkCandidateSource(this, bookmark);
			candidateSource = new java.lang.ref.WeakReference<>(source);
		}
		return source;
	}

	private Optional<IBookmark> applyBookmarkPermutation(IBookmark bookmark, BookmarkIngredientKey selected, boolean synchronize) {
		if (!bookmarksSet.contains(bookmark) || ingredientManager == null) {
			return Optional.empty();
		}
		var metadata = bookmarkGroups.getItemMetadata(bookmark);
		var candidate = metadata.permutations().stream().filter(selected::equals).findFirst().orElse(null);
		if (candidate == null || candidate.typedIngredient() == null) {
			return Optional.empty();
		}
		if (!synchronize && getSelectedKey(bookmark, metadata).filter(selected::equals).isPresent()) {
			return Optional.of(bookmark);
		}
		if (metadata.type().recipeRole() == RecipeIngredientRole.INPUT) {
			List<BookmarkRecipeSelection.Choice> choices = new ArrayList<>();
			for (int index = 0; index < bookmarksList.size(); index++) {
				IBookmark entry = bookmarksList.get(index);
				var info = bookmarkGroups.getItemMetadata(entry);
				if (entry != bookmark && (!synchronize || metadata.recipeUid() == null ||
					info.groupId() != metadata.groupId() ||
					!Objects.equals(metadata.recipeUid(), info.recipeUid()) || !Objects.equals(metadata.recipeTypeUid(), info.recipeTypeUid()) ||
					info.type() != metadata.type() || !info.permutations().equals(metadata.permutations()))
				) {
					continue;
				}
				choices.add(new BookmarkRecipeSelection.Choice(index, getSelectedKey(entry, info).orElseThrow(), selected, info.factor()));
			}
			if (choices.stream().allMatch(choice -> choice.before().equals(choice.after()))) {
				return Optional.of(bookmark);
			}
			if (!applyRecipeInputChoices(choices)) {
				return Optional.empty();
			}
			return bookmarksList.stream().filter(entry -> {
					var info = bookmarkGroups.getItemMetadata(entry);
					return info.groupId() == metadata.groupId() && Objects.equals(info.recipeUid(), metadata.recipeUid()) &&
						Objects.equals(info.recipeTypeUid(), metadata.recipeTypeUid()) && info.type() == metadata.type() &&
						getSelectedKey(entry, info).filter(selected::equals).isPresent();
				})
				.findFirst();
		}
		IBookmark replacement = createPermutationBookmark(bookmark, candidate.typedIngredient());
		return replaceBookmark(bookmark, replacement, metadata) ? Optional.of(replacement) : Optional.empty();
	}

	/** An expanded editor view; does not change the group's saved display or collapse settings. */
	public List<BookmarkDisplaySlot<IBookmark>> getGroupEditorSlots(int groupId, int columns) {
		var group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty()) {
			return List.of();
		}
		var saved = group.get();
		var expanded = new BookmarkGroup(groupId, saved.title(), BookmarkViewMode.TODO_LIST, false, saved.craftingMode(), Set.of());
		var details = getRecipeChainDetails(groupId);
		if (saved.craftingMode() && !saved.collapsedRecipeIds().isEmpty()) {
			details = Optional.of(mezz.jei.gui.bookmarks.chain.RecipeChainMath.refresh(getRecipeChainTooltipInputs(groupId), Set.of()));
		}
		return BookmarkDisplayGenerator.generate(bookmarksList, this::getBookmarkMetadata, Map.of(groupId, expanded),
			details.map(value -> Map.of(groupId, value)).orElse(Map.of()), columns, List.of(), id -> id == groupId);
	}

	/** Apply a complete saved-input projection in one transaction, splitting/merging repeated slots by quantity. */
	public boolean applyRecipeInputChoices(List<BookmarkRecipeSelection.Choice> choices) {
		if (ingredientManager == null || choices.stream().noneMatch(choice -> !Objects.equals(choice.before(), choice.after()))) {
			return false;
		}
		Map<IBookmark, List<BookmarkRecipeSelection.Choice>> bySource = new LinkedHashMap<>();
		for (var choice : choices) {
			if (choice.sourceIndex() < 0 || choice.sourceIndex() >= bookmarksList.size() || choice.before() == null || choice.after() == null || choice.amount() < 0) {
				return false;
			}
			IBookmark source = bookmarksList.get(choice.sourceIndex());
			var metadata = bookmarkGroups.getItemMetadata(source);
			if (metadata.type().recipeRole() != RecipeIngredientRole.INPUT ||
				!getSelectedKey(source, metadata).filter(choice.before()::equals).isPresent() ||
				(!choice.after().equals(choice.before()) && !metadata.permutations().contains(choice.after()))
			) {
				return false;
			}
			bySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(choice);
		}
		Map<IBookmark, BookmarkItemMetadata> replacements = new LinkedHashMap<>();
		for (var entry : bySource.entrySet()) {
			var metadata = bookmarkGroups.getItemMetadata(entry.getKey());
			long remaining = metadata.factor();
			for (var choice : entry.getValue()) {
				if (choice.amount() > remaining) {
					return false;
				}
				remaining -= choice.amount();
				var candidate = metadata.permutations().stream().filter(choice.after()::equals).findFirst().orElse(choice.after()).typedIngredient();
				if (candidate == null && choice.after().equals(choice.before())) {
					candidate = entry.getKey().getElement().getTypedIngredient();
				}
				if (candidate == null) {
					return false;
				}
				IBookmark replacement = createPermutationBookmark(entry.getKey(), ingredientManager.normalizeTypedIngredient(candidate));
				if (!mergeInputChoice(replacements, replacement, metadata.withFactor(choice.amount()))) {
					return false;
				}
			}
			if (remaining > 0 && !mergeInputChoice(replacements, entry.getKey(), metadata.withFactor(remaining))) {
				return false;
			}
		}
		if (replacements.keySet().stream().anyMatch(bookmark -> bookmarksSet.contains(bookmark) && !bySource.containsKey(bookmark))) {
			return false;
		}
		int insertAt = bySource.keySet().stream().mapToInt(bookmarksList::indexOf).min().orElseThrow();
		for (IBookmark source : bySource.keySet()) {
			removeBookmarkWithoutNotifying(source);
		}
		for (var entry : replacements.entrySet()) {
			bookmarksSet.add(entry.getKey());
			bookmarksList.add(insertAt++, entry.getKey());
			bookmarkGroups.setItemMetadata(entry.getKey(), entry.getValue());
		}
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	private static boolean mergeInputChoice(Map<IBookmark, BookmarkItemMetadata> entries, IBookmark bookmark, BookmarkItemMetadata metadata) {
		var previous = entries.get(bookmark);
		if (previous != null) {
			if (!previous.withFactor(0).equals(metadata.withFactor(0))) {
				return false;
			}
			metadata = metadata.withFactor(SaturatedMath.add(previous.factor(), metadata.factor()));
		}
		entries.put(bookmark, metadata);
		return true;
	}

	public boolean toggleBookmarkInputCatalyst(IBookmark bookmark) {
		if (!bookmarksSet.contains(bookmark)) {
			return false;
		}
		BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
		BookmarkItemType type = metadata.type();
		BookmarkItemType toggledType;
		if (type == BookmarkItemType.INGREDIENT) {
			toggledType = BookmarkItemType.NONCONSUMABLE;
		} else if (type == BookmarkItemType.NONCONSUMABLE) {
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
		IngredientBookmark<?> replacement = createIngredientBookmark((ITypedIngredient) typedIngredient);
		return bookmark instanceof IngredientBookmark<?> original ? replacement.withEqualityScope(original.getEqualityScope()) : replacement;
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

	private long getShiftedRecipeMultiplier(int groupId, ResourceLocation recipeUid, long shift) {
		boolean recipeInMiddle = bookmarkGroups.getRecipeChainDetails(groupId)
			.map(details -> details.middleRecipes().contains(recipeUid))
			.orElse(false);
		long minMultiplier = recipeInMiddle ? 1 : 0;
		long multiplier = Long.MAX_VALUE;
		for (IBookmark bookmark : bookmarksList) {
			BookmarkItemMetadata metadata = bookmarkGroups.getItemMetadata(bookmark);
			if (groupId == metadata.groupId() &&
				recipeUid.equals(metadata.recipeUid()) &&
				metadata.type().isGraphMember() &&
				metadata.factor() > 0
			) {
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

	public void setGroupCraftingMode(int groupId, boolean craftingMode) {
		bookmarkGroups.setCraftingMode(groupId, craftingMode);
		notifyListenersOfChange();
		saveBookmarks();
	}

	public void setGroupViewMode(int groupId, BookmarkViewMode viewMode) {
		bookmarkGroups.setViewMode(groupId, viewMode);
		notifyListenersOfChange();
		saveBookmarks();
	}

	public void setGroupCollapsedRecipeIds(int groupId, Set<ResourceLocation> collapsedRecipeIds) {
		bookmarkGroups.setCollapsedRecipeIds(groupId, collapsedRecipeIds);
		notifyListenersOfChange();
		saveBookmarks();
	}

	public boolean toggleGroupCollapsedRecipeId(int groupId, ResourceLocation recipeUid) {
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
				if (groupId == metadata.groupId() &&
					collapsedRecipeId.equals(metadata.recipeUid()) &&
					metadata.multiplier() == fromMultiplier
				) {
					bookmarkGroups.setItemMetadata(bookmark, metadata.withMultiplier(toMultiplier));
				}
			}
		}
		bookmarkGroups.setCollapsedRecipeIds(groupId, collapsedRecipeIds);
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean toggleGroupViewMode(int groupId) {
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty()) {
			return false;
		}
		bookmarkGroups.toggleViewMode(groupId);
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean toggleGroupCollapsed(int groupId) {
		Optional<BookmarkGroup> group = bookmarkGroups.getGroup(groupId);
		if (group.isEmpty()) {
			return false;
		}
		bookmarkGroups.toggleCollapsed(groupId);
		notifyListenersOfChange();
		saveBookmarks();
		return true;
	}

	public boolean removeGroup(int groupId) {
		List<IBookmark> removedBookmarks = bookmarksList.stream()
			.filter(bookmark -> groupId == bookmarkGroups.getGroupId(bookmark))
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

	public boolean moveGroupToBookmark(int groupId, IBookmark targetBookmark) {
		return moveGroupToBookmark(groupId, targetBookmark, 0);
	}

	public boolean moveGroupToEnd(int groupId) {
		if ((groupId == BookmarkGroupManager.DEFAULT_GROUP_ID)) {
			return false;
		}

		List<IBookmark> groupBookmarks = bookmarksList.stream()
			.filter(bookmark -> groupId == bookmarkGroups.getGroupId(bookmark))
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

	public boolean moveGroupToBookmark(int groupId, IBookmark targetBookmark, int offset) {
		if ((groupId == BookmarkGroupManager.DEFAULT_GROUP_ID) || !bookmarksSet.contains(targetBookmark)) {
			return false;
		}

		List<IBookmark> groupBookmarks = bookmarksList.stream()
			.filter(bookmark -> groupId == bookmarkGroups.getGroupId(bookmark))
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

	private static BookmarkItemMetadata createDefaultMetadata(IBookmark bookmark, int groupId) {
		if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
			return recipeBookmark.createDefaultMetadata(groupId);
		}
		return BookmarkItemMetadata.defaultForGroup(groupId);
	}

	private record RecipeBlockKey(int groupId, ResourceLocation recipeTypeUid, ResourceLocation recipeUid) {
	}

	private record RecipeMergeKey(
		BookmarkItemType type,
		ResourceLocation recipeTypeUid,
		ResourceLocation recipeUid,
		Set<BookmarkIngredientKey> slotKey
	) {
	}
}
