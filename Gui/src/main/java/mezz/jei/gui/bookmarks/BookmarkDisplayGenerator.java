package mezz.jei.gui.bookmarks;

import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class BookmarkDisplayGenerator {
	private BookmarkDisplayGenerator() {
	}

	public static <T> List<BookmarkDisplaySlot<T>> generate(
		List<T> orderedItems,
		Function<T, BookmarkItemMetadata> metadataGetter,
		Map<String, BookmarkGroup> groups,
		Map<String, RecipeChainDetails> recipeChainDetails
	) {
		return generate(orderedItems, metadataGetter, groups, recipeChainDetails, 0);
	}

	public static <T> List<BookmarkDisplaySlot<T>> generate(
		List<T> orderedItems,
		Function<T, BookmarkItemMetadata> metadataGetter,
		Map<String, BookmarkGroup> groups,
		Map<String, RecipeChainDetails> recipeChainDetails,
		int columns
	) {
		List<BookmarkDisplaySlot<T>> displaySlots = new ArrayList<>();
		Set<String> collapsedGroupsShown = new HashSet<>();
		for (int sourceIndex = 0; sourceIndex < orderedItems.size(); sourceIndex++) {
			T item = orderedItems.get(sourceIndex);
			BookmarkItemMetadata metadata = metadataGetter.apply(item);
			String groupId = metadata.groupId();
			BookmarkGroup group = groups.get(groupId);
			if (group != null && group.craftingMode()) {
				if (group.collapsed() && !BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId) && collapsedGroupsShown.contains(groupId)) {
					continue;
				}
				addCraftingDisplaySlot(displaySlots, item, sourceIndex, metadata, group, recipeChainDetails.get(group.id()), columns);
				if (!displaySlots.isEmpty() && displaySlots.get(displaySlots.size() - 1).entry().sourceIndex() == sourceIndex) {
					collapsedGroupsShown.add(groupId);
				}
			} else if (group == null || !group.collapsed() || BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
				addDisplaySlot(displaySlots, createDisplayEntry(item, sourceIndex, metadata, group), false, columns);
			} else if (collapsedGroupsShown.add(groupId)) {
				addDisplaySlot(displaySlots, createDisplayEntry(item, sourceIndex, metadata, group), false, columns);
			}
		}
		return List.copyOf(displaySlots);
	}

	private static <T> void addCraftingDisplaySlot(
		List<BookmarkDisplaySlot<T>> displaySlots,
		T item,
		int sourceIndex,
		BookmarkItemMetadata metadata,
		BookmarkGroup group,
		RecipeChainDetails details,
		int columns
	) {
		if (details == null) {
			return;
		}
		RecipeChainItem chainItem = details.calculatedItems().get(sourceIndex);
		if (chainItem == null) {
			addCollapsedRecipeShadowSlot(displaySlots, item, sourceIndex, metadata, group, details, columns);
			return;
		}
		if (group.viewMode() == BookmarkViewMode.DEFAULT && metadata.type() == BookmarkItemType.INGREDIENT) {
			return;
		}
		BookmarkDisplayEntry<T> entry = createDisplayEntry(item, sourceIndex, metadata, group, details, chainItem);
		if (isHiddenCollapsedIntermediateResult(entry)) {
			return;
		}
		addDisplaySlot(displaySlots, entry, isCollapsedRecipeShadow(group, entry), columns);
	}

	private static <T> void addCollapsedRecipeShadowSlot(
		List<BookmarkDisplaySlot<T>> displaySlots,
		T item,
		int sourceIndex,
		BookmarkItemMetadata metadata,
		BookmarkGroup group,
		RecipeChainDetails details,
		int columns
	) {
		Optional<ResourceLocation> displayRecipeUid = getDisplayRecipeUid(group, details, metadata.recipeUid());
		boolean ownCollapsedRecipeIngredient = metadata.type() == BookmarkItemType.INGREDIENT &&
			metadata.recipeUid() != null &&
			group.collapsedRecipeIds().contains(metadata.recipeUid());
		if (displayRecipeUid.isEmpty() || displayRecipeUid.equals(Optional.ofNullable(metadata.recipeUid())) && !ownCollapsedRecipeIngredient) {
			return;
		}
		BookmarkDisplayEntry<T> entry = new BookmarkDisplayEntry<>(
			item,
			sourceIndex,
			metadata,
			group.viewMode(),
			displayRecipeUid,
			Optional.empty(),
			false,
			false
		);
		addDisplaySlot(displaySlots, entry, true, columns);
	}

	private static boolean isHiddenCollapsedIntermediateResult(BookmarkDisplayEntry<?> entry) {
		return entry.metadata().type() == BookmarkItemType.RESULT &&
			entry.displayRecipeUid().isPresent() &&
			entry.metadata().recipeUid() != null &&
			!entry.displayRecipeUid().get().equals(entry.metadata().recipeUid());
	}

	private static <T> void addDisplaySlot(List<BookmarkDisplaySlot<T>> displaySlots, BookmarkDisplayEntry<T> entry, boolean shadow, int columns) {
		displaySlots.add(new BookmarkDisplaySlot<>(
			nextSlotIndex(displaySlots, entry, columns, entry.viewMode()),
			entry,
			shadow,
			isFirstOutput(displaySlots, entry)
		));
	}

	private static <T> int nextSlotIndex(List<BookmarkDisplaySlot<T>> displaySlots, BookmarkDisplayEntry<T> entry, int columns, BookmarkViewMode viewMode) {
		if (displaySlots.isEmpty()) {
			return 0;
		}
		BookmarkDisplaySlot<T> previousSlot = displaySlots.get(displaySlots.size() - 1);
		BookmarkDisplayEntry<T> previous = previousSlot.entry();
		int slotIndex = previousSlot.slotIndex() + 1;
		if (columns <= 0) {
			return slotIndex;
		}
		String previousGroupId = previous.metadata().groupId();
		String groupId = entry.metadata().groupId();
		if (!previousGroupId.equals(groupId) && slotIndex % columns != 0) {
			slotIndex += columns - slotIndex % columns;
		}
		if (viewMode == BookmarkViewMode.DEFAULT) {
			return slotIndex;
		}
		while (true) {
			boolean firstColumn = slotIndex % columns == 0;
			if (firstColumn && startsTodoListRow(previous, entry)) {
				return slotIndex;
			}
			if (!firstColumn && continuesTodoListRecipe(previous, entry)) {
				return slotIndex;
			}
			slotIndex++;
		}
	}

	private static boolean startsTodoListRow(BookmarkDisplayEntry<?> previous, BookmarkDisplayEntry<?> entry) {
		BookmarkItemMetadata metadata = entry.metadata();
		BookmarkItemMetadata previousMetadata = previous.metadata();
		return metadata.recipeUid() == null ||
			!previousMetadata.groupId().equals(metadata.groupId()) ||
			metadata.type() != BookmarkItemType.INGREDIENT ||
			!metadata.recipeUid().equals(previousMetadata.recipeUid());
	}

	private static boolean continuesTodoListRecipe(BookmarkDisplayEntry<?> previous, BookmarkDisplayEntry<?> entry) {
		BookmarkItemMetadata metadata = entry.metadata();
		BookmarkItemMetadata previousMetadata = previous.metadata();
		return metadata.type() != BookmarkItemType.ITEM &&
			previousMetadata.type() != BookmarkItemType.ITEM &&
			previousMetadata.groupId().equals(metadata.groupId()) &&
			metadata.recipeUid() != null &&
			metadata.recipeUid().equals(previousMetadata.recipeUid());
	}

	private static <T> boolean isFirstOutput(List<BookmarkDisplaySlot<T>> displaySlots, BookmarkDisplayEntry<T> entry) {
		if (entry.metadata().type() != BookmarkItemType.RESULT) {
			return false;
		}
		if (displaySlots.isEmpty()) {
			return true;
		}
		BookmarkDisplayEntry<T> previous = displaySlots.get(displaySlots.size() - 1).entry();
		return !entry.displayRecipeUid().equals(previous.displayRecipeUid());
	}

	private static boolean isCollapsedRecipeShadow(BookmarkGroup group, BookmarkDisplayEntry<?> entry) {
		return entry.displayRecipeUid()
			.filter(displayRecipeUid -> !displayRecipeUid.equals(entry.metadata().recipeUid()))
			.filter(group.collapsedRecipeIds()::contains)
			.isPresent();
	}

	private static <T> BookmarkDisplayEntry<T> createDisplayEntry(
		T item,
		int sourceIndex,
		BookmarkItemMetadata metadata,
		BookmarkGroup group
	) {
		return new BookmarkDisplayEntry<>(
			item,
			sourceIndex,
			metadata,
			group == null ? BookmarkViewMode.DEFAULT : group.viewMode(),
			Optional.ofNullable(metadata.recipeUid()),
			Optional.empty(),
			false,
			false
		);
	}

	private static <T> BookmarkDisplayEntry<T> createDisplayEntry(
		T item,
		int sourceIndex,
		BookmarkItemMetadata metadata,
		BookmarkGroup group,
		RecipeChainDetails details,
		RecipeChainItem chainItem
	) {
		ResourceLocation recipeUid = metadata.recipeUid();
		Optional<ResourceLocation> displayRecipeUid = getDisplayRecipeUid(group, details, recipeUid);
		RecipeChainItem displayChainItem = getDisplayChainItem(recipeUid, displayRecipeUid, chainItem);
		return new BookmarkDisplayEntry<>(
			item,
			sourceIndex,
			metadata,
			group.viewMode(),
			displayRecipeUid,
			Optional.of(displayChainItem),
			recipeUid != null && details.outputRecipes().contains(recipeUid),
			recipeUid != null && details.middleRecipes().contains(recipeUid)
		);
	}

	private static RecipeChainItem getDisplayChainItem(
		ResourceLocation recipeUid,
		Optional<ResourceLocation> displayRecipeUid,
		RecipeChainItem chainItem
	) {
		if (recipeUid != null && displayRecipeUid.isPresent() && !displayRecipeUid.get().equals(recipeUid)) {
			return chainItem.withRealProjection(chainItem.calculatedAmount(), chainItem.calculatedMultiplier());
		}
		return chainItem;
	}

	private static Optional<ResourceLocation> getDisplayRecipeUid(BookmarkGroup group, RecipeChainDetails details, ResourceLocation recipeUid) {
		if (recipeUid == null) {
			return Optional.empty();
		}
		for (ResourceLocation collapsedRecipeId : group.collapsedRecipeIds()) {
			Set<ResourceLocation> relations = details.recipeRelations().get(collapsedRecipeId);
			if (relations != null && relations.contains(recipeUid)) {
				return Optional.of(collapsedRecipeId);
			}
		}
		return Optional.of(recipeUid);
	}
}
