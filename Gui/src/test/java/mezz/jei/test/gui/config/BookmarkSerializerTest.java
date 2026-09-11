package mezz.jei.test.gui.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.common.chat.JeiChatItemLinks;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkType;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.bookmarks.chain.RecipeChainInput;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipSectionType;
import mezz.jei.gui.config.BookmarkJsonSerializer;
import mezz.jei.gui.config.BookmarkConfigEntry;
import mezz.jei.gui.config.BookmarkConfigEntryCodec;
import mezz.jei.test.gui.fixtures.RecipeLayoutTestFixtures.TestRecipeCategory;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.itemHelper;
import static mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures.typed;

public class BookmarkSerializerTest {
	private static final ResourceLocation RECIPE_UID = ResourceLocation.fromNamespaceAndPath("test", "glass");
	private static final RecipeType<Object> RECIPE_TYPE = RecipeType.create("test", "crafting", Object.class);
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager(1);

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void roundTripsGroup() {
		BookmarkGroup group = new BookmarkGroup(2, "Machines");

		JsonElement encoded = BookmarkConfigEntryCodec.GROUP_CODEC.encodeStart(JsonOps.INSTANCE, group).getOrThrow();
		BookmarkGroup decoded = BookmarkConfigEntryCodec.GROUP_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		Assertions.assertTrue(encoded.getAsJsonObject().getAsJsonPrimitive("id").isNumber());
		Assertions.assertEquals(2, encoded.getAsJsonObject().get("id").getAsInt());
		Assertions.assertEquals(group, decoded);
	}

