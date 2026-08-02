package mezz.jei.gui.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class BookmarkGroupConfigSerializer {
	public static final String MARKER_GROUP = "G:";
	public static final String MARKER_BOOKMARK_GROUP = "BG:";
	public static final String MARKER_BOOKMARK_METADATA = "BM:";

	private BookmarkGroupConfigSerializer() {
	}

	public static String serializeGroup(BookmarkGroup group) {
		JsonObject json = new JsonObject();
		json.addProperty("id", group.id());
		json.addProperty("title", group.title());
		json.addProperty("newLine", group.newLine());
		json.addProperty("resultOnly", group.resultOnly());
		json.addProperty("crafting", group.craftingMode());
		if (!group.collapsedRecipeIds().isEmpty()) {
			JsonArray collapsedRecipes = new JsonArray();
			group.collapsedRecipeIds().stream()
				.map(ResourceLocation::toString)
				.sorted()
				.forEach(collapsedRecipes::add);
			json.add("collapsedRecipes", collapsedRecipes);
		}
		return MARKER_GROUP + json;
	}

	public static Optional<BookmarkGroup> deserializeGroup(String line) {
		if (!line.startsWith(MARKER_GROUP)) {
			return Optional.empty();
		}

		try {
			JsonObject json = JsonParser.parseString(line.substring(MARKER_GROUP.length())).getAsJsonObject();
			String id = json.get("id").getAsString();
			String title = json.get("title").getAsString();
			boolean newLine = json.has("newLine") && json.get("newLine").getAsBoolean();
			boolean resultOnly = json.has("resultOnly") && json.get("resultOnly").getAsBoolean();
			boolean crafting = json.has("crafting") && json.get("crafting").getAsBoolean();
			Set<ResourceLocation> collapsedRecipeIds = json.has("collapsedRecipes") ?
				json.getAsJsonArray("collapsedRecipes")
					.asList()
					.stream()
					.map(element -> ResourceLocation.parse(element.getAsString()))
					.collect(Collectors.toUnmodifiableSet()) :
				Set.of();
			return Optional.of(new BookmarkGroup(id, title, newLine, resultOnly, crafting, collapsedRecipeIds));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	public static String serializeBookmarkGroupId(String groupId) {
		return MARKER_BOOKMARK_GROUP + groupId;
	}

	public static Optional<String> deserializeBookmarkGroupId(String line) {
		if (!line.startsWith(MARKER_BOOKMARK_GROUP)) {
			return Optional.empty();
		}

		String groupId = line.substring(MARKER_BOOKMARK_GROUP.length()).trim();
		if (groupId.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(groupId);
	}

	public static String serializeBookmarkMetadata(BookmarkItemMetadata metadata) {
		JsonObject json = new JsonObject();
		json.addProperty("groupId", metadata.groupId());
		json.addProperty("type", metadata.type().name());
		json.addProperty("multiplier", metadata.multiplier());
		json.addProperty("factor", metadata.factor());
		json.addProperty("chance", metadata.chance());
		if (metadata.recipeUid() != null) {
			if (metadata.recipeTypeUid() != null) {
				json.addProperty("recipeTypeUid", metadata.recipeTypeUid().toString());
			}
			json.addProperty("recipeUid", metadata.recipeUid().toString());
		}
		if (!metadata.permutations().isEmpty()) {
			JsonArray permutations = new JsonArray();
			metadata.permutations().stream()
				.sorted(Comparator.naturalOrder())
				.map(BookmarkGroupConfigSerializer::serializePermutation)
				.forEach(permutations::add);
			json.add("permutations", permutations);
		}
		if (metadata.containerItem() != null) {
			json.add("containerItem", serializePermutation(metadata.containerItem()));
		}
		if (metadata.containerItemCraftingUses() != 1) {
			json.addProperty("containerItemCraftingUses", metadata.containerItemCraftingUses());
		}
		if (metadata.brokenContainerItem() != null) {
			json.add("brokenContainerItem", serializePermutation(metadata.brokenContainerItem()));
		}
		return MARKER_BOOKMARK_METADATA + json;
	}

	private static JsonObject serializePermutation(BookmarkIngredientKey permutation) {
		JsonObject json = new JsonObject();
		json.addProperty("type", permutation.ingredientTypeUid());
		json.addProperty("uid", permutation.ingredientUid());
		if (permutation.serializedIngredient() != null) {
			json.addProperty("ingredient", permutation.serializedIngredient());
		}
		return json;
	}

	public static Optional<BookmarkItemMetadata> deserializeBookmarkMetadata(String line) {
		Optional<String> legacyGroupId = deserializeBookmarkGroupId(line);
		if (legacyGroupId.isPresent()) {
			return Optional.of(BookmarkItemMetadata.defaultForGroup(legacyGroupId.get()));
		}
		if (!line.startsWith(MARKER_BOOKMARK_METADATA)) {
			return Optional.empty();
		}

		try {
			JsonObject json = JsonParser.parseString(line.substring(MARKER_BOOKMARK_METADATA.length())).getAsJsonObject();
			String groupId = json.has("groupId") ? json.get("groupId").getAsString() : BookmarkGroupManager.DEFAULT_GROUP_ID;
			BookmarkItemType type = json.has("type") ?
				BookmarkItemType.valueOf(json.get("type").getAsString()) :
				BookmarkItemType.ITEM;
			long multiplier = json.has("multiplier") ? json.get("multiplier").getAsLong() : 1;
			long factor = json.has("factor") ? json.get("factor").getAsLong() : 1;
			long chance = json.has("chance") ? json.get("chance").getAsLong() : BookmarkItemMetadata.CHANCE_FULL;
			ResourceLocation recipeTypeUid = json.has("recipeTypeUid") ? ResourceLocation.parse(json.get("recipeTypeUid").getAsString()) : null;
			ResourceLocation recipeUid = json.has("recipeUid") ? ResourceLocation.parse(json.get("recipeUid").getAsString()) : null;
			Set<BookmarkIngredientKey> permutations = json.has("permutations") ?
				json.getAsJsonArray("permutations")
					.asList()
					.stream()
					.map(BookmarkGroupConfigSerializer::deserializePermutation)
					.collect(Collectors.toUnmodifiableSet()) :
				Set.of();
			BookmarkIngredientKey containerItem = json.has("containerItem") ?
				deserializePermutation(json.get("containerItem")) :
				null;
			long containerItemCraftingUses = json.has("containerItemCraftingUses") ?
				json.get("containerItemCraftingUses").getAsLong() :
				1;
			BookmarkIngredientKey brokenContainerItem = json.has("brokenContainerItem") ?
				deserializePermutation(json.get("brokenContainerItem")) :
				null;
			return Optional.of(new BookmarkItemMetadata(
				groupId,
				type,
				multiplier,
				factor,
				chance,
				recipeTypeUid,
				recipeUid,
				permutations,
				containerItem,
				containerItemCraftingUses,
				brokenContainerItem
			));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private static BookmarkIngredientKey deserializePermutation(JsonElement element) {
		if (element.isJsonPrimitive()) {
			return BookmarkIngredientKey.legacy(element.getAsString());
		}
		JsonObject json = element.getAsJsonObject();
		String type = json.has("type") ? json.get("type").getAsString() : BookmarkIngredientKey.UNKNOWN_TYPE_UID;
		String uid = json.has("uid") ? json.get("uid").getAsString() : "fallback:unknown";
		String ingredient = json.has("ingredient") ? json.get("ingredient").getAsString() : null;
		return new BookmarkIngredientKey(type, uid, ingredient);
	}
}
