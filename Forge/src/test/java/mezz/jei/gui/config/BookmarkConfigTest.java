package mezz.jei.gui.config;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.StackHelper;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.BookmarkGroup;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkViewMode;
import mezz.jei.gui.bookmarks.chain.RecipeChainTooltipModel;
import com.google.gson.JsonParser;
import mezz.jei.library.ingredients.subtypes.SubtypeManager;
import mezz.jei.library.load.registration.IngredientManagerBuilder;
import mezz.jei.library.load.registration.SubtypeRegistration;
import mezz.jei.library.plugins.vanilla.VanillaPlugin;
import mezz.jei.library.plugins.vanilla.ingredients.ItemStackHelper;
import mezz.jei.test.lib.ForgeTestBootstrap;
import mezz.jei.test.lib.TestColorHelper;

import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class BookmarkConfigTest {
	@BeforeAll
	public static void setup() {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		ForgeTestBootstrap.bootStrap();
	}

	@Test
	public void itemStackBookmarkSerializationPreservesCustomNbtNumberTypes() {
		CompoundTag customData = new CompoundTag();
		customData.putByte("byte", (byte) 1);
		customData.putShort("short", (short) 2);
		customData.putInt("int", 3);
		customData.putLong("long", 4L);
		customData.putFloat("float", 5.5F);
		customData.putDouble("double", 6.5D);

		ItemStack stack = new ItemStack(Items.CHEST);
		stack.getOrCreateTag()
			.put("custom", customData);

		IColorHelper colorHelper = new TestColorHelper();
		SubtypeRegistration subtypeRegistration = new SubtypeRegistration();
		new VanillaPlugin().registerItemSubtypes(subtypeRegistration);
		SubtypeManager subtypeManager = new SubtypeManager(subtypeRegistration.getInterpreters());
		StackHelper stackHelper = new StackHelper(subtypeManager);
		IIngredientHelper<ItemStack> itemStackHelper = new ItemStackHelper(subtypeManager, stackHelper, colorHelper);

		IngredientManagerBuilder builder = new IngredientManagerBuilder(subtypeManager, colorHelper);
		builder.register(
			VanillaTypes.ITEM_STACK,
			List.of(stack),
			itemStackHelper,
			new NoOpItemStackRenderer()
		);
		IIngredientManager ingredientManager = builder.build();

		String itemStackAsJson = stack.save(new CompoundTag()).toString();
		IBookmark bookmark = BookmarkConfig.loadItemStackBookmark(itemStackHelper, ingredientManager, itemStackAsJson);

		Assertions.assertInstanceOf(IngredientBookmark.class, bookmark);
		IngredientBookmark<?> ingredientBookmark = (IngredientBookmark<?>) bookmark;
		ItemStack decodedStack = ingredientBookmark.getIngredient()
			.getItemStack()
			.orElseThrow();

		Assertions.assertTrue(ItemStack.matches(stack, decodedStack), "Decoded item stack should match the original");
		CompoundTag decodedTag = decodedStack.getTag();
		Assertions.assertNotNull(decodedTag);
		CompoundTag decodedCustomData = decodedTag.getCompound("custom");
		assertTagId(decodedCustomData, "byte", Tag.TAG_BYTE);
		assertTagId(decodedCustomData, "short", Tag.TAG_SHORT);
		assertTagId(decodedCustomData, "int", Tag.TAG_INT);
		assertTagId(decodedCustomData, "long", Tag.TAG_LONG);
		assertTagId(decodedCustomData, "float", Tag.TAG_FLOAT);
		assertTagId(decodedCustomData, "double", Tag.TAG_DOUBLE);
		stack.setCount(64);
		stack.getOrCreateTag().putString("literal", "a*b$;=c");
		var typed = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).orElseThrow();
		var favoriteStore = new mezz.jei.gui.favorites.FavoriteRecipeStore();
		var target = new mezz.jei.gui.bookmarks.BookmarkIngredientKey(VanillaTypes.ITEM_STACK.getUid(), "test:unindexed_variant", stack.save(new CompoundTag()).toString(), typed);
		var favorite = new mezz.jei.gui.input.FocusedRecipe(new net.minecraft.resources.ResourceLocation("test", "type"), new net.minecraft.resources.ResourceLocation("test", "recipe"));
		favoriteStore.setFavorite(target, favorite, java.util.Map.of());
		var panel = new mezz.jei.gui.favorites.FavoriteRecipePanelState();
		var grid = new mezz.jei.gui.favorites.FavoriteRecipeGridSource(favoriteStore, panel, ingredientManager, null, null,
			(recipe, ingredient, choices) -> new mezz.jei.gui.favorites.FavoriteRecipeGridSource.ResolvedRecipeIngredients(ingredient, List.of()));
		Assertions.assertSame(typed, grid.getElements().get(0).getTypedIngredient());
		panel.cycleDisplayMode();
		Assertions.assertSame(typed, grid.getElements(4).get(0).getTypedIngredient());
		var loadedFavorite = FavoriteRecipeJsonSerializer.deserializeEntry(FavoriteRecipeJsonSerializer.serializeEntry(favoriteStore.entries().get(0))).orElseThrow();
		favoriteStore.setFavorites(List.of(loadedFavorite));
		var loadedGrid = new mezz.jei.gui.favorites.FavoriteRecipeGridSource(favoriteStore, panel, ingredientManager, null, null,
			(recipe, ingredient, choices) -> new mezz.jei.gui.favorites.FavoriteRecipeGridSource.ResolvedRecipeIngredients(ingredient, List.of()));
		Assertions.assertTrue(ItemStack.matches(stack, loadedGrid.getElements().get(0).getTypedIngredient().getItemStack().orElseThrow()));
		Assertions.assertTrue(ItemStack.matches(stack, loadedGrid.getElements(4).get(0).getTypedIngredient().getItemStack().orElseThrow()));
		stack.setCount(192);
		typed = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).orElseThrow();
		var shared = mezz.jei.common.chat.SharedChatIngredient.from(typed).orElseThrow();
		var restored = mezz.jei.common.chat.SharedChatIngredient.decode(shared.encode().orElseThrow()).orElseThrow()
			.resolve(ingredientManager).orElseThrow().getItemStack().orElseThrow();
		Assertions.assertTrue(ItemStack.matches(stack, restored));
		String rule = mezz.jei.gui.input.handlers.IngredientClipboardText.getNbtRule(stack);
		var entry = ConfigLineReader.read(List.of(rule.split("\n"))).get(0);
		var expression = mezz.jei.gui.match.IngredientExpression.parseIngredient(entry.value()).orElseThrow();
		Assertions.assertTrue(expression.matches(mezz.jei.gui.match.IngredientMatchInfo.fromIngredient(typed, false).orElseThrow()));
		stack.getOrCreateTag().putString("literal", "axxb$;=c");
		var changed = ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).orElseThrow();
		Assertions.assertFalse(expression.matches(mezz.jei.gui.match.IngredientMatchInfo.fromIngredient(changed, false).orElseThrow()));
	}

	private static void assertTagId(CompoundTag tag, String key, int expectedId) {
		Tag value = tag.get(key);
		Assertions.assertNotNull(value, "Expected custom data to contain key: " + key);
		Assertions.assertEquals((byte) expectedId, value.getId(), "Unexpected NBT tag type for key: " + key);
	}

	@Test
	public void savedCandidatesUseTheSameSelectionForScrollAndPopup() {
		ItemStack first = new ItemStack(Items.CHEST);
		first.getOrCreateTag().putString("variant", "first");
		ItemStack second = new ItemStack(Items.DIAMOND);
		second.getOrCreateTag().putString("variant", "not_in_ingredient_list");
		var manager = createManager(first);
		var firstTyped = manager.createTypedIngredient(VanillaTypes.ITEM_STACK, first, false).orElseThrow();
		var secondTyped = manager.createTypedIngredient(VanillaTypes.ITEM_STACK, second, false).orElseThrow();
		var firstKey = mezz.jei.gui.config.file.serializers.BookmarkIngredientKeySerializer.deserialize(mezz.jei.gui.config.file.serializers.BookmarkIngredientKeySerializer.serialize(BookmarkItemMetadataFactory.createPermutationKey(firstTyped, manager)));
		var secondKey = mezz.jei.gui.config.file.serializers.BookmarkIngredientKeySerializer.deserialize(mezz.jei.gui.config.file.serializers.BookmarkIngredientKeySerializer.serialize(BookmarkItemMetadataFactory.createPermutationKey(secondTyped, manager)));
		var recipe = new net.minecraft.resources.ResourceLocation("test", "recipe");
		for (boolean scroll : List.of(false, true)) {
			var bookmarks = new BookmarkList(null, null, manager, null, null, null, null);
			var original = IngredientBookmark.create(firstTyped, manager);
			bookmarks.addToListWithoutNotifying(original, false);
			bookmarks.moveBookmarkMetadataFromConfig(original, new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT,
				4, 3, BookmarkItemMetadata.CHANCE_FULL, recipe, recipe, Set.of(firstKey, secondKey)));
			Assertions.assertTrue(scroll ? bookmarks.cycleBookmarkPermutation(original, 1) : bookmarks.selectBookmarkPermutation(original, secondKey, false).isPresent());
			Assertions.assertEquals(1, bookmarks.getBookmarks().size());
			var replacement = bookmarks.getBookmarks().get(0);
			ItemStack chosen = replacement.getElement().getTypedIngredient().getItemStack().orElseThrow();
			Assertions.assertTrue(chosen.is(Items.DIAMOND));
			Assertions.assertEquals(second.getTag(), chosen.getTag());
			Assertions.assertEquals(3, bookmarks.getBookmarkMetadata(replacement).factor());
			Assertions.assertEquals(4, bookmarks.getBookmarkMetadata(replacement).multiplier());
		}
	}

	@Test
	public void sharedGroupRoundTrip() {
		ItemStack stack = new ItemStack(Items.CHEST, 12);
		stack.getOrCreateTag().putString("owner", "test");
		IIngredientManager manager = createManager(stack);
		var ingredient = manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).orElseThrow();
		BookmarkList bookmarks = new BookmarkList(null, null, manager, null, null, null, null);
		IBookmark original = IngredientBookmark.createPreservingAmount(ingredient, manager);
		bookmarks.addToListWithoutNotifying(original, false);
		int group = bookmarks.createGroupForBookmarks("Materials", List.of());
		bookmarks.addGroupFromConfig(new BookmarkGroup(group, "Materials", BookmarkViewMode.TODO_LIST, true, true, Set.of()));
		IBookmark scoped = IngredientBookmark.createPreservingAmount(ingredient, manager).withEqualityScope(group);
		bookmarks.addToListWithoutNotifying(scoped, false);
		bookmarks.moveBookmarkMetadataFromConfig(scoped, BookmarkItemMetadata.defaultForGroup(group).withMultiplier(7));
		String snapshot = BookmarkJsonSerializer.serializeGroupSnapshot(bookmarks, group, manager).orElseThrow();

		Assertions.assertTrue(BookmarkJsonSerializer.deserializeGroupSnapshot(snapshot, bookmarks, null, manager));
		Assertions.assertEquals(3, bookmarks.getBookmarks().size());
		Assertions.assertEquals(0, bookmarks.getBookmarkGroupId(original));
		int importedId = bookmarks.getBookmarkGroupId(bookmarks.getBookmarks().get(2));
		Assertions.assertNotEquals(group, importedId);
		BookmarkGroup imported = bookmarks.getBookmarkGroups().stream().filter(value -> value.id() == importedId).findFirst().orElseThrow();
		Assertions.assertTrue(imported.collapsed());
		Assertions.assertTrue(imported.craftingMode());
		Assertions.assertEquals(BookmarkViewMode.TODO_LIST, imported.viewMode());
		Assertions.assertEquals(7, bookmarks.getBookmarkMetadata(bookmarks.getBookmarks().get(2)).multiplier());
		Assertions.assertTrue(ItemStack.matches(stack, bookmarks.getBookmarks().get(2).getElement().getTypedIngredient().getItemStack().orElseThrow()));

		BookmarkList reloaded = new BookmarkList(null, null, manager, null, null, null, null);
		BookmarkJsonSerializer.deserialize(BookmarkJsonSerializer.serialize(bookmarks, manager), reloaded, null, manager);
		Assertions.assertEquals(3, reloaded.getBookmarks().size());
		Assertions.assertEquals(3, reloaded.getBookmarks().stream().map(reloaded::getBookmarkGroupId).distinct().count());
		Assertions.assertTrue(ItemStack.matches(stack, reloaded.getBookmarks().get(2).getElement().getTypedIngredient().getItemStack().orElseThrow()));

		var corrupt = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(snapshot), StandardCharsets.UTF_8)).getAsJsonObject();
		var brokenEntry = corrupt.getAsJsonArray("bookmarks").get(0).deepCopy().getAsJsonObject();
		brokenEntry.addProperty("type", "unknown");
		corrupt.getAsJsonArray("bookmarks").add(brokenEntry);
		String invalid = Base64.getUrlEncoder().withoutPadding().encodeToString(corrupt.toString().getBytes(StandardCharsets.UTF_8));
		Assertions.assertFalse(BookmarkJsonSerializer.deserializeGroupSnapshot(invalid, bookmarks, null, manager));
		Assertions.assertEquals(3, bookmarks.getBookmarks().size());
		Assertions.assertEquals(3, bookmarks.getBookmarkGroups().size());
	}

	@Test
	public void missingGroupKeepsOriginal() {
		ItemStack stack = new ItemStack(Items.IRON_INGOT, 32);
		IIngredientManager manager = createManager(stack);
		var ingredient = manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).orElseThrow();
		BookmarkList bookmarks = new BookmarkList(null, null, manager, null, null, null, null);
		IBookmark original = IngredientBookmark.createPreservingAmount(ingredient, manager);
		bookmarks.addToListWithoutNotifying(original, false);
		var key = BookmarkItemMetadataFactory.createPermutationKey(ingredient, manager);
		bookmarks.addMissingRecipeChainGroup(0, List.of(new RecipeChainTooltipModel.Item(key, BookmarkItemMetadata.defaultForGroup(0), 73, 0)));
		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
		Assertions.assertEquals(0, bookmarks.getBookmarkGroupId(original));
		IBookmark missing = bookmarks.getBookmarks().get(1);
		Assertions.assertEquals(73, bookmarks.getBookmarkMetadata(missing).amount());
		Assertions.assertEquals(1, missing.getElement().getTypedIngredient().getItemStack().orElseThrow().getCount());
		Assertions.assertTrue(bookmarks.isGroupCraftingMode(bookmarks.getBookmarkGroupId(missing)));
		BookmarkList reloaded = new BookmarkList(null, null, manager, null, null, null, null);
		BookmarkJsonSerializer.deserialize(BookmarkJsonSerializer.serialize(bookmarks, manager), reloaded, null, manager);
		Assertions.assertEquals(2, reloaded.getBookmarks().size());
		Assertions.assertEquals(73, reloaded.getBookmarkMetadata(reloaded.getBookmarks().get(1)).amount());
		bookmarks.moveBookmarkToGroup(missing, 0);
		Assertions.assertEquals(0, bookmarks.getBookmarkGroupId(bookmarks.getBookmarks().get(1)));
	}

	private static IIngredientManager createManager(ItemStack stack) {
		IColorHelper colors = new TestColorHelper();
		SubtypeRegistration registration = new SubtypeRegistration();
		new VanillaPlugin().registerItemSubtypes(registration);
		SubtypeManager subtypes = new SubtypeManager(registration.getInterpreters());
		IngredientManagerBuilder builder = new IngredientManagerBuilder(subtypes, colors);
		builder.register(VanillaTypes.ITEM_STACK, List.of(stack),
			new ItemStackHelper(subtypes, new StackHelper(subtypes), colors), new NoOpItemStackRenderer());
		return builder.build();
	}

	private static class NoOpItemStackRenderer implements IIngredientRenderer<ItemStack> {
		@Override
		public void render(GuiGraphics guiGraphics, ItemStack ingredient) {
		}

		@SuppressWarnings("removal")
		@Override
		public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
			return List.of();
		}

		@Override
		public void getTooltip(ITooltipBuilder tooltip, ItemStack ingredient, TooltipFlag tooltipFlag) {
		}
	}
}
