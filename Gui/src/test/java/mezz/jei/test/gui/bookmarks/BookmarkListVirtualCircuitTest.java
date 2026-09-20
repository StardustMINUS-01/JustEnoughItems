package mezz.jei.test.gui.bookmarks;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemMetadataFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Rect2i;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class BookmarkListVirtualCircuitTest {
	private static final ResourceLocation RECIPE_UID = new ResourceLocation("test", "assembler");
	private static final IIngredientType<IdentityIngredient> IDENTITY_INGREDIENT_TYPE = () -> IdentityIngredient.class;
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager();

	@BeforeAll
	public static void setup() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void collapsedRecipeDeletesAsBlockWhileExpandedEditorCanRemove() {
		for (boolean removeWhole : List.of(false, true)) {
			var bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
			var recipeLayout = layout(new Object(), List.of(List.of(item(Items.IRON_INGOT))), List.of(List.of(item(Items.GOLD_INGOT))));
			Assertions.assertTrue(bookmarks.addRecipeBookmarks(recipeLayout, false));
			bookmarks.addGroupFromConfig(new mezz.jei.gui.bookmarks.BookmarkGroup(1, "Chain", mezz.jei.gui.bookmarks.BookmarkViewMode.TODO_LIST, false, true, Set.of(RECIPE_UID)));
			for (var bookmark : bookmarks.getBookmarks()) {
				bookmarks.moveBookmarkMetadataFromConfig(bookmark, bookmarks.getBookmarkMetadata(bookmark).withGroupId(1));
			}
			var loose = mezz.jei.gui.bookmarks.IngredientBookmark.create(item(Items.DIAMOND), INGREDIENT_MANAGER).withEqualityScope(1);
			var outside = mezz.jei.gui.bookmarks.IngredientBookmark.create(item(Items.EMERALD), INGREDIENT_MANAGER).withEqualityScope(2);
			bookmarks.addToListWithoutNotifying(loose, false);
			bookmarks.addToListWithoutNotifying(outside, false);
			bookmarks.moveBookmarkMetadataFromConfig(loose, BookmarkItemMetadata.defaultForGroup(1));
			bookmarks.addGroupFromConfig(new mezz.jei.gui.bookmarks.BookmarkGroup(2, "Other"));
			bookmarks.moveBookmarkMetadataFromConfig(outside, new BookmarkItemMetadata(2, mezz.jei.gui.bookmarks.BookmarkItemType.RESULT,
				1, 1, BookmarkItemMetadata.CHANCE_FULL, RECIPE_UID, RECIPE_UID, Set.of(BookmarkItemMetadataFactory.createPermutationKey(item(Items.EMERALD), INGREDIENT_MANAGER))));
			bookmarks.notifyListenersOfChange();
			var before = bookmarks.getBookmarks();
			var input = before.stream().filter(bookmark -> bookmarks.getBookmarkMetadata(bookmark).type() == mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT).findFirst().orElseThrow();
			if (removeWhole) {
				var action = mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyRouter.resolveBookmarkKeyAction(
						mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeyContext.builder(mezz.jei.gui.bookmarks.hotkeys.BookmarkHotkeySubject.RECIPE_BOOKMARK)
							.hasIngredient(true).hasRecipe(true).isBookmarkSlot(true).build(),
						false,
						false
					)
					.orElseThrow();
				Assertions.assertTrue(bookmarks.onElementBookmarked(input.getElement(), action));
				Assertions.assertEquals(List.of(loose, outside), bookmarks.getBookmarks());
			} else {
				Assertions.assertTrue(bookmarks.removeExpandedRecipeBookmark(input));
				Assertions.assertEquals(before.size() - 1, bookmarks.getBookmarks().size());
			}
		}
	}

	@Test
	public void savedCandidatesAndSelectedOutputsStayWithinTheirSlots() {
		var oak = item(Items.OAK_PLANKS);
		var birch = item(Items.BIRCH_PLANKS);
		var gold = item(Items.GOLD_INGOT);
		var inputSlot = candidateSlot(RecipeIngredientRole.INPUT, List.of(oak, birch));
		var missingSlot = candidateSlot(RecipeIngredientRole.INPUT, List.of(item(Items.STONE)));
		var outputSlot = candidateSlot(RecipeIngredientRole.OUTPUT, List.of(item(Items.IRON_INGOT), gold));
		IRecipeSlotsView slots = () -> List.of(inputSlot, missingSlot, outputSlot);
		var recipeLayout = (IRecipeLayoutDrawable<?>) Proxy.newProxyInstance(IRecipeLayoutDrawable.class.getClassLoader(), new Class<?>[]{IRecipeLayoutDrawable.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getRecipeSlotsView")) {
					return slots;
				}
				throw new UnsupportedOperationException(method.getName());
			});
		var oakKey = BookmarkItemMetadataFactory.createPermutationKey(oak, INGREDIENT_MANAGER);
		var goldKey = BookmarkItemMetadataFactory.createPermutationKey(gold, INGREDIENT_MANAGER);
		var saved = List.of(
			new mezz.jei.gui.bookmarks.chain.RecipeChainInput(0, new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT, 1, 1, BookmarkItemMetadata.CHANCE_FULL, RECIPE_UID, RECIPE_UID, Set.of(oakKey)), oakKey, oak),
			new mezz.jei.gui.bookmarks.chain.RecipeChainInput(1, new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.RESULT, 1, 1, BookmarkItemMetadata.CHANCE_FULL, RECIPE_UID, RECIPE_UID, Set.of(goldKey)), goldKey, gold)
		);
		var selection = new mezz.jei.gui.bookmarks.BookmarkRecipeSelection(recipeLayout, saved, INGREDIENT_MANAGER);
		Assertions.assertEquals(List.of(oakKey), selection.getCandidates(inputSlot).stream().map(value -> BookmarkItemMetadataFactory.createPermutationKey(value, INGREDIENT_MANAGER)).toList());
		Assertions.assertTrue(selection.getCandidates(missingSlot).isEmpty());
		Assertions.assertTrue(missingSlot.getDisplayedIngredient().isEmpty());
		Assertions.assertTrue(outputSlot.getDisplayedItemStack().orElseThrow().is(Items.GOLD_INGOT));
		var targets = mezz.jei.gui.recipes.FavoriteRecipeTargetSelector.create(recipeLayout, value -> BookmarkItemMetadataFactory.createPermutationKey(value, INGREDIENT_MANAGER), null);
		Assertions.assertEquals(goldKey, targets.keyForSlot(outputSlot).orElseThrow());

		((mezz.jei.common.gui.IRecipeSlotCandidateView) inputSlot).setDisplayedCandidates(List.of(oak, birch));
		var state = new mezz.jei.gui.recipes.InputSlotSelectionState(INGREDIENT_MANAGER);
		state.setInputCandidates(Map.of(0, List.of(oak, birch), 1, List.of()));
		state.setSelectedKeys(Map.of(0, BookmarkItemMetadataFactory.createPermutationKey(birch, INGREDIENT_MANAGER)));
		var transfer = state.createTransferSlotsView(recipeLayout);
		Assertions.assertEquals(List.of(Items.BIRCH_PLANKS), transfer.getSlotViews(RecipeIngredientRole.INPUT).get(0).getAllIngredients().map(value -> value.getItemStack().orElseThrow().getItem()).toList());
		Assertions.assertTrue(transfer.getSlotViews(RecipeIngredientRole.INPUT).get(1).getAllIngredients().findAny().isEmpty());
		Assertions.assertSame(outputSlot, transfer.getSlotViews(RecipeIngredientRole.OUTPUT).get(0));
		var previous = mezz.jei.common.Internal.getOptionalJeiRuntime().orElse(null);
		try {
			mezz.jei.common.Internal.setRuntime((mezz.jei.api.runtime.IJeiRuntime) Proxy.newProxyInstance(mezz.jei.api.runtime.IJeiRuntime.class.getClassLoader(), new Class<?>[]{mezz.jei.api.runtime.IJeiRuntime.class},
				(proxy, method, args) -> {
					if (method.getName().equals("getIngredientManager")) {
						return INGREDIENT_MANAGER;
					}
					throw new UnsupportedOperationException(method.getName());
				}));
			Assertions.assertTrue(inputSlot.getTagKey().isPresent());
			Assertions.assertTrue(transfer.getSlotViews(RecipeIngredientRole.INPUT).get(0).getTagKey().isEmpty());
		} finally {
			mezz.jei.common.Internal.setRuntime(previous);
		}
	}

	@SuppressWarnings("unchecked")
	private static IRecipeSlotDrawable candidateSlot(RecipeIngredientRole role, List<ITypedIngredient<?>> values) {
		var candidates = new java.util.concurrent.atomic.AtomicReference<>(values);
		var selected = new java.util.concurrent.atomic.AtomicReference<ITypedIngredient<?>>();
		java.util.function.Supplier<Optional<ITypedIngredient<?>>> displayed = () -> Optional.<ITypedIngredient<?>>ofNullable(selected.get()).or(() -> candidates.get().stream().findFirst());
		return (IRecipeSlotDrawable) Proxy.newProxyInstance(IRecipeSlotDrawable.class.getClassLoader(), new Class<?>[]{IRecipeSlotDrawable.class, mezz.jei.common.gui.IRecipeSlotCandidateView.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getAllIngredients" -> values.stream();
				case "getAllIngredientsList" -> values;
				case "getDisplayedIngredient" -> displayed.get();
				case "getDisplayedItemStack" -> displayed.get().flatMap(ITypedIngredient::getItemStack);
				case "getDisplayedIngredients" -> displayed.get().stream();
				case "getCandidateIngredients" -> candidates.get().stream();
				case "getCandidates" -> candidates.get();
				case "getRole" -> role;
				case "getSlotName" -> Optional.empty();
				case "isEmpty" -> values.isEmpty();
				case "getTagKey" -> Optional.of(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, new ResourceLocation("test", "all_candidates")));
				case "setDisplayedCandidates" -> {
					candidates.set((List<ITypedIngredient<?>>) args[0]);
					yield null;
				}
				case "setSelectedCandidate" -> {
					selected.set((ITypedIngredient<?>) args[0]);
					yield null;
				}
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				case "toString" -> role.toString();
				default -> throw new UnsupportedOperationException(method.getName());
			});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void craftingTasksWaitForConfirmationAndReleaseTheirScope() {
		var category = (IRecipeCategory<Object>) Proxy.newProxyInstance(IRecipeCategory.class.getClassLoader(), new Class<?>[]{IRecipeCategory.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getRecipeType")) {
					return mezz.jei.api.constants.RecipeTypes.CRAFTING;
				}
				throw new UnsupportedOperationException(method.getName());
			});
		var slots = layout(new Object(), List.of(List.of(item(Items.OAK_PLANKS))), List.of(List.of(item(Items.STICK)))).getRecipeSlotsView();
		var layout = (IRecipeLayoutDrawable<?>) Proxy.newProxyInstance(IRecipeLayoutDrawable.class.getClassLoader(), new Class<?>[]{IRecipeLayoutDrawable.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getRecipeCategory" -> category;
				case "getRecipeSlotsView" -> slots;
				default -> throw new UnsupportedOperationException(method.getName());
			});
		var material = BookmarkIngredientKey.of("minecraft:item_stack", "minecraft:oak_planks");
		var result = BookmarkIngredientKey.of("minecraft:item_stack", "minecraft:stick");
		var crafting = mezz.jei.api.constants.RecipeTypes.CRAFTING.getUid();
		var chain = List.of(
			new mezz.jei.gui.bookmarks.chain.RecipeChainInput(0, new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.RESULT, 1, 1, BookmarkItemMetadata.CHANCE_FULL, crafting, RECIPE_UID, Set.of(result))),
			new mezz.jei.gui.bookmarks.chain.RecipeChainInput(1, new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT, 1, 1, BookmarkItemMetadata.CHANCE_FULL, crafting, RECIPE_UID, Set.of(material)))
		);
		for (String scenario : List.of("ack timeout", "sync timeout", "success", "zero ack", "cancel")) {
			var inventory = new java.util.concurrent.atomic.AtomicReference<>(List.of(
				new mezz.jei.gui.bookmarks.chain.RecipeChainInput(-1, new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.ITEM, 1, 1, BookmarkItemMetadata.CHANCE_FULL, null, null, Set.of(material)))));
			List<mezz.jei.common.network.packets.PacketJei> packets = new java.util.ArrayList<>();
			var task = mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingBridge.createTask(chain, Set.of(), 9, 7,
				inventory::get, () -> List.of(new ItemStack(Items.OAK_PLANKS)), id -> Optional.of(layout), packets::add, () -> true, false)
				.orElseThrow();
			var runner = new mezz.jei.gui.bookmarks.hotkeys.BookmarkAutoCraftingRunner();
			try {
				Assertions.assertTrue(runner.start(task), scenario);
				Assertions.assertEquals(1, packets.size());
				Assertions.assertEquals(Set.of(Items.OAK_PLANKS, Items.STICK), mezz.jei.gui.bookmarks.chain.BookmarkCraftingScope.getInterests().stream().map(ItemStack::getItem).collect(java.util.stream.Collectors.toSet()));
				var packet = packets.get(0);
				Assertions.assertEquals(mezz.jei.common.network.packets.PacketCraftingGridCraft.CHANNEL, packet.getChannelId());
				var buffer = packet.getPacketData().getLeft();
				try {
					Assertions.assertEquals(mezz.jei.common.network.PacketIdServer.CRAFTING_GRID_CRAFT_WITH_RECIPE.ordinal(), buffer.readUnsignedByte());
					Assertions.assertEquals(7, buffer.readVarInt());
					Assertions.assertEquals(task.taskId(), buffer.readVarInt());
					Assertions.assertEquals(1, buffer.readVarInt());
					Assertions.assertEquals(RECIPE_UID, buffer.readNullable(net.minecraft.network.FriendlyByteBuf::readResourceLocation));
				} finally {
					buffer.release();
				}
				if (scenario.equals("cancel")) {
					runner.stop();
				} else if (scenario.equals("zero ack")) {
					runner.handleAck(task.taskId(), 1, 0);
				} else if (scenario.equals("success")) {
					runner.handleAck(task.taskId(), 999, 1);
					runner.handleAck(task.taskId(), 1, 1);
					Assertions.assertTrue(runner.hasActiveTask());
					inventory.set(List.of(new mezz.jei.gui.bookmarks.chain.RecipeChainInput(-1, new BookmarkItemMetadata(0, mezz.jei.gui.bookmarks.BookmarkItemType.ITEM, 1, 1, BookmarkItemMetadata.CHANCE_FULL, null, null, Set.of(result)))));
					runner.tick();
				} else {
					if (scenario.equals("sync timeout")) {
						task.handleAck(1, 1);
					}
					for (int tick = 0; tick < 40; tick++) {
						runner.tick();
						Assertions.assertTrue(runner.hasActiveTask(), scenario);
					}
					runner.tick();
				}
				Assertions.assertFalse(runner.hasActiveTask(), scenario);
				Assertions.assertEquals(1, packets.size(), scenario);
				Assertions.assertTrue(mezz.jei.gui.bookmarks.chain.BookmarkCraftingScope.getInterests().isEmpty(), scenario);
			} finally {
				runner.stop();
			}
		}
	}

	@Test
	public void recipeBookmarksIncludeZeroCostGtmVirtualCircuitInput() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new GTRecipe(7),
			List.of(List.of(item(Items.IRON_INGOT))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, false));

		IBookmark circuitBookmark = bookmarks.getBookmarks()
			.stream()
			.filter(bookmark -> bookmark.getElement()
				.getTypedIngredient()
				.getItemStack()
				.map(stack -> stack.is(Items.REPEATER))
				.orElse(false))
			.findFirst()
			.orElseThrow();
		BookmarkItemMetadata metadata = bookmarks.getBookmarkMetadata(circuitBookmark);
		Assertions.assertEquals(0, metadata.factor());
		Assertions.assertEquals(0, metadata.amount());
		Assertions.assertEquals(RECIPE_UID, metadata.recipeUid());
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.NONCONSUMABLE, metadata.type());
	}

	@Test
	public void recipeBookmarksMarkGtmNonConsumableInputsAsCatalysts() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		TestRecipeLayout layout = layout(
			new GTRecipe(7, mold),
			List.of(List.of(item(Items.IRON_INGOT)), List.of(typed(mold))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, true));
		var recipe = new mezz.jei.gui.input.FocusedRecipe(layout.getRecipeCategory().getRecipeType().getUid(), RECIPE_UID);
		var cache = new mezz.jei.gui.favorites.RecipeLayoutBuildCache();
		cache.put(recipe, layout);
		var resolver = new mezz.jei.gui.favorites.FavoriteTreeRecipeLayoutResolver(null, null, INGREDIENT_MANAGER);
		var inputs = resolver.resolve(recipe, cache).orElseThrow().inputs();
		Assertions.assertEquals(1, inputs.size());
		Assertions.assertEquals(0, inputs.get(0).inputSlotIndex());
		Assertions.assertTrue(inputs.get(0).permutationIngredients().get(0).getItemStack().orElseThrow().is(Items.IRON_INGOT));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.SHEARS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.NONCONSUMABLE, metadata.type());
	}

	@Test
	public void syntheticGtmNonConsumableCatalystKeepsItsRawAmount() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ItemStack mold = new ItemStack(Items.SHEARS, 3);
		TestRecipeLayout layout = layout(
			new GTRecipe(7, mold),
			List.of(List.of(item(Items.IRON_INGOT))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, true));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.SHEARS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.NONCONSUMABLE, metadata.type());
		Assertions.assertEquals(3, metadata.factor());
		Assertions.assertEquals(3, metadata.amount());
	}

	@Test
	public void recipeBookmarksKeepDistinctGtmCatalystsVisible() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		ItemStack firstCatalyst = new ItemStack(Items.SHEARS, 3);
		ItemStack secondCatalyst = new ItemStack(Items.FLINT_AND_STEEL, 2);
		TestRecipeLayout layout = layout(
			new GTRecipe(7, List.of(firstCatalyst, secondCatalyst)),
			List.of(
				List.of(item(Items.IRON_INGOT)),
				List.of(typed(firstCatalyst)),
				List.of(typed(secondCatalyst))
			),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, true));

		List<BookmarkItemMetadata> catalysts = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.SHEARS) || stack.is(Items.FLINT_AND_STEEL))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.toList();
		Assertions.assertEquals(2, catalysts.size());
		Assertions.assertTrue(catalysts.stream().allMatch(metadata -> metadata.type() == mezz.jei.gui.bookmarks.BookmarkItemType.NONCONSUMABLE));
	}

	@Test
	public void repeatedRecipeBookmarkingDeduplicatesIdentityOnlyIngredients() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(List.of(identity("styrene_butadiene_rubber"))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		bookmarks.addRecipeBookmarks(layout, false);
		bookmarks.addRecipeBookmarks(layout, false);

		Assertions.assertEquals(2, bookmarks.getBookmarks().size());
	}

	@Test
	public void onlyInputsToggleCatalyst() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		bookmarks.addRecipeBookmarks(layout(new Object(), List.of(List.of(item(Items.IRON_INGOT))), List.of(List.of(item(Items.GOLD_INGOT)))), false);
		IBookmark output = bookmarks.getBookmarks().stream().filter(b -> bookmarks.getBookmarkMetadata(b).type() == mezz.jei.gui.bookmarks.BookmarkItemType.RESULT).findFirst().orElseThrow();
		var original = bookmarks.getBookmarkMetadata(output);
		Assertions.assertFalse(bookmarks.toggleBookmarkInputCatalyst(output));
		Assertions.assertFalse(bookmarks.toggleBookmarkInputCatalyst(output));
		Assertions.assertEquals(original, bookmarks.getBookmarkMetadata(output));
		IBookmark input = bookmarks.getBookmarks().stream().filter(b -> bookmarks.getBookmarkMetadata(b).type() == mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT).findFirst().orElseThrow();
		Assertions.assertTrue(bookmarks.toggleBookmarkInputCatalyst(input));
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.NONCONSUMABLE, bookmarks.getBookmarkMetadata(input).type());
		Assertions.assertTrue(bookmarks.toggleBookmarkInputCatalyst(input));
		Assertions.assertEquals(mezz.jei.gui.bookmarks.BookmarkItemType.INGREDIENT, bookmarks.getBookmarkMetadata(input).type());
	}

	@Test
	public void projectionLocksSelectedInputPermutation() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(List.of(item(Items.GLASS), item(Items.RED_STAINED_GLASS), item(Items.BLUE_STAINED_GLASS))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);
		BookmarkIngredientKey selectedKey = BookmarkItemMetadataFactory.createPermutationKey(
			item(Items.RED_STAINED_GLASS),
			INGREDIENT_MANAGER
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, false, Map.of(0, selectedKey)));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.RED_STAINED_GLASS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(Set.of(selectedKey), metadata.permutations());
	}

	@Test
	public void projectionWithoutSelectedInputKeepsAllVariants() {
		BookmarkList bookmarks = new BookmarkList(null, null, INGREDIENT_MANAGER, null, null, null, null);
		TestRecipeLayout layout = layout(
			new Object(),
			List.of(List.of(item(Items.GLASS), item(Items.RED_STAINED_GLASS), item(Items.BLUE_STAINED_GLASS))),
			List.of(List.of(item(Items.GOLD_INGOT)))
		);

		Assertions.assertTrue(bookmarks.addRecipeBookmarks(layout, false));

		BookmarkItemMetadata metadata = bookmarks.getBookmarks().stream()
			.filter(bookmark -> bookmark.getElement().getTypedIngredient().getItemStack()
				.map(stack -> stack.is(Items.GLASS))
				.orElse(false))
			.map(bookmarks::getBookmarkMetadata)
			.findFirst()
			.orElseThrow();
		Assertions.assertEquals(
			Set.of(
				BookmarkItemMetadataFactory.createPermutationKey(item(Items.GLASS), INGREDIENT_MANAGER),
				BookmarkItemMetadataFactory.createPermutationKey(item(Items.RED_STAINED_GLASS), INGREDIENT_MANAGER),
				BookmarkItemMetadataFactory.createPermutationKey(item(Items.BLUE_STAINED_GLASS), INGREDIENT_MANAGER)
			),
			metadata.permutations()
		);
	}

	private static TestRecipeLayout layout(
		Object recipe,
		List<List<@Nullable ITypedIngredient<?>>> inputs,
		List<List<@Nullable ITypedIngredient<?>>> outputs
	) {
		return new TestRecipeLayout(new TestRecipeCategory(), recipe, inputs, outputs);
	}

	private static ITypedIngredient<ItemStack> item(net.minecraft.world.level.ItemLike item) {
		return typed(new ItemStack(item));
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

			@Override
			public ITypedIngredient<ItemStack> normalize(mezz.jei.api.ingredients.IIngredientHelper<ItemStack> helper) {
				return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
			}
		};
	}

	private static ITypedIngredient<IdentityIngredient> identity(String id) {
		return new ITypedIngredient<>() {
			@Override
			public IIngredientType<IdentityIngredient> getType() {
				return IDENTITY_INGREDIENT_TYPE;
			}

			@Override
			public IdentityIngredient getIngredient() {
				return new IdentityIngredient(id);
			}

			@Override
			public ITypedIngredient<IdentityIngredient> normalize(mezz.jei.api.ingredients.IIngredientHelper<IdentityIngredient> helper) {
				return mezz.jei.common.ingredients.TypedIngredient.createUnvalidated(getType(), helper.normalizeIngredient(getIngredient()));
			}
		};
	}

	private static IIngredientManager ingredientManager() {
		return (IIngredientManager) Proxy.newProxyInstance(
			BookmarkListVirtualCircuitTest.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if ("getIngredientHelper".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return itemHelper();
				}
				if ("getIngredientHelper".equals(method.getName()) && args[0] == IDENTITY_INGREDIENT_TYPE) {
					return identityHelper();
				}
				if ("createTypedIngredient".equals(method.getName()) && args[0] == VanillaTypes.ITEM_STACK) {
					return Optional.of(typed((ItemStack) args[1]));
				}
				if ("normalizeTypedIngredient".equals(method.getName())) {
					return args[0];
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IIngredientHelper<IdentityIngredient> identityHelper() {
		return new IIngredientHelper<>() {
			@Override
			public IIngredientType<IdentityIngredient> getIngredientType() {
				return IDENTITY_INGREDIENT_TYPE;
			}

			@Override
			public String getDisplayName(IdentityIngredient ingredient) {
				return ingredient.id;
			}

			@Override
			public String getUniqueId(IdentityIngredient ingredient, UidContext context) {
				return ingredient.id;
			}

			@Override
			public ResourceLocation getResourceLocation(IdentityIngredient ingredient) {
				return new ResourceLocation("test", ingredient.id);
			}

			@Override
			public IdentityIngredient copyIngredient(IdentityIngredient ingredient) {
				return new IdentityIngredient(ingredient.id);
			}

			@Override
			public String getErrorInfo(IdentityIngredient ingredient) {
				return ingredient.id;
			}
		};
	}

	private static final class IdentityIngredient {
		private final String id;

		private IdentityIngredient(String id) {
			this.id = id;
		}
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
			public String getErrorInfo(ItemStack ingredient) {
				return ingredient.toString();
			}
		};
	}

	private record TestRecipeCategory() implements IRecipeCategory<Object> {
		@Override
		public RecipeType<Object> getRecipeType() {
			return RecipeType.create("gtceu", "assembler", Object.class);
		}

		@Override
		public Component getTitle() {
			return Component.literal("assembler");
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

	private record TestRecipeLayout(
		TestRecipeCategory category,
		Object recipe,
		List<List<@Nullable ITypedIngredient<?>>> inputs,
		List<List<@Nullable ITypedIngredient<?>>> outputs
	) implements IRecipeLayoutDrawable<Object> {
		@Override
		public void setPosition(int posX, int posY) {
		}

		@Override
		public void drawRecipe(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public void drawOverlays(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY) {
		}

		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return true;
		}

		@Override
		public <T> Optional<T> getIngredientUnderMouse(int mouseX, int mouseY, IIngredientType<T> ingredientType) {
			return Optional.empty();
		}

		@Override
		public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
			return Optional.empty();
		}

		@Override
		public Rect2i getRect() {
			return new Rect2i(0, 0, 1, 1);
		}

		@Override
		public Rect2i getRectWithBorder() {
			return getRect();
		}

		@Override
		public Rect2i getSideButtonArea(int buttonIndex) {
			return getRect();
		}

		@Override
		public IRecipeSlotsView getRecipeSlotsView() {
			return () -> Stream.concat(
					inputs.stream().map(i -> new TestRecipeSlotView(RecipeIngredientRole.INPUT, i)),
					outputs.stream().map(i -> new TestRecipeSlotView(RecipeIngredientRole.OUTPUT, i))
				)
				.map(IRecipeSlotView.class::cast)
				.toList();
		}

		@Override
		public IRecipeCategory<Object> getRecipeCategory() {
			return category;
		}

		@Override
		public Object getRecipe() {
			return recipe;
		}

		@Override
		public IJeiInputHandler getInputHandler() {
			return () -> ScreenRectangle.empty();
		}

		@Override
		public void tick() {
		}
	}

	private record TestRecipeSlotView(
		RecipeIngredientRole role,
		List<@Nullable ITypedIngredient<?>> ingredients
	) implements IRecipeSlotView {
		@Override
		public Stream<ITypedIngredient<?>> getAllIngredients() {
			return ingredients.stream().filter(Objects::nonNull);
		}

		@Override
		public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
			return ingredients;
		}

		@Override
		public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
			return ingredients.stream().filter(Objects::nonNull).findFirst();
		}

		@Override
		public RecipeIngredientRole getRole() {
			return role;
		}

		@Override
		public void drawHighlight(net.minecraft.client.gui.GuiGraphics guiGraphics, int color) {
		}

		@Override
		public Optional<net.minecraft.tags.TagKey<?>> getTagKey() { return Optional.empty(); }

		@Override
		public java.util.stream.Stream<ITypedIngredient<?>> getDisplayedIngredients() { return getAllIngredients(); }

		@Override
		public Optional<String> getSlotName() {
			return Optional.empty();
		}
	}
}
