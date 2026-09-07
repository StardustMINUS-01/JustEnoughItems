package mezz.jei.gui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.config.file.serializers.BookmarkIngredientKeySerializer;
import mezz.jei.gui.config.file.serializers.RecipeBookmarkSerializer;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class BookmarkJsonSerializer {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String TYPE_GROUP = "group";
	private static final String TYPE_RECIPE = "recipe";
	private static final String TYPE_ITEM = "item";

	private BookmarkJsonSerializer() {
	}

	public static List<JsonElement> serialize(BookmarkList bookmarkList, IIngredientManager ingredientManager) {
		List<JsonElement> elements = new ArrayList<>();
		for (BookmarkGroup group : bookmarkList.getBookmarkGroups()) {
			if (BookmarkGroupManager.DEFAULT_GROUP_ID != group.id()) {
				elements.add(serializeGroup(group));
			}
		}
		for (IBookmark bookmark : bookmarkList.getBookmarks()) {
			elements.add(serializeBookmark(bookmark, bookmarkList.getBookmarkMetadata(bookmark), ingredientManager));
		}
		return List.copyOf(elements);
	}

	public static JsonObject serializeGroup(BookmarkGroup group) {
		JsonObject json = new JsonObject();
		json.addProperty("type", TYPE_GROUP);
		json.addProperty("id", group.id());
		json.addProperty("title", group.title());
		json.addProperty("viewMode", group.viewMode().name());
		json.addProperty("collapsed", group.collapsed());
		json.addProperty("crafting", group.craftingMode());
		JsonArray arr = new JsonArray();
		group.collapsedRecipeIds().stream().map(ResourceLocation::toString).forEach(arr::add);
		json.add("collapsedRecipes", arr);
		return json;
	}

	public static Optional<BookmarkGroup> deserializeGroup(JsonElement element) {
		if (!element.isJsonObject()) {
			return Optional.empty();
		}
		JsonObject json = element.getAsJsonObject();
		if (!json.has("type") || !TYPE_GROUP.equals(json.get("type").getAsString())) {
			return Optional.empty();
		}
		Optional<BookmarkGroup> group = BookmarkGroupConfigSerializer.deserializeGroupJson(json);
		if (group.isEmpty()) {
			LOGGER.error("Failed to load bookmark group from json:\n{}", element);
		}
		return group;
	}

	private static JsonObject serializeBookmark(
		IBookmark bookmark,
		BookmarkItemMetadata metadata,
		IIngredientManager ingredientManager
	) {
		BookmarkIngredientKey ingredientKey = BookmarkItemMetadataFactory.createPermutationKey(
			bookmark.getElement().getTypedIngredient(),
			ingredientManager
		);
		if (bookmark instanceof RecipeBookmark<?, ?> recipeBookmark) {
			JsonObject json = new JsonObject();
			json.addProperty("type", TYPE_RECIPE);
			addGroup(json, metadata);
			json.addProperty("kind", metadata.type().name());
			json.addProperty("recipeType", recipeBookmark.getRecipeCategory().getRecipeType().getUid().toString());
			json.addProperty("recipe", recipeBookmark.getRecipeUid().toString());
			json.add("ingredient", BookmarkIngredientKeySerializer.serialize(ingredientKey));
			addMetadata(json, metadata);
			return json;
		}
		JsonObject json = new JsonObject();
		json.addProperty("type", TYPE_ITEM);
		addGroup(json, metadata);
		json.add("ingredient", BookmarkIngredientKeySerializer.serialize(ingredientKey));
		addMetadata(json, metadata);
		return json;
	}

	public static void deserialize(
		List<JsonElement> elements,
		BookmarkList bookmarkList,
		@Nullable RecipeBookmarkSerializer recipeBookmarkSerializer,
		IIngredientManager ingredientManager
	) {
		for (JsonElement element : elements) {
			try {
				Optional<BookmarkGroup> group = deserializeGroup(element);
				if (group.isPresent()) {
					bookmarkList.addGroupFromConfig(group.get());
					continue;
				}
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject json = element.getAsJsonObject();
				String type = json.get("type").getAsString();
				switch (type) {
					case TYPE_RECIPE -> deserializeRecipe(json, bookmarkList, recipeBookmarkSerializer, ingredientManager);
					case TYPE_ITEM -> deserializeItem(json, bookmarkList, ingredientManager);
					default -> LOGGER.error("Failed to load unknown bookmark type from json:\n{}", element);
				}
			} catch (RuntimeException e) {
				LOGGER.error("Failed to load bookmark from json:\n{}", element, e);
			}
		}
		bookmarkList.notifyListenersOfChange();
	}

	private static void deserializeRecipe(
		JsonObject json,
		BookmarkList bookmarkList,
		@Nullable RecipeBookmarkSerializer recipeBookmarkSerializer,
		IIngredientManager ingredientManager
	) {
		if (recipeBookmarkSerializer == null) {
			return;
		}
		int groupId = json.has("group") ? json.get("group").getAsInt() : BookmarkGroupManager.DEFAULT_GROUP_ID;
		BookmarkItemType type = json.has("kind") ?
			BookmarkItemType.valueOf(json.get("kind").getAsString()) :
			BookmarkItemType.RESULT;
		RecipeIngredientRole displayRole = type.recipeRole() == RecipeIngredientRole.INPUT ?
			RecipeIngredientRole.INPUT :
			RecipeIngredientRole.OUTPUT;
		ResourceLocation recipeTypeUid = new ResourceLocation(json.get("recipeType").getAsString());
		ResourceLocation recipeUid = new ResourceLocation(json.get("recipe").getAsString());
		BookmarkIngredientKey ingredientKey = BookmarkIngredientKeySerializer.deserialize(json.get("ingredient"));
		Optional<ITypedIngredient<?>> output = resolveIngredient(ingredientKey, ingredientManager);
		if (output.isEmpty()) {
			LOGGER.error("Failed to load bookmarked recipe ingredient from json:\n{}", json);
			return;
		}
		Optional<RecipeBookmark<?, ?>> bookmark = recipeBookmarkSerializer.createBookmark(
			recipeTypeUid,
			recipeUid,
			output.get(),
			displayRole
		);
		if (bookmark.isEmpty()) {
			LOGGER.error("Failed to load bookmarked recipe from json:\n{}", json);
			return;
		}
		IBookmark resolvedBookmark = bookmark.get();
		if (BookmarkGroupManager.DEFAULT_GROUP_ID != groupId) {
			resolvedBookmark = ((RecipeBookmark<?, ?>) bookmark.get()).withEqualityScope(groupId);
		}
		bookmarkList.addToListWithoutNotifying(resolvedBookmark, false);
		bookmarkList.moveBookmarkMetadataFromConfig(
			resolvedBookmark,
			deserializeMetadata(json, groupId, type, recipeTypeUid, recipeUid)
		);
	}

	private static void deserializeItem(JsonObject json, BookmarkList bookmarkList, IIngredientManager ingredientManager) {
		int groupId = json.has("group") ? json.get("group").getAsInt() : BookmarkGroupManager.DEFAULT_GROUP_ID;
		BookmarkIngredientKey ingredientKey = BookmarkIngredientKeySerializer.deserialize(json.get("ingredient"));
		Optional<ITypedIngredient<?>> ingredient = resolveIngredient(ingredientKey, ingredientManager);
		if (ingredient.isEmpty()) {
			LOGGER.error("Failed to load bookmarked item from json:\n{}", json);
			return;
		}
		IBookmark bookmark = IngredientBookmark.create(ingredient.get(), ingredientManager);
		bookmarkList.addToListWithoutNotifying(bookmark, false);
		bookmarkList.moveBookmarkMetadataFromConfig(
			bookmark,
			deserializeMetadata(json, groupId, BookmarkItemType.ITEM, null, null)
		);
	}

	private static BookmarkItemMetadata deserializeMetadata(
		JsonObject json,
		int groupId,
		BookmarkItemType type,
		@Nullable ResourceLocation recipeTypeUid,
		@Nullable ResourceLocation recipeUid
	) {
		long multiplier = json.has("multiplier") ? json.get("multiplier").getAsLong() : 1;
		long factor = json.has("factor") ? json.get("factor").getAsLong() : 1;
		long chance = json.has("chance") ? json.get("chance").getAsLong() : BookmarkItemMetadata.CHANCE_FULL;
		Set<BookmarkIngredientKey> permutations = json.has("permutations") ?
			json.getAsJsonArray("permutations")
				.asList()
				.stream()
				.map(BookmarkIngredientKeySerializer::deserialize)
				.collect(Collectors.toUnmodifiableSet()) :
			Set.of();
		BookmarkIngredientKey containerItem = json.has("containerItem") ?
			BookmarkIngredientKeySerializer.deserialize(json.get("containerItem")) :
			null;
		long containerItemCraftingUses = json.has("containerItemCraftingUses") ?
			json.get("containerItemCraftingUses").getAsLong() :
			1;
		BookmarkIngredientKey brokenContainerItem = json.has("brokenContainerItem") ?
			BookmarkIngredientKeySerializer.deserialize(json.get("brokenContainerItem")) :
			null;
		return new BookmarkItemMetadata(
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
		);
	}

	private static void addGroup(JsonObject json, BookmarkItemMetadata metadata) {
		if (BookmarkGroupManager.DEFAULT_GROUP_ID != metadata.groupId()) {
			json.addProperty("group", metadata.groupId());
		}
	}

	private static void addMetadata(JsonObject json, BookmarkItemMetadata metadata) {
		if (metadata.multiplier() != 1) {
			json.addProperty("multiplier", metadata.multiplier());
		}
		if (metadata.factor() != 1) {
			json.addProperty("factor", metadata.factor());
		}
		if (metadata.chance() != BookmarkItemMetadata.CHANCE_FULL) {
			json.addProperty("chance", metadata.chance());
		}
		if (!metadata.permutations().isEmpty()) {
			JsonArray permutations = new JsonArray();
			metadata.permutations().stream()
				.sorted(Comparator.naturalOrder())
				.map(BookmarkIngredientKeySerializer::serialize)
				.forEach(permutations::add);
			json.add("permutations", permutations);
		}
		if (metadata.containerItem() != null) {
			json.add("containerItem", BookmarkIngredientKeySerializer.serialize(metadata.containerItem()));
		}
		if (metadata.containerItemCraftingUses() != 1) {
			json.addProperty("containerItemCraftingUses", metadata.containerItemCraftingUses());
		}
		if (metadata.brokenContainerItem() != null) {
			json.add("brokenContainerItem", BookmarkIngredientKeySerializer.serialize(metadata.brokenContainerItem()));
		}
	}

	private static Optional<ITypedIngredient<?>> resolveIngredient(
		BookmarkIngredientKey key,
		IIngredientManager ingredientManager
	) {
		// 1) Exact NBT snapshot (saved for NBT-bearing items like TConstruct tools):
		// restore the ItemStack directly without any registry lookup.
		if (key.serializedIngredient() != null) {
			Optional<ITypedIngredient<?>> fromSnapshot = resolveFromSnapshot(key.serializedIngredient(), ingredientManager);
			if (fromSnapshot.isPresent()) {
				return fromSnapshot;
			}
		}
		// 2) Direct uid lookup against JEI's ingredient registry.
		Optional<IIngredientType<?>> typeOpt = ingredientManager.getIngredientTypeForUid(key.ingredientTypeUid());
		if (typeOpt.isEmpty()) {
			return Optional.empty();
		}
		IIngredientType<?> type = typeOpt.get();
		Optional<ITypedIngredient<?>> direct = typedIngredientByUid(type, key.ingredientUid(), ingredientManager);
		if (direct.isPresent()) {
			return direct;
		}
		// 3) Fallback for bare registry-name uids: some mods (TConstruct tools with
		// material NBT, GT tools) only register NBT-annotated variants, so a bare
		// "modid:item" uid cannot be resolved. Match the first registered variant.
		if (type.getUid().equals(VanillaTypes.ITEM_STACK.getUid())) {
			ResourceLocation itemId = ResourceLocation.tryParse(key.ingredientUid());
			if (itemId != null) {
				for (ITypedIngredient<?> candidate : ingredientManager.getAllTypedIngredients(VanillaTypes.ITEM_STACK)) {
					if (candidate.getIngredient() instanceof ItemStack stack &&
						!stack.isEmpty() &&
						stack.getItem().builtInRegistryHolder().is(itemId)) {
						return Optional.of(candidate);
					}
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<ITypedIngredient<?>> resolveFromSnapshot(
		String serializedIngredient,
		IIngredientManager ingredientManager
	) {
		try {
			ItemStack stack = ItemStack.of(NbtUtils.snbtToStructure(serializedIngredient));
			if (!stack.isEmpty()) {
				@SuppressWarnings({"rawtypes", "unchecked"})
				Optional<ITypedIngredient<?>> resolved = (Optional) ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
				return resolved;
			}
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
			// fall through to uid-based resolution
		}
		return Optional.empty();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Optional<ITypedIngredient<?>> typedIngredientByUid(
		IIngredientType<?> type,
		String ingredientUid,
		IIngredientManager ingredientManager
	) {
		return (Optional) ingredientManager.getTypedIngredientByUid((IIngredientType) type, ingredientUid);
	}
}
