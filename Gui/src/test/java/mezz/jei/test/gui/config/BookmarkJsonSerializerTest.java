package mezz.jei.test.gui.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkType;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.config.BookmarkJsonSerializer;
import mezz.jei.gui.config.BookmarkConfigEntry;
import mezz.jei.gui.config.BookmarkConfigEntryCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class BookmarkJsonSerializerTest {
	private static final ResourceLocation RECIPE_UID = ResourceLocation.fromNamespaceAndPath("test", "glass");
	private static final RecipeType<Object> RECIPE_TYPE = RecipeType.create("test", "crafting", Object.class);
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager();

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void groupCodecUsesIntegerIds() {
		BookmarkGroup group = new BookmarkGroup(2, "Machines");

		JsonElement encoded = BookmarkConfigEntryCodec.GROUP_CODEC.encodeStart(JsonOps.INSTANCE, group).getOrThrow();
		BookmarkGroup decoded = BookmarkConfigEntryCodec.GROUP_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		Assertions.assertEquals(2, encoded.getAsJsonObject().get("id").getAsInt());
		Assertions.assertEquals(group, decoded);
	}

	@Test
	public void defaultIngredientBookmarkUsesOfficialV2ShapeWithoutForkData() {
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS));
		IBookmark bookmark = IngredientBookmark.create(glass, INGREDIENT_MANAGER);
		Codec<BookmarkConfigEntry> codec = createEntryCodec();

		JsonElement encoded = codec.encodeStart(
			JsonOps.INSTANCE,
			BookmarkConfigEntry.bookmark(bookmark, BookmarkItemMetadata.defaultForGroup(0))
		).getOrThrow();
		BookmarkConfigEntry decoded = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		Assertions.assertEquals("INGREDIENT", encoded.getAsJsonObject().get("bookmarkType").getAsString());
		Assertions.assertFalse(encoded.getAsJsonObject().has("forkData"));
		Assertions.assertEquals(0, decoded.metadata().groupId());
		Assertions.assertEquals(bookmark, decoded.bookmark());
	}

	@Test
	public void forkDataRoundTripsAmountAndOrderedCandidates() {
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 4));
		ITypedIngredient<ItemStack> whiteGlass = typed(new ItemStack(Items.WHITE_STAINED_GLASS));
		IBookmark bookmark = IngredientBookmark.createWithAmount(glass, 4, INGREDIENT_MANAGER);
		BookmarkIngredientKey glassKey = BookmarkItemMetadataFactory.createPermutationKey(glass, INGREDIENT_MANAGER);
		BookmarkIngredientKey whiteGlassKey = BookmarkItemMetadataFactory.createPermutationKey(whiteGlass, INGREDIENT_MANAGER);
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			2,
			BookmarkItemType.ITEM,
			2,
			3,
			5_000,
			null,
			null,
			new LinkedHashSet<>(List.of(glassKey, whiteGlassKey))
		);
		Codec<BookmarkConfigEntry> codec = createEntryCodec();

		JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, BookmarkConfigEntry.bookmark(bookmark, metadata)).getOrThrow();
		BookmarkConfigEntry decoded = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		Assertions.assertEquals(4, encoded.getAsJsonObject().getAsJsonObject("forkData").get("amount").getAsLong());
		Assertions.assertFalse(encoded.getAsJsonObject().has("count"));
		Assertions.assertEquals(metadata, decoded.metadata());
		Assertions.assertEquals(List.of(glassKey, whiteGlassKey), List.copyOf(decoded.metadata().permutations()));
		Assertions.assertTrue(decoded.metadata().permutations().stream().allMatch(key -> key.typedIngredient() != null));
		Assertions.assertEquals(4, decoded.bookmark().getElement().getTypedIngredient().getItemStack().orElseThrow().getCount());
	}

	@Test
	public void nonConsumableRecipeRestoresSelectedInputWithoutDisplayRoleField() {
		ITypedIngredient<ItemStack> selected = typed(new ItemStack(Items.WHITE_STAINED_GLASS));
		RecipeBookmark<Object, ItemStack> bookmark = new RecipeBookmark<>(
			new TestRecipeCategory(),
			new Object(),
			RECIPE_UID,
			selected,
			RecipeIngredientRole.INPUT
		);
		BookmarkItemMetadata metadata = new BookmarkItemMetadata(
			3,
			BookmarkItemType.NONCONSUMABLE,
			1,
			1,
			BookmarkItemMetadata.CHANCE_FULL,
			RECIPE_TYPE.getUid(),
			RECIPE_UID,
			Set.of(BookmarkItemMetadataFactory.createPermutationKey(selected, INGREDIENT_MANAGER))
		);
		Codec<BookmarkConfigEntry> codec = createEntryCodec();

		JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, BookmarkConfigEntry.bookmark(bookmark, metadata)).getOrThrow();
		BookmarkConfigEntry decoded = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		Assertions.assertFalse(encoded.toString().contains("displayRole"));
		Assertions.assertEquals("NONCONSUMABLE", encoded.getAsJsonObject().getAsJsonObject("forkData").get("kind").getAsString());
		RecipeBookmark<?, ?> decodedBookmark = (RecipeBookmark<?, ?>) decoded.bookmark();
		Assertions.assertEquals(RecipeIngredientRole.INPUT, decodedBookmark.getDisplayRole());
		Assertions.assertEquals(Items.WHITE_STAINED_GLASS, decodedBookmark.getRecipeOutput().getItemStack().orElseThrow().getItem());
	}

	@Test
	public void itemBookmarkRoundTripsThroughList() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		source.addGroupFromConfig(new BookmarkGroup(1, "Machines"));
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 4));
		IBookmark itemBookmark = IngredientBookmark.create(glass, INGREDIENT_MANAGER);
		source.addToListWithoutNotifying(itemBookmark, false);

		Codec<BookmarkConfigEntry> codec = createEntryCodec();
		List<BookmarkConfigEntry> entries = BookmarkJsonSerializer.createEntries(source).stream()
			.map(entry -> codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, entry).getOrThrow()).getOrThrow())
			.toList();
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		BookmarkJsonSerializer.applyEntries(entries, decoded);

		Assertions.assertTrue(decoded.getBookmarkGroups().stream().anyMatch(group -> group.id() == 1));
		Assertions.assertEquals(1, decoded.getBookmarks().size());
		Assertions.assertTrue(decoded.getBookmarks().getFirst() instanceof IngredientBookmark);
	}

	@Test
	public void recipeBookmarkRoundTripsThroughList() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		source.addGroupFromConfig(new BookmarkGroup(1, "Machines"));
		Object recipe = new Object();
		ITypedIngredient<ItemStack> output = typed(new ItemStack(Items.GLASS));
		RecipeBookmark<Object, ItemStack> recipeBookmark = new RecipeBookmark<>(
			new TestRecipeCategory(),
			recipe,
			RECIPE_UID,
			output,
			RecipeIngredientRole.OUTPUT
		);
		source.addToListWithoutNotifying(recipeBookmark, false);

		Codec<BookmarkConfigEntry> codec = createEntryCodec();
		List<BookmarkConfigEntry> entries = BookmarkJsonSerializer.createEntries(source).stream()
			.map(entry -> codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, entry).getOrThrow()).getOrThrow())
			.toList();
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		BookmarkJsonSerializer.applyEntries(entries, decoded);

		Assertions.assertEquals(1, decoded.getBookmarks().size());
		Assertions.assertTrue(decoded.getBookmarks().getFirst() instanceof RecipeBookmark);
		Assertions.assertEquals(RECIPE_UID, ((RecipeBookmark<?, ?>) decoded.getBookmarks().getFirst()).getRecipeUid());
	}

	@Test
	public void groupSnapshotImportsWithFreshGroupId() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 4));
		int sharedGroupId = source.addRecipeBookmarkGroup(
			"Shared Machines",
			List.of(IngredientBookmark.createWithAmount(glass, 4, INGREDIENT_MANAGER))
		);
		source.setGroupCraftingMode(sharedGroupId, true);

		Codec<BookmarkConfigEntry> codec = createEntryCodec();
		String snapshot = BookmarkJsonSerializer.serializeGroupSnapshot(source, sharedGroupId, codec, JsonOps.INSTANCE).orElseThrow();
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		decoded.addGroupFromConfig(new BookmarkGroup(1, "Existing"));
		decoded.addToListWithoutNotifying(IngredientBookmark.create(typed(new ItemStack(Items.GLASS)), INGREDIENT_MANAGER), false);

		int importedGroupId = BookmarkJsonSerializer.deserializeGroupSnapshot(
			snapshot,
			decoded,
			codec,
			JsonOps.INSTANCE
		).orElseThrow();

		Assertions.assertEquals(2, importedGroupId);
		BookmarkGroup importedGroup = decoded.getBookmarkGroups().getLast();
		Assertions.assertEquals("Shared Machines", importedGroup.title());
		Assertions.assertTrue(importedGroup.craftingMode());
		Assertions.assertEquals(2, decoded.getBookmarks().size());
	}

	private static Codec<BookmarkConfigEntry> createEntryCodec() {
		MapCodec<ITypedIngredient<?>> typedIngredientCodec = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("type", VanillaTypes.ITEM_STACK.getUid())
				.forGetter(ingredient -> ingredient.getType().getUid()),
			ResourceLocation.CODEC.fieldOf("ingredient")
				.forGetter(ingredient -> BuiltInRegistries.ITEM.getKey(ingredient.getItemStack().orElseThrow().getItem())),
			Codec.INT.optionalFieldOf("count", 1)
				.forGetter(ingredient -> ingredient.getItemStack().orElseThrow().getCount())
		).apply(instance, (type, id, count) -> typed(new ItemStack(BuiltInRegistries.ITEM.get(id), count))));
		ICodecHelper codecHelper = (ICodecHelper) Proxy.newProxyInstance(
			BookmarkJsonSerializerTest.class.getClassLoader(),
			new Class<?>[]{ICodecHelper.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getTypedIngredientCodec" -> typedIngredientCodec;
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
		MapCodec<IBookmark> bookmarkCodec = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("bookmarkType").forGetter(bookmark -> bookmark.getType().name()),
			typedIngredientCodec.forGetter(bookmark -> bookmark.getElement().getTypedIngredient())
		).apply(instance, (type, ingredient) -> BookmarkType.valueOf(type) == BookmarkType.RECIPE ?
			new RecipeBookmark<>(new TestRecipeCategory(), new Object(), RECIPE_UID, ingredient, RecipeIngredientRole.OUTPUT) :
			IngredientBookmark.create(ingredient, INGREDIENT_MANAGER)));
		return BookmarkConfigEntryCodec.create(codecHelper, INGREDIENT_MANAGER, bookmarkCodec.codec());
	}

	private static IIngredientManager ingredientManager() {
		return (IIngredientManager) Proxy.newProxyInstance(
			BookmarkJsonSerializerTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getIngredientHelper" -> itemHelper();
				case "createTypedIngredient" -> Optional.of(typed((ItemStack) args[1]));
				case "normalizeTypedIngredient" -> {
					ItemStack normalized = ((ITypedIngredient<?>) args[0]).getItemStack().orElseThrow().copy();
					normalized.setCount(1);
					yield typed(normalized);
				}
				case "getIngredientTypeForUid" -> "item_stack".equals(args[0]) ?
					Optional.of(VanillaTypes.ITEM_STACK) :
					Optional.empty();
				case "getTypedIngredientByUid" -> Optional.of(typed(
					new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) args[1])))
				));
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IIngredientHelper<ItemStack> itemHelper() {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<ItemStack> getIngredientType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public String getDisplayName(ItemStack ingredient) {
				return ingredient.getHoverName().getString();
			}

			@Override
			public String getUniqueId(ItemStack ingredient, UidContext context) {
				ResourceLocation key = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
				return key == null ? "minecraft:air" : key.toString();
			}

			@Override
			public ResourceLocation getResourceLocation(ItemStack ingredient) {
				return BuiltInRegistries.ITEM.getKey(ingredient.getItem());
			}

			@Override
			public long getAmount(ItemStack ingredient) {
				return ingredient.getCount();
			}

			@Override
			public ItemStack copyIngredient(ItemStack ingredient) {
				return ingredient.copy();
			}

			@Override
			public ItemStack copyWithAmount(ItemStack ingredient, long amount) {
				ItemStack copy = ingredient.copy();
				copy.setCount((int) amount);
				return copy;
			}

			@Override
			public String getErrorInfo(ItemStack ingredient) {
				return ingredient.toString();
			}
		};
	}

	private static ITypedIngredient<ItemStack> typed(ItemStack stack) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<ItemStack> getType() {
				return VanillaTypes.ITEM_STACK;
			}

			@Override
			public ItemStack getIngredient() {
				return stack;
			}
		};
	}

	private record TestRecipeCategory() implements IRecipeCategory<Object> {
		@Override
		public RecipeType<Object> getRecipeType() {
			return RECIPE_TYPE;
		}

		@Override
		public Component getTitle() {
			return Component.literal("crafting");
		}

		@Override
		public @Nullable mezz.jei.api.gui.drawable.IDrawable getIcon() {
			return null;
		}

		@Override
		public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, Object recipe, IFocusGroup focuses) {
		}

		@Override
		public @Nullable ResourceLocation getRegistryName(Object recipe) {
			return RECIPE_UID;
		}
	}
}
