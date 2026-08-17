package mezz.jei.gui.bookmarks;

import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainItem;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
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
		return generate(orderedItems, metadataGetter, groups, recipeChainDetails, columns, List.of());
	}

	public static <T> List<BookmarkDisplaySlot<T>> generate(
		List<T> orderedItems,
		Function<T, BookmarkItemMetadata> metadataGetter,
		Map<String, BookmarkGroup> groups,
		Map<String, RecipeChainDetails> recipeChainDetails,
		int columns,
		List<Integer> usableColumnsPerRow
	) {
		List<BookmarkDisplaySlot<T>> displaySlots = new ArrayList<>();
		Set<ResourceLocation> emittedBlocks = new HashSet<>();
		BookmarkRowLayout.RowLayout rowLayout = BookmarkRowLayout.RowLayout.create(columns, usableColumnsPerRow);
		for (int sourceIndex = 0; sourceIndex < orderedItems.size(); sourceIndex++) {
			T item = orderedItems.get(sourceIndex);
			BookmarkItemMetadata metadata = metadataGetter.apply(item);
			String groupId = metadata.groupId();
			BookmarkGroup group = groups.get(groupId);
			if (group != null && group.craftingMode()) {
				RecipeChainDetails details = recipeChainDetails.get(group.id());
				if (details == null) {
					continue;
				}
				ResourceLocation blockRoot = details.itemToRecipe().get(sourceIndex);
				if (blockRoot != null) {
					if (emittedBlocks.add(blockRoot)) {
						addCollapsedBlock(displaySlots, orderedItems, blockRoot, details, group, rowLayout);
					}
					continue;
				}
				if (isHiddenCraftingInput(group, groupId, metadata)) {
					continue;
				}
				if (isCatalystInCollapsedClosure(group, details, metadata)) {
					continue;
				}
				addCraftingDisplaySlot(displaySlots, item, sourceIndex, metadata, group, details, rowLayout);
			} else if (group == null || group.viewMode() != BookmarkViewMode.COLLAPSED ||
				BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId)) {
				addDisplaySlot(displaySlots, createDisplayEntry(item, sourceIndex, metadata, group), false, rowLayout);
			} else if (isResultOnlyGroupOutput(metadata)) {
				addDisplaySlot(displaySlots, createDisplayEntry(item, sourceIndex, metadata, group), false, rowLayout);
			}
		}
		return attachBorders(displaySlots, groups, rowLayout);
	}

	private static <T> List<BookmarkDisplaySlot<T>> attachBorders(
		List<BookmarkDisplaySlot<T>> displaySlots,
		Map<String, BookmarkGroup> groups,
		BookmarkRowLayout.RowLayout rowLayout
	) {
		if (displaySlots.isEmpty()) {
			return List.copyOf(displaySlots);
		}
		Map<Integer, String> borderKeys = new HashMap<>();
		for (BookmarkDisplaySlot<T> slot : displaySlots) {
			String key = getBorderKey(slot.entry(), groups);
			if (key != null) {
				borderKeys.put(slot.slotIndex(), key);
			}
		}
		if (borderKeys.isEmpty()) {
			return List.copyOf(displaySlots);
		}
		List<BookmarkDisplaySlot<T>> borderedSlots = new ArrayList<>(displaySlots.size());
		for (BookmarkDisplaySlot<T> slot : displaySlots) {
			int slotIndex = slot.slotIndex();
			String key = borderKeys.get(slotIndex);
			if (key == null) {
				borderedSlots.add(slot);
				continue;
			}
			int above = BookmarkRowLayout.above(slotIndex, rowLayout);
			int below = BookmarkRowLayout.below(slotIndex, rowLayout);
			BookmarkDisplayEntry<T> entry = slot.entry().withBorder(new BookmarkSlotBorder(
				getBorderColor(slot.entry(), groups),
				(rowLayout.columns() > 0 && BookmarkRowLayout.isRowStart(slotIndex, rowLayout)) || !key.equals(borderKeys.get(slotIndex - 1)),
				(rowLayout.columns() > 0 && BookmarkRowLayout.isRowEnd(slotIndex, rowLayout)) || !key.equals(borderKeys.get(slotIndex + 1)),
				rowLayout.columns() > 0 && !key.equals(above >= 0 ? borderKeys.get(above) : null),
				rowLayout.columns() > 0 && !key.equals(below >= 0 ? borderKeys.get(below) : null)
			));
			borderedSlots.add(new BookmarkDisplaySlot<>(slotIndex, entry, slot.shadow(), slot.firstOutput()));
		}
		return List.copyOf(borderedSlots);
	}

	private static <T> String getBorderKey(BookmarkDisplayEntry<T> entry, Map<String, BookmarkGroup> groups) {
		String groupId = entry.metadata().groupId();
		BookmarkGroup group = groups.get(groupId);
		if (group != null && group.viewMode() == BookmarkViewMode.COLLAPSED) {
			return "group:" + groupId;
		}
		ResourceLocation blockId = entry.collapsedBlockId();
		if (blockId != null) {
			return "recipe:" + blockId;
		}
		return null;
	}

	private static <T> int getBorderColor(BookmarkDisplayEntry<T> entry, Map<String, BookmarkGroup> groups) {
		BookmarkGroup group = groups.get(entry.metadata().groupId());
		if (group != null && group.viewMode() == BookmarkViewMode.COLLAPSED) {
			return group.craftingMode() ?
				BookmarkSlotBorder.GROUP_CHAIN_COLOR :
				BookmarkSlotBorder.GROUP_NONE_COLOR;
		}
		return BookmarkSlotBorder.RECIPE_COLOR;
	}

	private static boolean isHiddenCraftingInput(
		BookmarkGroup group,
		String groupId,
		BookmarkItemMetadata metadata
	) {
		return group.viewMode() != BookmarkViewMode.TODO_LIST &&
			!BookmarkGroupManager.DEFAULT_GROUP_ID.equals(groupId) &&
			(metadata.type().isGraphInput() || metadata.type().isCatalyst());
	}

	private static boolean isResultOnlyGroupOutput(BookmarkItemMetadata metadata) {
		return !metadata.type().isGraphInput() &&
			!metadata.type().isCatalyst();
	}

	private static <T> void addCollapsedBlock(
		List<BookmarkDisplaySlot<T>> displaySlots,
		List<T> orderedItems,
		ResourceLocation blockRoot,
		RecipeChainDetails details,
		BookmarkGroup group,
		BookmarkRowLayout.RowLayout rowLayout
	) {
		RecipeChainDetails.CollapsedBlock block = details.collapsedBlocks().get(blockRoot);
		if (block == null) {
			return;
		}
		for (RecipeChainDetails.CollapsedBlockItem blockItem : block.items()) {
			BookmarkItemMetadata metadata = blockItem.metadata();
			boolean anchor = blockItem.anchor();
			if (group.viewMode() != BookmarkViewMode.TODO_LIST && !metadata.type().isGraphOutput()) {
				continue;
			}
			int sourceIndex = blockItem.sourceIndex();
			if (sourceIndex < 0 || sourceIndex >= orderedItems.size()) {
				continue;
			}
			RecipeChainItem chainItem = blockItem.chainItem();
			BookmarkDisplayEntry<T> entry = new BookmarkDisplayEntry<>(
				orderedItems.get(sourceIndex),
				sourceIndex,
				metadata,
				group.viewMode(),
				Optional.of(blockRoot),
				Optional.of(chainItem),
				anchor && details.outputRecipes().contains(blockRoot),
				anchor && details.middleRecipes().contains(blockRoot),
				blockRoot,
				null
			);
			addDisplaySlot(displaySlots, entry, !anchor, rowLayout);
		}
	}

	private static boolean isCatalystInCollapsedClosure(BookmarkGroup group, RecipeChainDetails details, BookmarkItemMetadata metadata) {
		if (!metadata.type().isCatalyst()) {
			return false;
		}
		ResourceLocation recipeUid = metadata.recipeUid();
		if (recipeUid == null) {
			return false;
		}
		return group.collapsedRecipeIds().stream()
			.anyMatch(root -> details.recipeRelations().getOrDefault(root, Set.of(root)).contains(recipeUid));
	}

	private static <T> void addCraftingDisplaySlot(
		List<BookmarkDisplaySlot<T>> displaySlots,
		T item,
		int sourceIndex,
		BookmarkItemMetadata metadata,
		BookmarkGroup group,
		RecipeChainDetails details,
		BookmarkRowLayout.RowLayout rowLayout
	) {
		RecipeChainItem chainItem = details.calculatedItems().get(sourceIndex);
		if (chainItem == null) {
			if (metadata.type().isCatalyst() || metadata.type() == BookmarkItemType.ITEM) {
				addDisplaySlot(displaySlots, createDisplayEntry(item, sourceIndex, metadata, group), false, rowLayout);
			}
			return;
		}
		if (group.viewMode() != BookmarkViewMode.TODO_LIST && metadata.type().isGraphInput()) {
			return;
		}
		addDisplaySlot(displaySlots, createDisplayEntry(item, sourceIndex, metadata, group, details, chainItem), false, rowLayout);
	}

	private static <T> void addDisplaySlot(
		List<BookmarkDisplaySlot<T>> displaySlots,
		BookmarkDisplayEntry<T> entry,
		boolean shadow,
		BookmarkRowLayout.RowLayout rowLayout
	) {
		if (!displaySlots.isEmpty()) {
			int slotIndex = nextSlotIndex(displaySlots, entry, rowLayout);
			if (entry.viewMode() == BookmarkViewMode.COLLAPSED && rowLayout.columns() > 0) {
				BookmarkDisplaySlot<T> previousSlot = displaySlots.get(displaySlots.size() - 1);
				boolean sameGroup = previousSlot.entry().metadata().groupId().equals(entry.metadata().groupId());
				if (sameGroup && BookmarkRowLayout.isRowStart(slotIndex, rowLayout)) {
					return;
				}
			}
		}
		displaySlots.add(new BookmarkDisplaySlot<>(
			nextSlotIndex(displaySlots, entry, rowLayout),
			entry,
			shadow,
			isFirstOutput(displaySlots, entry)
		));
	}

	private static <T> int nextSlotIndex(
		List<BookmarkDisplaySlot<T>> displaySlots,
		BookmarkDisplayEntry<T> entry,
		BookmarkRowLayout.RowLayout rowLayout
	) {
		if (displaySlots.isEmpty()) {
			return 0;
		}
		BookmarkDisplaySlot<T> previousSlot = displaySlots.get(displaySlots.size() - 1);
		BookmarkDisplayEntry<T> previous = previousSlot.entry();
		int slotIndex = previousSlot.slotIndex() + 1;
		if (rowLayout.columns() <= 0) {
			return slotIndex;
		}
		String previousGroupId = previous.metadata().groupId();
		String groupId = entry.metadata().groupId();
		if (!previousGroupId.equals(groupId) && !BookmarkRowLayout.isRowStart(slotIndex, rowLayout)) {
			slotIndex = BookmarkRowLayout.nextRowStart(slotIndex, rowLayout);
		}
		if (entry.viewMode() != BookmarkViewMode.TODO_LIST) {
			return slotIndex;
		}
		while (true) {
			boolean firstColumn = BookmarkRowLayout.isRowStart(slotIndex, rowLayout);
			if (firstColumn && startsRecipeRow(previous, entry)) {
				return slotIndex;
			}
			if (!firstColumn && continuesRecipe(previous, entry)) {
				return slotIndex;
			}
			slotIndex++;
		}
	}

	private static boolean startsRecipeRow(BookmarkDisplayEntry<?> previous, BookmarkDisplayEntry<?> entry) {
		if (sameCollapsedBlock(previous, entry)) {
			return false;
		}
		BookmarkItemMetadata metadata = entry.metadata();
		BookmarkItemMetadata previousMetadata = previous.metadata();
		ResourceLocation recipeUid = entry.displayRecipeUid().orElse(null);
		ResourceLocation previousRecipeUid = previous.displayRecipeUid().orElse(null);
		return recipeUid == null ||
			!previousMetadata.groupId().equals(metadata.groupId()) ||
			!metadata.type().isGraphInput() ||
			!recipeUid.equals(previousRecipeUid);
	}

	private static boolean continuesRecipe(BookmarkDisplayEntry<?> previous, BookmarkDisplayEntry<?> entry) {
		if (sameCollapsedBlock(previous, entry)) {
			return true;
		}
		BookmarkItemMetadata metadata = entry.metadata();
		BookmarkItemMetadata previousMetadata = previous.metadata();
		ResourceLocation recipeUid = entry.displayRecipeUid().orElse(null);
		ResourceLocation previousRecipeUid = previous.displayRecipeUid().orElse(null);
		return metadata.type().isRecipeAssociated() &&
			previousMetadata.type().isRecipeAssociated() &&
			previousMetadata.groupId().equals(metadata.groupId()) &&
			recipeUid != null &&
			recipeUid.equals(previousRecipeUid);
	}

	private static boolean sameCollapsedBlock(BookmarkDisplayEntry<?> previous, BookmarkDisplayEntry<?> entry) {
		ResourceLocation previousBlockId = previous.collapsedBlockId();
		return previousBlockId != null && previousBlockId.equals(entry.collapsedBlockId());
	}

	private static <T> boolean isFirstOutput(List<BookmarkDisplaySlot<T>> displaySlots, BookmarkDisplayEntry<T> entry) {
		if (!entry.metadata().type().isGraphOutput()) {
			return false;
		}
		if (displaySlots.isEmpty()) {
			return true;
		}
		BookmarkDisplayEntry<T> previous = displaySlots.get(displaySlots.size() - 1).entry();
		return !entry.displayRecipeUid().equals(previous.displayRecipeUid());
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
		return new BookmarkDisplayEntry<>(
			item,
			sourceIndex,
			metadata,
			group.viewMode(),
			Optional.ofNullable(recipeUid),
			Optional.of(chainItem),
			recipeUid != null && details.outputRecipes().contains(recipeUid),
			recipeUid != null && details.middleRecipes().contains(recipeUid)
		);
	}
}
