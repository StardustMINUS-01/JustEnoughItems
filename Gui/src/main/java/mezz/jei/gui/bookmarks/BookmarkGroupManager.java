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

public class BookmarkGroupManager<T> {
	public static final String DEFAULT_GROUP_ID = "default";

	private final Map<String, BookmarkGroup> groups = new LinkedHashMap<>();
	private final Map<T, BookmarkItemMetadata> itemMetadata = new IdentityHashMap<>();
	private final Map<String, RecipeChainDetails> recipeChainDetails = new LinkedHashMap<>();
	private int nextGroupId = 1;

	public BookmarkGroupManager() {
		groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(DEFAULT_GROUP_ID, "Bookmarks"));
	}

	public void clear() {
		groups.clear();
		itemMetadata.clear();
		recipeChainDetails.clear();
		nextGroupId = 1;
		groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(DEFAULT_GROUP_ID, "Bookmarks"));
	}

	public List<BookmarkGroup> getGroups() {
		return List.copyOf(groups.values());
	}

	public Optional<BookmarkGroup> getGroup(String groupId) {
		return Optional.ofNullable(groups.get(groupId));
	}

	public String createGroup(String title) {
		String groupId = "group_" + nextGroupId++;
		groups.put(groupId, new BookmarkGroup(groupId, title));
		return groupId;
	}

	public void addGroup(BookmarkGroup group) {
		groups.put(group.id(), group);
		if (group.id().startsWith("group_")) {
			try {
				int groupNumber = Integer.parseInt(group.id().substring("group_".length()));
				nextGroupId = Math.max(nextGroupId, groupNumber + 1);
			} catch (NumberFormatException ignored) {
			}
		}
	}

	public void addItem(T item, boolean addToFront) {
		itemMetadata.putIfAbsent(item, BookmarkItemMetadata.defaultForGroup(DEFAULT_GROUP_ID));
	}

	public void removeItem(T item) {
		itemMetadata.remove(item);
	}

	public String getGroupId(T item) {
		return getItemMetadata(item).groupId();
	}

	public BookmarkItemMetadata getItemMetadata(T item) {
		return itemMetadata.getOrDefault(item, BookmarkItemMetadata.defaultForGroup(DEFAULT_GROUP_ID));
	}

	public void setItemMetadata(T item, BookmarkItemMetadata metadata) {
		String groupId = metadata.groupId();
		if (!groups.containsKey(groupId)) {
			metadata = metadata.withGroupId(DEFAULT_GROUP_ID);
		}
		itemMetadata.put(item, metadata);
	}

	public void moveItemToGroup(T item, String groupId) {
		if (!groups.containsKey(groupId)) {
			groupId = DEFAULT_GROUP_ID;
		}
		BookmarkItemMetadata metadata = getItemMetadata(item);
		itemMetadata.put(item, metadata.withGroupId(groupId));
	}

	public void setViewMode(String groupId, BookmarkViewMode viewMode) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.withViewMode(viewMode));
		}
	}

	public void toggleViewMode(String groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.toggleViewMode());
		}
	}

	public void toggleCollapsed(String groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.toggleCollapsed());
		}
	}

	public void setCraftingMode(String groupId, boolean craftingMode) {
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

	public void setCollapsedRecipeIds(String groupId, Set<ResourceLocation> collapsedRecipeIds) {
		BookmarkGroup group = groups.get(groupId);
		if (group != null) {
			groups.put(groupId, group.withCollapsedRecipeIds(collapsedRecipeIds));
		}
	}

	public boolean isCraftingMode(String groupId) {
		BookmarkGroup group = groups.get(groupId);
		return group != null && group.craftingMode();
	}

	public boolean removeGroup(String groupId) {
		if (DEFAULT_GROUP_ID.equals(groupId) || !groups.containsKey(groupId)) {
			return false;
		}
		groups.remove(groupId);
		recipeChainDetails.remove(groupId);
		itemMetadata.replaceAll((item, metadata) -> groupId.equals(metadata.groupId()) ? metadata.withGroupId(DEFAULT_GROUP_ID) : metadata);
		return true;
	}

	public Optional<RecipeChainDetails> getRecipeChainDetails(String groupId) {
		return Optional.ofNullable(recipeChainDetails.get(groupId));
	}

	public List<RecipeChainInput> getRecipeChainInputs(List<T> orderedItems, String groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group == null || !group.craftingMode()) {
			return List.of();
		}
		return createRecipeChainInputs(group, orderedItems);
	}

	public List<RecipeChainInput> getGroupRecipeInputs(List<T> orderedItems, String groupId) {
		BookmarkGroup group = groups.get(groupId);
		if (group == null) {
			return List.of();
		}
		return createRecipeChainInputs(group, orderedItems);
	}

	public Set<ResourceLocation> getCollapsedRecipeIds(String groupId) {
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
	}

	private void refreshRecipeChainDetails(BookmarkGroup group, List<T> orderedItems) {
		List<RecipeChainInput> inputs = createRecipeChainInputs(group, orderedItems);
		recipeChainDetails.put(group.id(), RecipeChainMath.refresh(inputs, group.collapsedRecipeIds()));
	}

	private List<RecipeChainInput> createRecipeChainInputs(BookmarkGroup group, List<T> orderedItems) {
		List<RecipeChainInput> inputs = new ArrayList<>();
		for (int index = 0; index < orderedItems.size(); index++) {
			T item = orderedItems.get(index);
			BookmarkItemMetadata metadata = getItemMetadata(item);
			if (group.id().equals(metadata.groupId())) {
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
