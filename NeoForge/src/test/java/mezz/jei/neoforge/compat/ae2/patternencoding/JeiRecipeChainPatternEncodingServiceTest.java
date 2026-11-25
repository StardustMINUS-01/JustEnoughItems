package mezz.jei.neoforge.compat.ae2.patternencoding;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.inv.AppEngInternalInventory;
import io.netty.buffer.Unpooled;
import mezz.jei.gui.compat.ae2.PatternEncodingFeedback.Outcome;
import mezz.jei.neoforge.compat.ae2.patternencoding.JeiRecipeChainPatternEncodingService.PreparedEntry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class JeiRecipeChainPatternEncodingServiceTest {
	@BeforeAll
	public static void setup() {
		LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void rejectsNonTerminalMenuBeforeAnyPlayerStateIsChecked() {
		JeiPatternEncodeRequestWire request = processingRequest(List.of(), List.of());

		JeiBatchPatternEncodeResult result = JeiRecipeChainPatternEncodingService.encodeRecipeChainPatterns(null, new FakeMenu(), List.of(request));

		Assertions.assertEquals(JeiPatternEncodeStopReason.NOT_PATTERN_ENCODING_TERMINAL, result.stopReason());
		Assertions.assertEquals(List.of(new JeiPatternEncodeEntryResult(request.recipeTypeUid(), request.recipeUid(), JeiPatternEncodeEntryStatus.NOT_PROCESSED)), result.entries());
	}

	@ParameterizedTest
	@CsvSource({"1, 1", "0, 2", "2, 0"})
	public void encodingConsumesBlanksAcrossTerminalSlots(int inputCount, int editingCount) {
		var terminal = new SimpleContainer(2);
		var input = new Slot(terminal, 0, 0, 0);
		var editing = new Slot(terminal, 1, 0, 0);
		input.set(new ItemStack(Items.PAPER, inputCount));
		editing.set(new ItemStack(Items.PAPER, editingCount));
		var blanks = new JeiRecipeChainPatternEncodingService.TerminalBlankPatterns(List.of(input, editing), Items.PAPER);
		var destination = new AppEngInternalInventory(1);
		var pattern = new ItemStack(Items.MAP);

		Assertions.assertEquals(2, blanks.count());
		Assertions.assertEquals(JeiPatternEncodeStopReason.NONE, blanks.writePattern(destination, pattern, false));
		Assertions.assertEquals(Math.max(0, inputCount - 1), input.getItem().getCount());
		Assertions.assertEquals(JeiPatternEncodeStopReason.NONE, blanks.writePattern(destination, pattern, false));
		Assertions.assertEquals(0, blanks.count());
		editing.set(pattern.copy());
		Assertions.assertEquals(0, blanks.count());
		Assertions.assertEquals(JeiPatternEncodeStopReason.NO_BLANK_PATTERN, blanks.writePattern(destination, pattern, false));
		Assertions.assertTrue(editing.getItem().is(Items.MAP));
		Assertions.assertEquals(2, destination.getStackInSlot(0).getCount());
	}

	@Test
	public void fullInventoryAndCreativeEncodingKeepTheBlank() {
		Slot input = new Slot(new SimpleContainer(1), 0, 0, 0);
		input.set(new ItemStack(Items.PAPER));
		var blanks = new JeiRecipeChainPatternEncodingService.TerminalBlankPatterns(List.of(input), Items.PAPER);
		var destination = new AppEngInternalInventory(1);
		destination.setItemDirect(0, new ItemStack(Items.STONE));
		var pattern = new ItemStack(Items.MAP);
		Assertions.assertEquals(JeiPatternEncodeStopReason.NO_INVENTORY_SPACE, blanks.writePattern(destination, pattern, false));
		Assertions.assertEquals(1, blanks.count());
		destination.setItemDirect(0, ItemStack.EMPTY);
		Assertions.assertEquals(JeiPatternEncodeStopReason.NONE, blanks.writePattern(destination, pattern, true));
		Assertions.assertEquals(1, blanks.count());
		Assertions.assertTrue(destination.getStackInSlot(0).is(Items.MAP));
	}

	@Test
	public void encodingReplyIdentifiesTheFullInventoryEntryAndLeavesLaterRecipesUnmarked() {
		var prepared = List.of(
			PreparedEntry.skipped(feedbackRequest("existing"), JeiPatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT),
			PreparedEntry.skipped(feedbackRequest("invalid"), JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE),
			PreparedEntry.writable(feedbackRequest("first"), new ItemStack(Items.MAP)),
			PreparedEntry.writable(feedbackRequest("second"), new ItemStack(Items.BOOK)),
			PreparedEntry.writable(feedbackRequest("third"), new ItemStack(Items.COMPASS))
		);
		var source = new Slot(new SimpleContainer(1), 0, 0, 0);
		var blanks = new JeiRecipeChainPatternEncodingService.TerminalBlankPatterns(List.of(source), Items.PAPER);
		var destination = new AppEngInternalInventory(1);
		var shortage = JeiRecipeChainPatternEncodingService.writePreparedEntries(prepared, blanks, destination, false);
		Assertions.assertEquals(JeiPatternEncodeStopReason.NO_BLANK_PATTERN, shortage.stopReason());
		Assertions.assertEquals(List.of(Outcome.FAILED, Outcome.FAILED, Outcome.NONE, Outcome.NONE, Outcome.NONE),
			PacketPatternEncodingResult.from(shortage).entries.stream().map(entry -> entry.outcome()).toList());

		source.set(new ItemStack(Items.PAPER, 3));
		var result = JeiRecipeChainPatternEncodingService.writePreparedEntries(prepared, blanks, destination, false);
		Assertions.assertEquals(JeiPatternEncodeStopReason.NO_INVENTORY_SPACE, result.stopReason());
		Assertions.assertEquals(1, result.encodedCount());
		Assertions.assertEquals(2, blanks.count());
		var reply = PacketPatternEncodingResult.from(result);
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			PacketPatternEncodingResult.STREAM_CODEC.encode(buffer, reply);
			var received = PacketPatternEncodingResult.STREAM_CODEC.decode(buffer);
			Assertions.assertEquals(reply.message, received.message);
			String prefix = "jei.message.ae2.pattern_encoding.";
			var separator = Component.translatableWithFallback(prefix + "separator", ", ");
			Assertions.assertEquals(List.of(Component.translatable(prefix + "inventory_full"), separator,
				Component.translatable(prefix + "encoded", 1), separator, Component.translatable(prefix + "existing", 1),
				separator, Component.translatable(prefix + "invalid", 1)), received.message.getSiblings());
			Assertions.assertEquals(List.of(Outcome.FAILED, Outcome.FAILED, Outcome.ENCODED, Outcome.INVENTORY_FULL, Outcome.NONE),
				received.entries.stream().map(entry -> entry.outcome()).toList());
			Assertions.assertEquals(prepared.stream().map(entry -> entry.request().recipeUid()).toList(),
				received.entries.stream().map(entry -> entry.recipe().id()).toList());
		} finally {
			buffer.release();
		}
	}

	@Test
	public void mixedRequestKeepsConversionFailuresInTheFinalReply() {
		var valid = feedbackRequest("valid");
		var invalid = new JeiPatternEncodeEntryResult(valid.recipeTypeUid(), ResourceLocation.parse("test:invalid"), JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE);
		var encoded = new JeiPatternEncodeEntryResult(valid.recipeTypeUid(), valid.recipeUid(), JeiPatternEncodeEntryStatus.ENCODED);
		var packet = PacketEncodeRecipeChainPatterns.create(List.of(valid), List.of(invalid), true);
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			Assertions.assertEquals(PacketEncodeRecipeChainPatterns.WITH_FAILURES, packet.type());
			packet.streamCodec().encode(buffer, packet);
			var received = PacketEncodeRecipeChainPatterns.WITH_FAILURES_CODEC.decode(buffer);
			var merged = received.mergeFailures(JeiBatchPatternEncodeResult.fromEntries(JeiPatternEncodeStopReason.NONE, List.of(encoded)));
			var reply = PacketPatternEncodingResult.from(merged);
			String prefix = "jei.message.ae2.pattern_encoding.";
			Assertions.assertEquals(List.of(Component.translatable(prefix + "encoded", 1), Component.translatableWithFallback(prefix + "separator", ", "),
				Component.translatable(prefix + "invalid", 1)), reply.message.getSiblings());
			Assertions.assertEquals(List.of(invalid, encoded), merged.entries());
			Assertions.assertEquals(List.of(Outcome.FAILED, Outcome.ENCODED), reply.entries.stream().map(entry -> entry.outcome()).toList());

			var stopped = received.mergeFailures(JeiBatchPatternEncodeResult.fromEntries(JeiPatternEncodeStopReason.NO_INVENTORY_SPACE,
				List.of(new JeiPatternEncodeEntryResult(valid.recipeTypeUid(), valid.recipeUid(), JeiPatternEncodeEntryStatus.INVENTORY_FULL))));
			Assertions.assertEquals(JeiPatternEncodeStopReason.NO_INVENTORY_SPACE, stopped.stopReason());
			Assertions.assertEquals(1, stopped.skippedInvalidCount());
		} finally {
			buffer.release();
		}
	}

	@Test
	public void reportsAllConversionFailuresInOneLocalReply() {
		var type = ResourceLocation.parse("test:processing");
		var first = new JeiPatternEncodeEntryResult(type, ResourceLocation.parse("test:first"), JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE);
		var second = new JeiPatternEncodeEntryResult(type, ResourceLocation.parse("test:second"), JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE);
		var local = PacketPatternEncodingResult.invalidRecipes(List.of(first, second));
		Assertions.assertEquals(List.of(Component.translatable("jei.message.ae2.pattern_encoding.invalid", 2)), local.message.getSiblings());
		Assertions.assertEquals(List.of(first.recipeUid(), second.recipeUid()), local.entries.stream().map(entry -> entry.recipe().id()).toList());
		Assertions.assertTrue(local.entries.stream().allMatch(entry -> entry.outcome() == Outcome.FAILED));
	}

	@Test
	public void legacyRequestCountsFailedConversionsWithoutAnEncodablePattern() {
		var valid = feedbackRequest("valid");
		var invalid = new JeiPatternEncodeEntryResult(valid.recipeTypeUid(), ResourceLocation.parse("test:invalid"), JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE);
		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			var legacy = PacketEncodeRecipeChainPatterns.create(List.of(valid), List.of(invalid), false);
			Assertions.assertEquals(PacketEncodeRecipeChainPatterns.TYPE, legacy.type());
			legacy.streamCodec().encode(buffer, legacy);
			var requests = JeiPatternEncodeRequestWire.STREAM_CODEC.apply(ByteBufCodecs.list(JeiPatternEncodeRequestWire.MAX_REQUESTS)).decode(buffer);
			Assertions.assertEquals(List.of(invalid.recipeUid(), valid.recipeUid()), requests.stream().map(JeiPatternEncodeRequestWire::recipeUid).toList());
			Assertions.assertNull(JeiRecipeChainPatternEncodingService.createProcessingPattern(requests.getFirst()));
			Assertions.assertEquals(valid, requests.getLast());
			Assertions.assertFalse(buffer.isReadable());
		} finally {
			buffer.release();
		}
	}

	private static JeiPatternEncodeRequestWire feedbackRequest(String id) {
		return new JeiPatternEncodeRequestWire(ResourceLocation.parse("test:category"), ResourceLocation.fromNamespaceAndPath("test", id),
			JeiPatternEncodeMode.PROCESSING, List.of(), List.of(), List.of(), List.of(), null, false, false);
	}

	@Test
	public void existingPatternMessageDescribesOneOutputOrCountsSkippedRecipes() {
		String prefix = "jei.message.ae2.pattern_encoding.";
		var category = ResourceLocation.parse("test:category");
		var first = new JeiPatternEncodeEntryResult(category, ResourceLocation.parse("test:first"), JeiPatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT);
		var second = new JeiPatternEncodeEntryResult(category, ResourceLocation.parse("test:second"), JeiPatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT);
		var single = JeiBatchPatternEncodeResult.fromEntries(JeiPatternEncodeStopReason.NONE, List.of(first));
		var batch = JeiBatchPatternEncodeResult.fromEntries(JeiPatternEncodeStopReason.NONE, List.of(first, second));
		Assertions.assertEquals(List.of(Component.translatableWithFallback(prefix + "existing_single", "This output already has a pattern")),
			PacketPatternEncodingResult.from(single).message.getSiblings());
		Assertions.assertEquals(List.of(Component.translatable(prefix + "existing", 2)), PacketPatternEncodingResult.from(batch).message.getSiblings());
	}

	@ParameterizedTest
	@MethodSource("invalidCatalystRequests")
	public void rejectsInvalidCatalysts(JeiPatternEncodeRequestWire request, String expectedFailure) {
		Assertions.assertEquals(expectedFailure, JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	private static List<Arguments> invalidCatalystRequests() {
		JeiPatternEncodeRequestWire craftingRequest = new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.CRAFTING,
			List.of(),
			List.of(),
			List.of(new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1))),
			List.of(),
			null,
			false,
			false
		);
		return List.of(
			Arguments.of(craftingRequest, "Catalysts are only supported for processing patterns"),
			Arguments.of(
				processingRequest(
					List.of(stack(Items.IRON_INGOT, 1)),
					List.of(new JeiPatternCatalystWire(2, stack(Items.IRON_INGOT, 1)))
				),
				"Catalyst source slot is out of range"
			),
			Arguments.of(
				processingRequest(
					List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
					List.of(
						new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1)),
						new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1))
					)
				),
				"Catalyst source slots must be unique"
			),
			Arguments.of(
				processingRequest(
					List.of(stack(Items.IRON_INGOT, 1)),
					List.of(new JeiPatternCatalystWire(0, stack(Items.GOLD_INGOT, 1)))
				),
				"Catalyst must match its processing input"
			),
			Arguments.of(
				processingRequest(
					List.of(stack(Items.IRON_INGOT, 1)),
					List.of(new JeiPatternCatalystWire(0, stack(Items.IRON_INGOT, 1)))
				),
				"Processing pattern has no real input"
			)
		);
	}

	@Test
	public void acceptsValidProcessingRequestWithCatalyst() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 1), stack(Items.GOLD_INGOT, 1)),
			List.of(new JeiPatternCatalystWire(1, stack(Items.GOLD_INGOT, 1)))
		);

		Assertions.assertNull(JeiRecipeChainPatternEncodingService.validateCatalysts(request));
	}

	@Test
	public void networkPatternPrimaryOutputIsQueriedOnlyOnce() {
		AEKey primaryOutput = AEItemKey.of(new ItemStack(Items.DIAMOND));
		Set<AEKey> knownPrimaryOutputs = new HashSet<>();
		AtomicInteger networkQueryCount = new AtomicInteger();
		Predicate<AEKey> networkHasPattern = key -> {
			networkQueryCount.incrementAndGet();
			return primaryOutput.equals(key);
		};

		boolean firstIsNew = JeiRecipeChainPatternEncodingService.markPrimaryOutputIfNew(
			primaryOutput,
			knownPrimaryOutputs,
			networkHasPattern
		);
		boolean secondIsNew = JeiRecipeChainPatternEncodingService.markPrimaryOutputIfNew(
			primaryOutput,
			knownPrimaryOutputs,
			networkHasPattern
		);

		Assertions.assertFalse(firstIsNew);
		Assertions.assertFalse(secondIsNew);
		Assertions.assertTrue(knownPrimaryOutputs.contains(primaryOutput));
		Assertions.assertEquals(1, networkQueryCount.get());
	}

	@Test
	public void removesCatalystInputsWithoutChangingSavedAmounts() {
		JeiPatternEncodeRequestWire request = processingRequest(
			List.of(stack(Items.IRON_INGOT, 4), stack(Items.GOLD_INGOT, 64)),
			List.of(new JeiPatternCatalystWire(1, stack(Items.GOLD_INGOT, 64)))
		);

		List<GenericStack> realInputs = JeiRecipeChainPatternEncodingService.removeCatalystInputs(request.sparseInputs(), request.catalysts());

		Assertions.assertEquals(stack(Items.IRON_INGOT, 4), realInputs.get(0));
		Assertions.assertNull(realInputs.get(1));
		Assertions.assertEquals(stack(Items.GOLD_INGOT, 64), request.sparseInputs().get(1));
		Assertions.assertEquals(stack(Items.GOLD_INGOT, 64), request.catalysts().getFirst().stack());
	}

	@Test
	public void craftingGuideItemsMapSingleRowRecipeFromJeiLayoutOrder() {
		List<@Nullable GenericStack> guides = emptyGuides();
		guides.set(3, stack(Items.GLOWSTONE_DUST, 1));
		guides.set(4, stack(Items.GLASS, 1));
		guides.set(5, stack(Items.GLOWSTONE_DUST, 1));

		ItemStack[] items = JeiRecipeChainPatternEncodingService.createCraftingGuideItems(
			craftingRequest(guides),
			createSingleRowRecipe()
		);

		Assertions.assertNotNull(items);
		Assertions.assertEquals(Items.GLOWSTONE_DUST, items[0].getItem());
		Assertions.assertEquals(Items.GLASS, items[1].getItem());
		Assertions.assertEquals(Items.GLOWSTONE_DUST, items[2].getItem());
		for (int i = 3; i < items.length; i++) {
			Assertions.assertTrue(items[i].isEmpty());
		}
	}

	@Test
	public void craftingGuideItemsRejectTopLeftGuidesForSingleRowRecipe() {
		List<@Nullable GenericStack> guides = emptyGuides();
		guides.set(0, stack(Items.GLOWSTONE_DUST, 1));
		guides.set(1, stack(Items.GLASS, 1));
		guides.set(2, stack(Items.GLOWSTONE_DUST, 1));

		ItemStack[] items = JeiRecipeChainPatternEncodingService.createCraftingGuideItems(
			craftingRequest(guides),
			createSingleRowRecipe()
		);

		Assertions.assertNull(items);
	}

	@Test
	public void craftingGuideItemsMapShapelessThreeFromJeiLayoutOrder() {
		NonNullList<Ingredient> ingredients = NonNullList.withSize(3, Ingredient.EMPTY);
		ingredients.set(0, Ingredient.of(Items.GLOWSTONE_DUST));
		ingredients.set(1, Ingredient.of(Items.GLASS));
		ingredients.set(2, Ingredient.of(Items.REDSTONE));
		ShapelessRecipe recipe = new ShapelessRecipe(
			"",
			CraftingBookCategory.MISC,
			new ItemStack(Items.GLOWSTONE),
			ingredients
		);
		List<@Nullable GenericStack> guides = emptyGuides();
		guides.set(0, stack(Items.GLOWSTONE_DUST, 1));
		guides.set(1, stack(Items.GLASS, 1));
		guides.set(3, stack(Items.REDSTONE, 1));

		ItemStack[] items = JeiRecipeChainPatternEncodingService.createCraftingGuideItems(
			craftingRequest(guides),
			new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("test", "shapeless_three"), recipe)
		);

		Assertions.assertNotNull(items);
		Assertions.assertEquals(Items.GLOWSTONE_DUST, items[0].getItem());
		Assertions.assertEquals(Items.GLASS, items[1].getItem());
		Assertions.assertEquals(Items.REDSTONE, items[2].getItem());
		for (int i = 3; i < items.length; i++) {
			Assertions.assertTrue(items[i].isEmpty());
		}
	}

	private static RecipeHolder<CraftingRecipe> createSingleRowRecipe() {
		NonNullList<Ingredient> ingredients = NonNullList.withSize(3, Ingredient.EMPTY);
		ingredients.set(0, Ingredient.of(Items.GLOWSTONE_DUST));
		ingredients.set(1, Ingredient.of(Items.GLASS));
		ingredients.set(2, Ingredient.of(Items.GLOWSTONE_DUST));
		ShapedRecipe recipe = new ShapedRecipe(
			"",
			CraftingBookCategory.MISC,
			new ShapedRecipePattern(3, 1, ingredients, Optional.empty()),
			new ItemStack(Items.GLOWSTONE),
			false
		);
		return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("test", "vibrant_glass"), recipe);
	}

	private static List<@Nullable GenericStack> emptyGuides() {
		return new ArrayList<>(Collections.nCopies(9, (GenericStack) null));
	}

	private static JeiPatternEncodeRequestWire craftingRequest(
		List<@Nullable GenericStack> guides
	) {
		return new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.CRAFTING,
			List.of(),
			List.of(),
			List.of(),
			guides,
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			false,
			false
		);
	}

	private static JeiPatternEncodeRequestWire processingRequest(
		List<GenericStack> inputs,
		List<JeiPatternCatalystWire> catalysts
	) {
		return processingRequest(inputs, catalysts, List.of(stack(Items.DIAMOND, 1)));
	}

	private static JeiPatternEncodeRequestWire processingRequest(
		List<GenericStack> inputs,
		List<JeiPatternCatalystWire> catalysts,
		List<GenericStack> outputs
	) {
		return new JeiPatternEncodeRequestWire(
			ResourceLocation.fromNamespaceAndPath("test", "category"),
			ResourceLocation.fromNamespaceAndPath("test", "recipe"),
			JeiPatternEncodeMode.PROCESSING,
			List.copyOf(inputs),
			List.copyOf(outputs),
			List.copyOf(catalysts),
			List.of(),
			null,
			false,
			false
		);
	}

	private static GenericStack stack(ItemLike item, long amount) {
		return new GenericStack(AEItemKey.of(new ItemStack(item)), amount);
	}

	private static final class FakeMenu extends AbstractContainerMenu {
		private FakeMenu() {
			super(new MenuType<>((id, inventory) -> null, FeatureFlags.DEFAULT_FLAGS), 0);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