	@ParameterizedTest
	@CsvSource({"1, 1", "128, 1", "1, 1000", "2500, 1000"})
	public void preservesMissingAmounts(long amount, int normalizedAmount) {
		// Some ingredient types normalize to a larger unit, such as a bucket of fluid.
		IIngredientManager manager = ingredientManager(normalizedAmount);
		BookmarkList source = new BookmarkList(null, null, manager, null, null, null, null);
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 64));
		BookmarkIngredientKey key = BookmarkItemMetadataFactory.createPermutationKey(glass, manager);
		source.addMissingRecipeChainGroup(0, List.of(new RecipeChainTooltipModel.Item(key, amount, glass)));
		Assertions.assertEquals(1, source.getBookmarks().getFirst().getElement().getTypedIngredient().getItemStack().orElseThrow().getCount());
		BookmarkList decoded = reloadBookmarks(source, manager);
		int groupId = decoded.getBookmarkGroups().getLast().id();
		Assertions.assertTrue(decoded.isGroupCraftingMode(groupId));
		List<RecipeChainInput> inputs = decoded.getRecipeChainTooltipInputs(groupId);
		RecipeChainTooltipModel normal = RecipeChainTooltipModel.create(inputs, decoded.getRecipeChainDetails(groupId),
			Set.of(), List.of(), false, false, manager);
		Assertions.assertEquals(RecipeChainTooltipSectionType.INPUT, normal.sections().getFirst().type());
		Assertions.assertEquals(amount, normal.sections().getFirst().items().getFirst().amount());
		long stored = Math.min(20, amount - 1);
		RecipeChainInput inventory = new RecipeChainInput(-1,
			BookmarkItemMetadata.defaultForGroup(groupId).withMultiplier(stored).withPermutations(Set.of(key)), key, glass);
		RecipeChainTooltipModel shift = RecipeChainTooltipModel.create(inputs, decoded.getRecipeChainDetails(groupId),
			Set.of(), List.of(inventory), true, false, manager);
		List<RecipeChainTooltipModel.Item> missing = shift.sections().stream()
			.filter(section -> section.type() == RecipeChainTooltipSectionType.MISSING)
			.flatMap(section -> section.items().stream()).toList();
		Assertions.assertEquals(amount - stored, missing.getFirst().amount());
		decoded.addMissingRecipeChainGroup(groupId, missing);
		int savedGroupId = decoded.getBookmarkGroups().getLast().id();
		Assertions.assertEquals(amount - stored, decoded.getRecipeChainTooltipInputs(savedGroupId).getFirst().metadata().amount());
		Assertions.assertEquals(amount, decoded.getRecipeChainTooltipInputs(groupId).getFirst().metadata().amount());
	}

	@Test
	public void omitsDefaultForkData() {
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
	public void roundTripsForkData() {
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
	public void restoresNonConsumableInput() {
		ITypedIngredient<ItemStack> selected = typed(new ItemStack(Items.WHITE_STAINED_GLASS));
		RecipeBookmark<Object, ItemStack> bookmark = new RecipeBookmark<>(
			new TestRecipeCategory(RECIPE_TYPE, RECIPE_UID),
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
	public void restoresItemBookmark() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		source.addGroupFromConfig(new BookmarkGroup(1, "Machines"));
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 4));
		IBookmark bookmark = IngredientBookmark.create(glass, INGREDIENT_MANAGER);
		source.addToListWithoutNotifying(bookmark, false);

		BookmarkList decoded = reloadBookmarks(source, INGREDIENT_MANAGER);

		Assertions.assertTrue(decoded.getBookmarkGroups().stream().anyMatch(group -> group.id() == 1));
		Assertions.assertEquals(1, decoded.getBookmarks().size());
		Assertions.assertTrue(decoded.getBookmarks().getFirst() instanceof IngredientBookmark);
	}

	@Test
	public void restoresRecipeBookmark() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		source.addGroupFromConfig(new BookmarkGroup(1, "Machines"));
		Object recipe = new Object();
		ITypedIngredient<ItemStack> output = typed(new ItemStack(Items.GLASS));
		RecipeBookmark<Object, ItemStack> bookmark = new RecipeBookmark<>(
			new TestRecipeCategory(RECIPE_TYPE, RECIPE_UID),
			recipe,
			RECIPE_UID,
			output,
			RecipeIngredientRole.OUTPUT
		);
		source.addToListWithoutNotifying(bookmark, false);

		BookmarkList decoded = reloadBookmarks(source, INGREDIENT_MANAGER);

		Assertions.assertEquals(1, decoded.getBookmarks().size());
		Assertions.assertTrue(decoded.getBookmarks().getFirst() instanceof RecipeBookmark);
		Assertions.assertEquals(RECIPE_UID, ((RecipeBookmark<?, ?>) decoded.getBookmarks().getFirst()).getRecipeUid());
	}

	@Test
	public void importsGroupSnapshot() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 4));
		int sharedGroupId = source.addRecipeBookmarkGroup(
			"Shared Machines",
			List.of(IngredientBookmark.createWithAmount(glass, 4, INGREDIENT_MANAGER))
		);
		source.setGroupCraftingMode(sharedGroupId, true);

		Codec<BookmarkConfigEntry> codec = createEntryCodec();
		String snapshot = BookmarkJsonSerializer.serializeGroupSnapshot(source, sharedGroupId, codec, JsonOps.INSTANCE).orElseThrow();
		Assertions.assertTrue(JeiChatItemLinks.isValidBookmarkGroupSnapshot(snapshot));
		Assertions.assertEquals("[Shared Machines]", JeiChatItemLinks.parse(
			JeiChatItemLinks.createBookmarkGroupLinkMarker(snapshot).trim()).getString());
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

	private static BookmarkList reloadBookmarks(BookmarkList source, IIngredientManager manager) {
		Codec<BookmarkConfigEntry> codec = createEntryCodec(manager);
		List<BookmarkConfigEntry> entries = BookmarkJsonSerializer.createEntries(source).stream()
			.map(entry -> codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, entry).getOrThrow()).getOrThrow())
			.toList();
		BookmarkList decoded = new BookmarkList(null, null, manager, null, null, null, null);
		BookmarkJsonSerializer.applyEntries(entries, decoded);
		return decoded;
	}

	private static Codec<BookmarkConfigEntry> createEntryCodec() {
		return createEntryCodec(INGREDIENT_MANAGER);
	}

	private static Codec<BookmarkConfigEntry> createEntryCodec(IIngredientManager manager) {
		MapCodec<ITypedIngredient<?>> typedIngredientCodec = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("type", VanillaTypes.ITEM_STACK.getUid())
				.forGetter(ingredient -> ingredient.getType().getUid()),
			ResourceLocation.CODEC.fieldOf("ingredient")
				.forGetter(ingredient -> BuiltInRegistries.ITEM.getKey(ingredient.getItemStack().orElseThrow().getItem())),
			Codec.INT.optionalFieldOf("count", 1)
				.forGetter(ingredient -> ingredient.getItemStack().orElseThrow().getCount())
		).apply(instance, (type, id, count) -> typed(new ItemStack(BuiltInRegistries.ITEM.get(id), count))));
		ICodecHelper codecHelper = (ICodecHelper) Proxy.newProxyInstance(
			BookmarkSerializerTest.class.getClassLoader(),
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
			new RecipeBookmark<>(new TestRecipeCategory(RECIPE_TYPE, RECIPE_UID), new Object(), RECIPE_UID, ingredient, RecipeIngredientRole.OUTPUT) :
			IngredientBookmark.create(ingredient, manager)));
		return BookmarkConfigEntryCodec.create(codecHelper, manager, bookmarkCodec.codec());
	}

	private static IIngredientManager ingredientManager(int normalizedAmount) {
		return (IIngredientManager) Proxy.newProxyInstance(
			BookmarkSerializerTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getIngredientHelper" -> itemHelper();
				case "createTypedIngredient" -> Optional.of(typed((ItemStack) args[1]));
				case "normalizeTypedIngredient" -> {
					ItemStack normalized = ((ITypedIngredient<?>) args[0]).getItemStack().orElseThrow().copy();
					normalized.setCount(normalizedAmount);
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

}
