/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks;

import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainDetails;
import mezz.jei.gui.bookmarks.chain.RecipeChainMath;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class BookmarkGroupManager<T> {
	public static final int DEFAULT_GROUP_ID = 0;

	private final Map<Integer, BookmarkGroup> groups = new LinkedHashMap<>();
	private final Map<T, BookmarkItemMetadata> itemMetadata = new IdentityHashMap<>();
	private final Map<Integer, RecipeChainDetails> recipeChainDetails = new LinkedHashMap<>();
	private boolean recipeChainDetailsDirty = true;
	private @Nullable List<T> pendingOrderedItems;
	private int nextGroupId = DEFAULT_GROUP_ID + 1;

	public BookmarkGroupManager() {
		groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(DEFAULT_GROUP_ID, "Bookmarks"));
	}

	public void clear() {
		groups.clear();
		itemMetadata.clear();
		recipeChainDetails.clear();
		recipeChainDetailsDirty = true;
		pendingOrderedItems = null;
		nextGroupId = DEFAULT_GROUP_ID + 1;
		groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(DEFAULT_GROUP_ID, "Bookmarks"));
	}

	public List<BookmarkGroup> getGroups() {
		return List.copyOf(groups.values());
	}

	public Optional<BookmarkGroup> getGroup(int groupId) {
		return Optional.ofNullable(groups.get(groupId));
	}

	public int createGroup(String title) {
		int groupId = nextGroupId++;
		groups.put(groupId, new BookmarkGroup(groupId, title));
		return groupId;
	}

	public void addGroup(BookmarkGroup group) {
		groups.put(group.id(), group);
		if (group.id() >= nextGroupId) {
			nextGroupId = group.id() + 1;
		}
	}

	public void addItem(T item, boolean addToFront) {
		itemMetadata.putIfAbsent(item, BookmarkItemMetadata.defaultForGroup(DEFAULT_GROUP_ID));
	}

	public void removeItem(T item) {
		itemMetadata.remove(item);
	}

	public int getGroupId(T item) {
		return getItemMetadata(item).groupId();
	}

	public BookmarkItemMetadata getItemMetadata(T item) {
		return itemMetadata.getOrDefault(item, BookmarkItemMetadata.defaultForGroup(DEFAULT_GROUP_ID));
	}

	public void setItemMetadata(T item, BookmarkItemMetadata metadata) {
		int groupId = metadata.groupId();
		if (!groups.containsKey(groupId)) {
			metadata = metadata.withGroupId(DEFAULT_GROUP_ID);
		}
		itemMetadata.put(item, metadata);
	}

	public void moveItemToGroup(T item, int groupId) {
		if (!groups.containsKey(groupId)) {
			groupId = DEFAULT_GROUP_ID;
		}
		BookmarkItemMetadata metadata = getItemMetadata(item);
		itemMetadata.put(item, metadata.withGroupId(groupId));
	}

	public void setViewMode(int groupId, BookmarkViewMode viewMode) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.withViewMode(viewMode));
		}
	}

	public void toggleViewMode(int groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.toggleViewMode());
		}
	}

	public void toggleCollapsed(int groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.toggleCollapsed());
		}
	}

	public void setCraftingMode(int groupId, boolean craftingMode) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			BookmarkGroup updated = group.withCraftingMode(craftingMode);
			if (!craftingMode && !updated.collapsedRecipeIds().isEmpty()) {
				updated = updated.withCollapsedRecipeIds(Set.of());
			}
			groups.put(groupId, updated);
			if (!craftingMode) {
				recipeChainDetails.remove(groupId);
			}
		}
	}

	public void setCollapsedRecipeIds(int groupId, Set<ResourceLocation> collapsedRecipeIds) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.withCollapsedRecipeIds(collapsedRecipeIds));
		}
	}

	public boolean isCraftingMode(int groupId) {
		BookmarkGroup group = groups.get(groupId);
		return group != null && group.craftingMode();
	}

	public boolean removeGroup(int groupId) {
		if (groupId == DEFAULT_GROUP_ID || !groups.containsKey(groupId)) {
			return false;
		}
		groups.remove(groupId);
		recipeChainDetails.remove(groupId);
		int removed = groupId;
		itemMetadata.replaceAll((item, metadata) -> removed == metadata.groupId() ? metadata.withGroupId(DEFAULT_GROUP_ID) : metadata);
		return true;
	}

	public Optional<RecipeChainDetails> getRecipeChainDetails(int groupId) {
		ensureRecipeChainDetails();
		return Optional.ofNullable(recipeChainDetails.get(groupId));
	}

	public List<RecipeChainInput> getRecipeChainInputs(List<T> orderedItems, int groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group == null || !group.craftingMode()) {
			return List.of();
		}
		return createRecipeChainInputs(group, orderedItems);
	}

	public List<RecipeChainInput> getGroupRecipeInputs(List<T> orderedItems, int groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group == null) {
			return List.of();
		}
		return createRecipeChainInputs(group, orderedItems);
	}

	public Set<ResourceLocation> getCollapsedRecipeIds(int groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group == null || !group.craftingMode()) {
			return Set.of();
		}
		return group.collapsedRecipeIds();
	}

	public void refreshRecipeChainDetails(List<T> orderedItems) {
		recipeChainDetails.clear();
		for (BookmarkGroup group : groups.values()) {
			if (group.craftingMode()) {
				refreshRecipeChainDetails(group, orderedItems);
			}
		}
		recipeChainDetailsDirty = false;
	}

	public void markRecipeChainDetailsDirty(List<T> orderedItems) {
		recipeChainDetailsDirty = true;
		pendingOrderedItems = orderedItems;
	}

	private void ensureRecipeChainDetails() {
		if (recipeChainDetailsDirty && pendingOrderedItems != null) {
			refreshRecipeChainDetails(pendingOrderedItems);
		}
	}

	private void refreshRecipeChainDetails(BookmarkGroup group, List<T> orderedItems) {
		List<RecipeChainInput> inputs = createRecipeChainInputs(group, orderedItems);
		recipeChainDetails.put(group.id(), RecipeChainMath.refresh(inputs, group.collapsedRecipeIds()));
	}

	private List<RecipeChainInput> createRecipeChainInputs(BookmarkGroup group, List<T> orderedItems) {
		List<RecipeChainInput> inputs = new ArrayList<>();
		int gid = group.id();
		for (int index = 0; index < orderedItems.size(); index++) {
			T item = orderedItems.get(index);
			BookmarkItemMetadata metadata = getItemMetadata(item);
			if (gid == metadata.groupId()) {
				inputs.add(new RecipeChainInput(index, metadata));
			}
		}
		return List.copyOf(inputs);
	}

	public List<T> getVisibleItems(List<T> orderedItems) {
		return getDisplayEntries(orderedItems).stream()
			.map(BookmarkDisplayEntry::item)
			.toList();
	}

	public List<BookmarkDisplayEntry<T>> getDisplayEntries(List<T> orderedItems) {
		return getDisplaySlots(orderedItems).stream()
			.map(BookmarkDisplaySlot::entry)
			.toList();
	}

	public List<BookmarkDisplaySlot<T>> getDisplaySlots(List<T> orderedItems) {
		ensureRecipeChainDetails();
		return BookmarkDisplayGenerator.generate(
			orderedItems,
			this::getItemMetadata,
			groups,
			recipeChainDetails
		);
	}

	public List<BookmarkDisplaySlot<T>> getDisplaySlots(List<T> orderedItems, int columns) {
		return getDisplaySlots(orderedItems, columns, List.of());
	}

	public List<BookmarkDisplaySlot<T>> getDisplaySlots(
		List<T> orderedItems,
		int columns,
		List<Integer> usableColumnsPerRow
	) {
		ensureRecipeChainDetails();
		return BookmarkDisplayGenerator.generate(
			orderedItems,
			this::getItemMetadata,
			groups,
			recipeChainDetails,
			columns,
			usableColumnsPerRow
		);
	}
}
