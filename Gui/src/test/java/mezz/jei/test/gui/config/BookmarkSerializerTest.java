package mezz.jei.test.gui.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import mezz.jei.common.config.file.JsonArrayFileHelper;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.common.ingredients.TypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkManager;
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
import mezz.jei.test.gui.fixtures.ItemStackIngredientTestFixtures;
import mezz.jei.test.gui.lib.ConfigTestFixtures;
import mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyAction;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.function.Function;
import java.util.LinkedHashSet;
import java.util.HashSet;
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
	public void uiAndManagerShareIdentityAfterReload(@TempDir Path directory) {
		BookmarkFactory factory = new BookmarkFactory(createCodecHelper(itemStackCodec()), RegistryAccess.EMPTY, INGREDIENT_MANAGER);
		var clientConfig = ConfigTestFixtures.createClientConfigs(directory.resolve("client.ini")).getClientConfig();
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, clientConfig, null);
		BookmarkManager manager = new BookmarkManager(bookmarks, factory, null, null, INGREDIENT_MANAGER, null);
		ITypedIngredient<ItemStack> glass = typed(new ItemStack(Items.GLASS, 32));

		Assertions.assertTrue(bookmarks.onElementBookmarked(new IngredientElement<>(glass), BookmarkHotkeyAction.ADD_BOOKMARK));
		Assertions.assertEquals(1, bookmarks.getBookmarks().getFirst().getElement().getTypedIngredient().getItemStack().orElseThrow().getCount());
		Assertions.assertTrue(manager.contains(glass));
		Assertions.assertFalse(manager.add(glass));

		BookmarkList reloaded = reloadBookmarks(bookmarks, INGREDIENT_MANAGER);
		BookmarkManager reloadedManager = new BookmarkManager(reloaded, factory, null, null, INGREDIENT_MANAGER, null);
		Assertions.assertTrue(reloadedManager.contains(glass));
		Assertions.assertTrue(reloadedManager.remove(glass));
		Assertions.assertTrue(reloaded.getBookmarks().isEmpty());
		Assertions.assertTrue(manager.remove(glass));
		Assertions.assertTrue(bookmarks.getBookmarks().isEmpty());
		Assertions.assertTrue(manager.add(glass));
		Assertions.assertFalse(bookmarks.addIngredientBookmark(glass, false));
		Assertions.assertTrue(bookmarks.onElementBookmarked(new IngredientElement<>(glass), BookmarkHotkeyAction.ADD_BOOKMARK_WITH_COUNT));
		Assertions.assertTrue(manager.remove(glass));
		var counted = IngredientBookmark.createWithAmount(glass, 32, INGREDIENT_MANAGER);
		Assertions.assertEquals(List.of(counted), bookmarks.getBookmarks());
		BookmarkList countedReloaded = reloadBookmarks(bookmarks, INGREDIENT_MANAGER);
		Assertions.assertTrue(countedReloaded.contains(counted));
		Assertions.assertTrue(countedReloaded.remove(counted));
		Assertions.assertTrue(countedReloaded.getBookmarks().isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"1, 0", "4, 0", "1, 2", "4, 2"})
	public void roundTripsComponentAmountAndScopeIdentity(int amount, int groupId) {
		ItemStack stack = new ItemStack(Items.GLASS, amount);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Named glass"));
		IngredientBookmark<ItemStack> bookmark = IngredientBookmark.createPreservingAmount(typed(stack), INGREDIENT_MANAGER);
		if (groupId != 0) {
			bookmark = bookmark.withEqualityScope(groupId);
		}
		Codec<BookmarkConfigEntry> codec = createEntryCodec(INGREDIENT_MANAGER, itemStackCodec());
		var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(JsonOps.INSTANCE);
		var entry = BookmarkConfigEntry.bookmark(bookmark, BookmarkItemMetadata.defaultForGroup(groupId));
		JsonElement encoded = codec.encodeStart(ops, entry).getOrThrow();
		IBookmark decoded = codec.parse(ops, encoded).getOrThrow().bookmark();

		Assertions.assertEquals(bookmark, decoded);
		Assertions.assertEquals(bookmark.hashCode(), decoded.hashCode());
		Set<IBookmark> bookmarks = new HashSet<>(List.of(bookmark));
		Assertions.assertTrue(bookmarks.contains(decoded));
		Assertions.assertFalse(bookmarks.add(decoded));
		Assertions.assertTrue(bookmarks.remove(decoded));
		Assertions.assertTrue(ItemStack.matches(stack, decoded.getElement().getTypedIngredient().getItemStack().orElseThrow()));
		if (groupId != 0) {
			Assertions.assertNotEquals(IngredientBookmark.createPreservingAmount(typed(stack), INGREDIENT_MANAGER), decoded);
		}
	}

	@Test
	public void ingredientIdentitySurvivesStackMutation() {
		ItemStack stack = new ItemStack(Items.GLASS, 4);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Before"));
		var bookmark = IngredientBookmark.createPreservingAmount(typed(stack), INGREDIENT_MANAGER);
		var equivalent = IngredientBookmark.createWithAmount(typed(stack.copy()), 4, INGREDIENT_MANAGER);
		Set<IBookmark> bookmarks = new HashSet<>(List.of(bookmark));
		int hashCode = bookmark.hashCode();

		stack.setCount(2);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("After"));
		bookmark.getIngredient().getIngredient().set(DataComponents.DAMAGE, 3);

		Assertions.assertEquals(hashCode, bookmark.hashCode());
		Assertions.assertEquals(equivalent, bookmark);
		Assertions.assertTrue(bookmarks.contains(equivalent));
		Assertions.assertFalse(bookmarks.add(equivalent));
		Assertions.assertEquals(equivalent.withEqualityScope(2), bookmark.withEqualityScope(2));
		Assertions.assertNotEquals(IngredientBookmark.createPreservingAmount(typed(stack), INGREDIENT_MANAGER), bookmark);
		Assertions.assertTrue(bookmarks.remove(equivalent));
	}

	@Test
	public void preservesDistinctComponentsAmountsAndScopes() {
		var plain = typed(new ItemStack(Items.GLASS));
		ItemStack namedStack = new ItemStack(Items.GLASS);
		namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("Glass"));
		var named = typed(namedStack);
		var factory = new BookmarkFactory(createCodecHelper(itemStackCodec()), RegistryAccess.EMPTY, INGREDIENT_MANAGER);
		var ordinary = IngredientBookmark.create(plain, INGREDIENT_MANAGER);
		var counted = IngredientBookmark.createWithAmount(plain, 4, INGREDIENT_MANAGER);
		Set<IBookmark> bookmarks = new HashSet<>(List.of(ordinary, counted, IngredientBookmark.create(named, INGREDIENT_MANAGER), ordinary.withEqualityScope(2), ordinary.withEqualityScope(3)));

		Assertions.assertEquals(5, bookmarks.size());
		Assertions.assertFalse(bookmarks.add(factory.create(plain)));
		Assertions.assertFalse(bookmarks.add(factory.create(named)));
		Assertions.assertFalse(bookmarks.add(IngredientBookmark.createPreservingAmount(typed(new ItemStack(Items.GLASS, 4)), INGREDIENT_MANAGER)));
		Assertions.assertEquals(5, bookmarks.size());
	}

	@Test
	public void separatesIngredientTypesWithTheSameHelperUid() {
		IIngredientType<String> firstType = () -> String.class;
		IIngredientType<String> secondType = () -> String.class;
		@SuppressWarnings("unchecked")
		IIngredientHelper<String> helper = (IIngredientHelper<String>) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{IIngredientHelper.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getUniqueId" -> args[0];
				default -> throw new UnsupportedOperationException(method.getName());
			});
		IIngredientManager manager = ItemStackIngredientTestFixtures.ingredientManager(firstType, helper);
		var ingredient = TypedIngredient.createUnvalidated(firstType, "same_uid");
		var first = IngredientBookmark.create(ingredient, manager);
		var second = IngredientBookmark.create(TypedIngredient.createUnvalidated(secondType, "same_uid"),
			ItemStackIngredientTestFixtures.ingredientManager(secondType, helper));
		MapCodec<ITypedIngredient<?>> codec = Codec.STRING.<ITypedIngredient<?>>xmap(
				value -> TypedIngredient.createUnvalidated(firstType, value), value -> (String) value.getIngredient()
			)
			.fieldOf("ingredient");
		var factory = new BookmarkFactory(createCodecHelper(codec), RegistryAccess.EMPTY, manager);
		Set<IBookmark> bookmarks = new HashSet<>(List.of(first, second));

		Assertions.assertEquals(2, bookmarks.size());
		Assertions.assertNotEquals(first, second);
		Assertions.assertEquals(first.hashCode(), factory.create(ingredient).hashCode());
		Assertions.assertFalse(bookmarks.add(factory.create(ingredient)));
		Assertions.assertTrue(bookmarks.remove(factory.create(ingredient)));
		Assertions.assertTrue(bookmarks.contains(second));
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

	@Test
	public void roundTripsIndependentChaptersAndMovesGroups() {
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		IBookmark glass = IngredientBookmark.create(typed(new ItemStack(Items.GLASS)), INGREDIENT_MANAGER);
		source.addToListWithoutNotifying(glass, false);
		int originalGroup = source.createGroupForBookmarks("Glass", List.of(glass));
		source.setGroupCraftingMode(originalGroup, true);
		source.rememberChapterPage(3);
		source.renameChapter(1, "First");
		int second = source.createChapter();
		Assertions.assertEquals(2, second);
		Assertions.assertEquals("2", source.getChapters().getLast().title());
		source.selectChapter(second);
		Assertions.assertTrue(source.getBookmarks().isEmpty());
		source.renameChapter(second, "Independent name");
		IBookmark stone = IngredientBookmark.create(typed(new ItemStack(Items.STONE)), INGREDIENT_MANAGER);
		source.addToListWithoutNotifying(stone, false);
		int targetGroup = source.createGroupForBookmarks("Existing", List.of(stone));
		source.selectChapter(1);
		Assertions.assertEquals(3, source.getChapterPage());
		var originalArea = new mezz.jei.common.util.ImmutableRect2i(10, 90, 16, 16);
		var drag = mezz.jei.gui.overlay.bookmarks.BookmarkSortDragState.group(originalGroup, originalArea, 13, 96);
		var originalSlots = List.of(new mezz.jei.gui.overlay.bookmarks.BookmarkPanelLayout.PanelSlot<>(source.getBookmarks().getFirst(), originalGroup, originalArea, false));
		drag.update(source, originalSlots, originalSlots, 13, 96, 0);
		var preview = drag.getFloatingGroupPanelSlots(100, 40);
		Assertions.assertFalse(preview.isEmpty());
		drag.updateProjectedSlotOverlayAreas(originalSlots);
		Assertions.assertEquals(List.of(originalArea), drag.getTargetSlotOverlayAreas());
		int moved = source.moveGroupToChapter(originalGroup, second);
		drag.continueGroupDrag(moved);
		Assertions.assertTrue(drag.getTargetSlotOverlayAreas().isEmpty());
		var targetArea = new mezz.jei.common.util.ImmutableRect2i(70, 180, 16, 16);
		var targetSlots = List.of(new mezz.jei.gui.overlay.bookmarks.BookmarkPanelLayout.PanelSlot<>(source.getBookmarks().getLast(), moved, targetArea, false));
		drag.update(source, targetSlots, targetSlots, 100, 40, 0);
		drag.updateProjectedSlotOverlayAreas(targetSlots);
		Assertions.assertEquals(List.of(targetArea), drag.getSourceSlotOverlayAreas(targetSlots));
		Assertions.assertEquals(List.of(targetArea), drag.getTargetSlotOverlayAreas());
		Assertions.assertFalse(targetSlots.getFirst().item().isVisible());
		Assertions.assertEquals(preview, drag.getFloatingGroupPanelSlots(100, 40), "chapter changes must preserve the original grab offset and preview coordinates");
		drag.continueGroupDrag(moved);
		Assertions.assertEquals(preview, drag.getFloatingGroupPanelSlots(100, 40));
		drag.stop();
		Assertions.assertTrue(drag.getTargetSlotOverlayAreas().isEmpty());
		Assertions.assertTrue(targetSlots.getFirst().item().isVisible());
		Assertions.assertNotEquals(targetGroup, moved);
		Assertions.assertTrue(source.isGroupCraftingMode(moved));
		Assertions.assertEquals(2, source.getBookmarks().size());
		Codec<BookmarkConfigEntry> codec = createEntryCodec();
		List<BookmarkConfigEntry> entries = source.createChapterEntries().stream()
			.map(entry -> codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, entry).getOrThrow()).getOrThrow()).toList();
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		BookmarkJsonSerializer.applyEntries(entries, decoded);
		Assertions.assertEquals(second, decoded.getActiveChapterId());
		Assertions.assertEquals("Independent name", decoded.getChapters().getLast().title());
		Assertions.assertEquals(2, decoded.getBookmarks().size());
		Assertions.assertTrue(decoded.isGroupCraftingMode(moved));
		decoded.selectChapter(1);
		Assertions.assertTrue(decoded.getBookmarks().isEmpty());
		Assertions.assertEquals(3, decoded.getChapterPage());
		int empty = decoded.createChapter();
		decoded.selectChapter(empty);
		decoded.selectChapter(second);
		Assertions.assertEquals(3, decoded.getChapters().size(), "switching must retain empty unnamed chapters");
		List<BookmarkConfigEntry> saved = decoded.createChapterEntries().stream()
			.map(entry -> codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, entry).getOrThrow()).getOrThrow()).toList();
		BookmarkList restored = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		BookmarkJsonSerializer.applyEntries(saved, restored);
		Assertions.assertEquals(decoded.getChapters(), restored.getChapters());
		Assertions.assertTrue(restored.deleteChapter(empty));
		Assertions.assertEquals(empty, restored.createChapter(), "only explicit deletion releases chapter ids");
		Assertions.assertTrue(restored.deleteChapter(second));
		Assertions.assertEquals(1, restored.getActiveChapterId());
		Assertions.assertTrue(restored.getBookmarks().isEmpty());
		Assertions.assertTrue(restored.deleteChapter(empty));
		Assertions.assertFalse(restored.deleteChapter(1), "at least one chapter remains");
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
			)
			.getOrThrow();
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
		Assertions.assertEquals(Items.WHITE_STAINED_GLASS, decodedBookmark.getDisplayIngredient().getItemStack().orElseThrow().getItem());
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
			JeiChatItemLinks.createBookmarkGroupLinkMarker(snapshot).trim())
			.getString());
		BookmarkList decoded = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		decoded.addGroupFromConfig(new BookmarkGroup(1, "Existing"));
		decoded.addToListWithoutNotifying(IngredientBookmark.create(typed(new ItemStack(Items.GLASS)), INGREDIENT_MANAGER), false);

		int importedGroupId = BookmarkJsonSerializer.deserializeGroupSnapshot(snapshot, decoded, codec, JsonOps.INSTANCE)
			.orElseThrow();

		Assertions.assertEquals(2, importedGroupId);
		BookmarkGroup importedGroup = decoded.getBookmarkGroups().getLast();
		Assertions.assertEquals("Shared Machines", importedGroup.title());
		Assertions.assertTrue(importedGroup.craftingMode());
		Assertions.assertEquals(2, decoded.getBookmarks().size());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void migratesWithoutLosingSources(boolean fail, @TempDir Path directory) throws IOException {
		Path json = directory.resolve("bookmarks.json");
		Path ini = directory.resolve("bookmarks.ini");
		Files.writeString(json, "original json");
		Files.writeString(ini, "original ini");
		BookmarkList source = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		source.addGroupFromConfig(new BookmarkGroup(2, "Machines"));
		IBookmark grouped = IngredientBookmark.create(typed(new ItemStack(Items.GLASS)), INGREDIENT_MANAGER);
		source.addToListWithoutNotifying(grouped, false);
		source.moveBookmarkMetadataFromConfig(grouped, BookmarkItemMetadata.defaultForGroup(2).withMultiplier(64));
		source.addToListWithoutNotifying(IngredientBookmark.create(typed(new ItemStack(Items.IRON_INGOT)), INGREDIENT_MANAGER), false);
		List<BookmarkConfigEntry> entries = BookmarkJsonSerializer.createEntries(source);
		Codec<BookmarkConfigEntry> codec = createEntryCodec();
		if (fail) {
			Codec<BookmarkConfigEntry> broken = codec.flatComapMap(Function.identity(),
				entry -> entry.bookmark() == null ? DataResult.success(entry) : DataResult.error(() -> "Cannot encode ingredient"));
			Assertions.assertThrows(IllegalStateException.class,
				() -> BookmarkJsonSerializer.migrate(json, 2, Optional.of(ini), entries, broken, JsonOps.INSTANCE));
			Assertions.assertEquals("original json", Files.readString(json));
			Assertions.assertEquals("original ini", Files.readString(ini));
			Assertions.assertFalse(Files.exists(directory.resolve("bookmarks.ini.bak")));
		} else {
			BookmarkJsonSerializer.migrate(json, 2, Optional.of(ini), entries, codec, JsonOps.INSTANCE);
			Assertions.assertFalse(Files.exists(ini));
			Assertions.assertEquals("original ini", Files.readString(directory.resolve("bookmarks.ini.bak")));
			try (var reader = Files.newBufferedReader(json)) {
				List<BookmarkConfigEntry> loaded = JsonArrayFileHelper.read(reader, 2, codec, JsonOps.INSTANCE,
					(element, error) -> Assertions.fail(error.message()),
					(element, exception) -> Assertions.fail(exception));
				Assertions.assertEquals(entries.stream().map(BookmarkConfigEntry::group).toList(), loaded.stream().map(BookmarkConfigEntry::group).toList());
				Assertions.assertEquals(entries.stream().map(BookmarkConfigEntry::metadata).toList(), loaded.stream().map(BookmarkConfigEntry::metadata).toList());
				Assertions.assertEquals(3, loaded.size());
			}
		}
		Assertions.assertEquals("original json", Files.readString(directory.resolve("bookmarks.json.bak")));
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
			)
			.apply(instance, (type, id, count) -> typed(new ItemStack(BuiltInRegistries.ITEM.get(id), count))));
		return createEntryCodec(manager, typedIngredientCodec);
	}

	private static MapCodec<ITypedIngredient<?>> itemStackCodec() {
		return ItemStack.CODEC.<ITypedIngredient<?>>xmap(
				stack -> typed(stack), ingredient -> ingredient.getItemStack().orElseThrow()
			)
			.fieldOf("ingredient");
	}

	private static ICodecHelper createCodecHelper(MapCodec<ITypedIngredient<?>> typedIngredientCodec) {
		return (ICodecHelper) Proxy.newProxyInstance(
			BookmarkSerializerTest.class.getClassLoader(),
			new Class<?>[]{ICodecHelper.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getTypedIngredientCodec" -> typedIngredientCodec;
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static Codec<BookmarkConfigEntry> createEntryCodec(IIngredientManager manager, MapCodec<ITypedIngredient<?>> typedIngredientCodec) {
		ICodecHelper codecHelper = createCodecHelper(typedIngredientCodec);
		BookmarkFactory factory = new BookmarkFactory(codecHelper, RegistryAccess.EMPTY, manager);
		MapCodec<IBookmark> bookmarkCodec = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.fieldOf("bookmarkType").forGetter(bookmark -> bookmark.getType().name()),
				typedIngredientCodec.forGetter(bookmark -> bookmark.getElement().getTypedIngredient())
			)
			.apply(instance, (type, ingredient) -> BookmarkType.valueOf(type) == BookmarkType.RECIPE ? new RecipeBookmark<>(new TestRecipeCategory(RECIPE_TYPE, RECIPE_UID), new Object(), RECIPE_UID, ingredient, RecipeIngredientRole.OUTPUT) : factory.create(ingredient)));
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
				case "getIngredientTypeForUid" -> "item_stack".equals(args[0]) ? Optional.of(VanillaTypes.ITEM_STACK) : Optional.empty();
				case "getTypedIngredientByUid" -> Optional.of(typed(
					new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse((String) args[1])))
				));
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

}
