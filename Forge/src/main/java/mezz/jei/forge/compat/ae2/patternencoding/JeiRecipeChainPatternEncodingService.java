package mezz.jei.forge.compat.ae2.patternencoding;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.CraftingRecipeUtil;
import appeng.util.inv.PlayerInternalInventory;
import mezz.jei.gui.compat.ae2.JeiPatternEncodeMode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JeiRecipeChainPatternEncodingService {
	private JeiRecipeChainPatternEncodingService() {
	}

	public static JeiBatchPatternEncodeResult encodeRecipeChainPatterns(
		ServerPlayer player,
		AbstractContainerMenu menu,
		List<JeiPatternEncodeRequestWire> requests
	) {
		if (!(menu instanceof PatternEncodingTermMenu patternMenu)) {
			return stoppedBeforeProcessing(requests, JeiPatternEncodeStopReason.NOT_PATTERN_ENCODING_TERMINAL);
		}

		int encodedCount = 0;
		int skippedExistingCount = 0;
		int skippedInvalidCount = 0;
		JeiPatternEncodeStopReason stopReason = JeiPatternEncodeStopReason.NONE;
		List<JeiPatternEncodeEntryResult> entries = new ArrayList<>(requests.size());
		List<PreparedEntry> preparedEntries = prepareEntries(player, menu, requests);

		for (PreparedEntry preparedEntry : preparedEntries) {
			if (preparedEntry.status == JeiPatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT) {
				skippedExistingCount++;
			} else if (preparedEntry.status == JeiPatternEncodeEntryStatus.SKIPPED_INVALID_CATALYST
				|| preparedEntry.status == JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE
				|| preparedEntry.status == JeiPatternEncodeEntryStatus.SKIPPED_INVALID_PATTERN) {
				skippedInvalidCount++;
			}
		}

		int writableCount = countWritable(preparedEntries);
		if (!hasBlankPatterns(patternMenu, player, writableCount)) {
			entries.addAll(entriesForInsufficientBlankPatterns(preparedEntries));
			return new JeiBatchPatternEncodeResult(
				0,
				skippedExistingCount,
				skippedInvalidCount,
				countNotProcessed(entries),
				JeiPatternEncodeStopReason.NO_BLANK_PATTERN,
				entries
			);
		}

		for (int i = 0; i < preparedEntries.size(); i++) {
			PreparedEntry preparedEntry = preparedEntries.get(i);
			if (preparedEntry.status != JeiPatternEncodeEntryStatus.ENCODED) {
				entries.add(preparedEntry.toResult());
				continue;
			}

			ItemStack encodedPattern = preparedEntry.encodedPattern;
			PlayerInternalInventory playerInv = new PlayerInternalInventory(player.getInventory());
			if (!canInsertIntoPlayerInventory(playerInv, encodedPattern)) {
				stopReason = JeiPatternEncodeStopReason.NO_INVENTORY_SPACE;
				addNotProcessedEntries(entries, requests, i);
				break;
			}
			if (!insertIntoPlayerInventory(playerInv, encodedPattern)) {
				stopReason = JeiPatternEncodeStopReason.NO_INVENTORY_SPACE;
				addNotProcessedEntries(entries, requests, i);
				break;
			}
			if (!consumeBlankPatterns(patternMenu, player, 1)) {
				stopReason = JeiPatternEncodeStopReason.NO_BLANK_PATTERN;
				addNotProcessedEntries(entries, requests, i + 1);
				break;
			}

			encodedCount++;
			entries.add(entry(preparedEntry.request, JeiPatternEncodeEntryStatus.ENCODED, null));
		}

		return new JeiBatchPatternEncodeResult(
			encodedCount,
			skippedExistingCount,
			skippedInvalidCount,
			countNotProcessed(entries),
			stopReason,
			entries
		);
	}

	private static List<PreparedEntry> prepareEntries(ServerPlayer player, AbstractContainerMenu menu, List<JeiPatternEncodeRequestWire> requests) {
		List<PreparedEntry> preparedEntries = new ArrayList<>(requests.size());
		Set<AEKey> plannedPrimaryOutputs = new HashSet<>();

		for (JeiPatternEncodeRequestWire request : requests) {
			String catalystValidationFailure = validateCatalysts(request);
			if (catalystValidationFailure != null) {
				preparedEntries.add(PreparedEntry.skipped(request, JeiPatternEncodeEntryStatus.SKIPPED_INVALID_CATALYST, catalystValidationFailure));
				continue;
			}

			ItemStack encodedPattern = createEncodedPattern(player.level(), menu, request);
			if (encodedPattern == null) {
				preparedEntries.add(PreparedEntry.skipped(request, JeiPatternEncodeEntryStatus.SKIPPED_INVALID_RECIPE, "Unable to encode recipe"));
				continue;
			}

			IPatternDetails details = PatternDetailsHelper.decodePattern(encodedPattern, player.level());
			if (details == null || details.getOutputs().length == 0) {
				preparedEntries.add(PreparedEntry.skipped(request, JeiPatternEncodeEntryStatus.SKIPPED_INVALID_PATTERN, "Encoded pattern is invalid"));
				continue;
			}

			GenericStack primaryOutput = details.getPrimaryOutput();
			if (hasPatternWithPrimaryOutput(player, primaryOutput) || !plannedPrimaryOutputs.add(primaryOutput.what())) {
				preparedEntries.add(PreparedEntry.skipped(request, JeiPatternEncodeEntryStatus.SKIPPED_EXISTING_PRIMARY_OUTPUT, "Pattern with same primary output already exists"));
				continue;
			}

			preparedEntries.add(PreparedEntry.writable(request, encodedPattern));
		}

		return preparedEntries;
	}

	private static int countWritable(List<PreparedEntry> preparedEntries) {
		int count = 0;
		for (PreparedEntry preparedEntry : preparedEntries) {
			if (preparedEntry.status == JeiPatternEncodeEntryStatus.ENCODED) {
				count++;
			}
		}
		return count;
	}

	private static List<JeiPatternEncodeEntryResult> entriesForInsufficientBlankPatterns(List<PreparedEntry> preparedEntries) {
		List<JeiPatternEncodeEntryResult> entries = new ArrayList<>(preparedEntries.size());
		for (PreparedEntry preparedEntry : preparedEntries) {
			if (preparedEntry.status == JeiPatternEncodeEntryStatus.ENCODED) {
				entries.add(entry(preparedEntry.request, JeiPatternEncodeEntryStatus.NOT_PROCESSED, null));
			} else {
				entries.add(preparedEntry.toResult());
			}
		}
		return entries;
	}

	@Nullable
	static ItemStack createEncodedPattern(Level level, AbstractContainerMenu menu, JeiPatternEncodeRequestWire request) {
		try {
			if (request.mode() != JeiPatternEncodeMode.PROCESSING && !request.catalysts().isEmpty()) {
				return null;
			}
			return switch (request.mode()) {
				case PROCESSING -> request.canonicalInputGuides().isEmpty() ? createProcessingPattern(request) : null;
				case CRAFTING -> createCraftingPattern(level, menu, request);
				case STONECUTTING -> createStonecuttingPattern(level, request);
				case SMITHING_TABLE -> createSmithingTablePattern(level, request);
			};
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Nullable
	static ItemStack createProcessingPattern(JeiPatternEncodeRequestWire request) {
		List<@Nullable GenericStack> inputs = request.sparseInputs();
		List<@Nullable GenericStack> outputs = request.sparseOutputs();
		if (validateCatalysts(request) != null) {
			return null;
		}
		if (inputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS || outputs.size() > AEProcessingPattern.MAX_OUTPUT_SLOTS) {
			return null;
		}
		if (outputs.isEmpty() || !isValidStack(outputs.get(0))) {
			return null;
		}

		boolean hasInput = false;
		for (GenericStack input : inputs) {
			if (input != null) {
				if (!isValidStack(input)) {
					return null;
				}
				hasInput = true;
			}
		}
		if (!hasInput || !hasRealProcessingInput(inputs, catalystSlots(request))) {
			return null;
		}
		for (GenericStack output : outputs) {
			if (output != null && !isValidStack(output)) {
				return null;
			}
		}

		try {
			ForkPatternEncodingAccess fork = ForkPatternEncodingAccess.get();
			if (fork != null) {
				return fork.encodeProcessingPattern(inputs, outputs, request.catalysts());
			}
			return PatternDetailsHelper.encodeProcessingPattern(
				removeCatalystInputs(inputs, request.catalysts()).toArray(GenericStack[]::new),
				outputs.toArray(GenericStack[]::new)
			);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static List<@Nullable GenericStack> removeCatalystInputs(List<@Nullable GenericStack> inputs, List<JeiPatternCatalystWire> catalysts) {
		List<@Nullable GenericStack> realInputs = new ArrayList<>(inputs);
		for (JeiPatternCatalystWire catalyst : catalysts) {
			realInputs.set(catalyst.sourceSlot(), null);
		}
		return Collections.unmodifiableList(realInputs);
	}

	private static Set<Integer> catalystSlots(JeiPatternEncodeRequestWire request) {
		Set<Integer> slots = new HashSet<>();
		for (JeiPatternCatalystWire catalyst : request.catalysts()) {
			slots.add(catalyst.sourceSlot());
		}
		return slots;
	}

	@Nullable
	static String validateCatalysts(JeiPatternEncodeRequestWire request) {
		if (request.mode() != JeiPatternEncodeMode.PROCESSING) {
			return request.catalysts().isEmpty() ? null : "Catalysts are only supported for processing patterns";
		}
		if (request.catalysts().isEmpty()) {
			return null;
		}

		Set<Integer> slots = new HashSet<>();
		List<@Nullable GenericStack> inputs = request.sparseInputs();
		for (JeiPatternCatalystWire catalyst : request.catalysts()) {
			int slot = catalyst.sourceSlot();
			if (slot < 0 || slot >= inputs.size()) {
				return "Catalyst source slot is out of range";
			}
			if (!slots.add(slot)) {
				return "Catalyst source slots must be unique";
			}
			GenericStack input = inputs.get(slot);
			if (input == null || !catalyst.stack().equals(input)) {
				return "Catalyst must match its processing input";
			}
		}
		return hasRealProcessingInput(inputs, slots) ? null : "Processing pattern has no real input";
	}

	private static boolean hasRealProcessingInput(List<@Nullable GenericStack> inputs, Set<Integer> catalystSlots) {
		for (int slot = 0; slot < inputs.size(); slot++) {
			GenericStack input = inputs.get(slot);
			if (input != null && !catalystSlots.contains(slot)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	static ItemStack createCraftingPattern(Level level, AbstractContainerMenu menu, JeiPatternEncodeRequestWire request) {
		CraftingRecipe recipe = getCanonicalRecipe(level, request, RecipeType.CRAFTING, CraftingRecipe.class);
		if (recipe == null) {
			return null;
		}
		ItemStack[] ingredients = createCraftingGuideItems(request, recipe);
		if (ingredients == null) {
			return null;
		}
		TransientCraftingContainer craftingInput = new TransientCraftingContainer(menu, 3, 3);
		for (int i = 0; i < ingredients.length; i++) {
			craftingInput.setItem(i, ingredients[i]);
		}
		if (!recipe.matches(craftingInput, level)) {
			return null;
		}
		ItemStack output = recipe.assemble(craftingInput, level.registryAccess());
		if (output.isEmpty()) {
			return null;
		}
		return PatternDetailsHelper.encodeCraftingPattern(
			recipe,
			ingredients,
			output,
			request.allowSubstitution(),
			request.allowFluidSubstitution()
		);
	}

	@Nullable
	static ItemStack[] createCraftingGuideItems(JeiPatternEncodeRequestWire request, CraftingRecipe recipe) {
		List<Ingredient> ingredients = recipe.getIngredients();
		boolean shaped = recipe instanceof ShapedRecipe;
		int width;
		int height;
		if (shaped) {
			ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
			width = shapedRecipe.getWidth();
			height = shapedRecipe.getHeight();
		} else {
			width = height = getShapelessSize(ingredients.size());
		}
		List<@Nullable GenericStack> guides = request.canonicalInputGuides();
		if (guides.size() != 9) {
			return null;
		}

		ItemStack[] items = new ItemStack[9];
		boolean[] usedGuideSlots = new boolean[9];
		for (int matrixIndex = 0; matrixIndex < 9; matrixIndex++) {
			int ingredientIndex;
			if (shaped) {
				int x = matrixIndex % 3;
				int y = matrixIndex / 3;
				ingredientIndex = x < width && y < height ? y * width + x : -1;
			} else {
				ingredientIndex = matrixIndex < ingredients.size() ? matrixIndex : -1;
			}
			if (ingredientIndex < 0) {
				items[matrixIndex] = ItemStack.EMPTY;
				continue;
			}

			Ingredient ingredient = ingredients.get(ingredientIndex);
			int guideIndex = getCraftingIndex(ingredientIndex, width, height);
			usedGuideSlots[guideIndex] = true;
			GenericStack guide = guides.get(guideIndex);
			if (ingredient.isEmpty()) {
				if (guide != null) {
					return null;
				}
				items[matrixIndex] = ItemStack.EMPTY;
				continue;
			}
			if (guide == null || guide.amount() != 1 || !(guide.what() instanceof AEItemKey itemKey)) {
				return null;
			}
			ItemStack item = itemKey.toStack();
			if (!ingredient.test(item)) {
				return null;
			}
			items[matrixIndex] = item;
		}
		for (int slot = 0; slot < guides.size(); slot++) {
			if (guides.get(slot) != null && !usedGuideSlots[slot]) {
				return null;
			}
		}
		return items;
	}

	private static int getShapelessSize(int total) {
		if (total > 4) {
			return 3;
		} else if (total > 1) {
			return 2;
		} else {
			return 1;
		}
	}

	private static int getCraftingIndex(int i, int width, int height) {
		if (width == 1) {
			if (height == 3) {
				return i * 3 + 1;
			} else if (height == 2) {
				return i * 3 + 1;
			} else {
				return 4;
			}
		} else if (height == 1) {
			return i + 3;
		} else if (width == 2) {
			int index = i;
			if (i > 1) {
				index++;
				if (i > 3) {
					index++;
				}
			}
			return index;
		} else if (height == 2) {
			return i + 3;
		} else {
			return i;
		}
	}

	@Nullable
	static ItemStack createStonecuttingPattern(Level level, JeiPatternEncodeRequestWire request) {
		StonecutterRecipe recipe = getCanonicalRecipe(level, request, RecipeType.STONECUTTING, StonecutterRecipe.class);
		if (recipe == null) {
			return null;
		}
		List<Ingredient> ingredients = recipe.getIngredients();
		if (ingredients.size() != 1) {
			return null;
		}
		ItemStack[] guidedInputs = createCanonicalGuideItems(request, ingredients);
		if (guidedInputs == null) {
			return null;
		}
		ItemStack input = guidedInputs[0];
		SimpleContainer recipeInput = new SimpleContainer(1);
		recipeInput.setItem(0, input);
		if (!recipe.matches(recipeInput, level)) {
			return null;
		}
		ItemStack output = recipe.getResultItem(level.registryAccess());
		if (output.isEmpty()) {
			return null;
		}
		return PatternDetailsHelper.encodeStonecuttingPattern(
			recipe,
			AEItemKey.of(input),
			AEItemKey.of(output),
			request.allowSubstitution()
		);
	}

	@Nullable
	static ItemStack createSmithingTablePattern(Level level, JeiPatternEncodeRequestWire request) {
		SmithingRecipe recipe = getCanonicalRecipe(level, request, RecipeType.SMITHING, SmithingRecipe.class);
		if (recipe == null) {
			return null;
		}
		List<Ingredient> expectedIngredients = CraftingRecipeUtil.getIngredients(recipe);
		if (expectedIngredients.size() != 3) {
			return null;
		}
		ItemStack[] guidedInputs = createCanonicalGuideItems(request, expectedIngredients);
		if (guidedInputs == null) {
			return null;
		}
		ItemStack template = guidedInputs[0];
		ItemStack base = guidedInputs[1];
		ItemStack addition = guidedInputs[2];
		SimpleContainer recipeInput = new SimpleContainer(3);
		recipeInput.setItem(0, template);
		recipeInput.setItem(1, base);
		recipeInput.setItem(2, addition);
		if (!recipe.matches(recipeInput, level)) {
			return null;
		}
		ItemStack output = recipe.assemble(recipeInput, level.registryAccess());
		if (output.isEmpty()) {
			return null;
		}
		return PatternDetailsHelper.encodeSmithingTablePattern(
			recipe,
			AEItemKey.of(template),
			AEItemKey.of(base),
			AEItemKey.of(addition),
			AEItemKey.of(output),
			request.allowSubstitution()
		);
	}

	@Nullable
	private static <T extends Recipe<?>> T getCanonicalRecipe(
		Level level,
		JeiPatternEncodeRequestWire request,
		RecipeType<T> recipeType,
		Class<T> recipeClass
	) {
		if (request.canonicalRecipeId() == null) {
			return null;
		}
		Recipe<?> recipe = level.getRecipeManager().byKey(request.canonicalRecipeId()).orElse(null);
		if (recipe == null || recipe.getType() != recipeType || !recipeClass.isInstance(recipe)) {
			return null;
		}
		return recipeClass.cast(recipe);
	}

	@Nullable
	private static ItemStack[] createCanonicalGuideItems(JeiPatternEncodeRequestWire request, List<Ingredient> ingredients) {
		List<@Nullable GenericStack> guides = request.canonicalInputGuides();
		if (guides.size() != ingredients.size()) {
			return null;
		}
		ItemStack[] items = new ItemStack[ingredients.size()];
		for (int index = 0; index < ingredients.size(); index++) {
			Ingredient ingredient = ingredients.get(index);
			GenericStack guide = guides.get(index);
			if (ingredient.isEmpty()) {
				if (guide != null) {
					return null;
				}
				items[index] = ItemStack.EMPTY;
				continue;
			}
			if (guide == null || guide.amount() != 1 || !(guide.what() instanceof AEItemKey itemKey)) {
				return null;
			}
			ItemStack item = itemKey.toStack();
			if (!ingredient.test(item)) {
				return null;
			}
			items[index] = item;
		}
		return items;
	}

	private static boolean hasPatternWithPrimaryOutput(ServerPlayer player, GenericStack primaryOutput) {
		for (ItemStack stack : player.getInventory().items) {
			IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
			if (details == null || details.getOutputs().length == 0) {
				continue;
			}
			if (details.getPrimaryOutput().what().equals(primaryOutput.what())) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBlankPatterns(PatternEncodingTermMenu menu, ServerPlayer player, int amount) {
		if (amount <= 0 || player.getAbilities().instabuild) {
			return true;
		}
		ForkPatternEncodingAccess fork = ForkPatternEncodingAccess.get();
		if (fork != null) {
			return fork.hasBlankPatterns(menu, amount);
		}
		InternalInventory blanks = getBlankPatternInventory(menu);
		return blanks != null && countBlankPatterns(blanks) >= amount;
	}

	private static boolean consumeBlankPatterns(PatternEncodingTermMenu menu, ServerPlayer player, int amount) {
		if (amount <= 0 || player.getAbilities().instabuild) {
			return true;
		}
		ForkPatternEncodingAccess fork = ForkPatternEncodingAccess.get();
		if (fork != null) {
			return fork.consumeBlankPatterns(menu, amount);
		}
		InternalInventory blanks = getBlankPatternInventory(menu);
		return blanks != null && consumeBlankPatterns(blanks, amount);
	}

	@Nullable
	private static InternalInventory getBlankPatternInventory(PatternEncodingTermMenu menu) {
		if (menu.getHost() instanceof IPatternTerminalMenuHost host) {
			return host.getLogic().getBlankPatternInv();
		}
		return null;
	}

	private static int countBlankPatterns(InternalInventory blanks) {
		int count = 0;
		for (int i = 0; i < blanks.size(); i++) {
			ItemStack stack = blanks.getStackInSlot(i);
			if (!stack.isEmpty() && AEItems.BLANK_PATTERN.isSameAs(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static boolean consumeBlankPatterns(InternalInventory blanks, int amount) {
		for (int i = 0; i < blanks.size() && amount > 0; i++) {
			ItemStack stack = blanks.getStackInSlot(i);
			if (stack.isEmpty() || !AEItems.BLANK_PATTERN.isSameAs(stack)) {
				continue;
			}
			int taken = Math.min(amount, stack.getCount());
			stack.shrink(taken);
			amount -= taken;
			blanks.setItemDirect(i, stack);
		}
		return amount <= 0;
	}

	private static boolean canInsertIntoPlayerInventory(PlayerInternalInventory inventory, ItemStack encodedPattern) {
		return inventory.simulateAdd(encodedPattern).isEmpty();
	}

	private static boolean insertIntoPlayerInventory(PlayerInternalInventory inventory, ItemStack encodedPattern) {
		return inventory.addItems(encodedPattern).isEmpty();
	}

	private static boolean isValidStack(@Nullable GenericStack stack) {
		return stack != null && stack.amount() > 0;
	}

	private static JeiPatternEncodeEntryResult entry(
		JeiPatternEncodeRequestWire request,
		JeiPatternEncodeEntryStatus status,
		@Nullable String message
	) {
		return new JeiPatternEncodeEntryResult(
			request.recipeUid(),
			status,
			message != null ? Component.literal(message) : null
		);
	}

	private static void addNotProcessedEntries(
		List<JeiPatternEncodeEntryResult> entries,
		List<JeiPatternEncodeRequestWire> requests,
		int firstNotProcessed
	) {
		for (int index = firstNotProcessed; index < requests.size(); index++) {
			entries.add(entry(requests.get(index), JeiPatternEncodeEntryStatus.NOT_PROCESSED, null));
		}
	}

	private static int countNotProcessed(List<JeiPatternEncodeEntryResult> entries) {
		int count = 0;
		for (JeiPatternEncodeEntryResult entry : entries) {
			if (entry.status() == JeiPatternEncodeEntryStatus.NOT_PROCESSED) {
				count++;
			}
		}
		return count;
	}

	private static JeiBatchPatternEncodeResult stoppedBeforeProcessing(
		List<JeiPatternEncodeRequestWire> requests,
		JeiPatternEncodeStopReason stopReason
	) {
		List<JeiPatternEncodeEntryResult> entries = new ArrayList<>(requests.size());
		addNotProcessedEntries(entries, requests, 0);
		return new JeiBatchPatternEncodeResult(0, 0, 0, requests.size(), stopReason, entries);
	}

	private record PreparedEntry(
		JeiPatternEncodeRequestWire request,
		JeiPatternEncodeEntryStatus status,
		@Nullable ItemStack encodedPattern,
		@Nullable String message
	) {
		static PreparedEntry writable(JeiPatternEncodeRequestWire request, ItemStack encodedPattern) {
			return new PreparedEntry(request, JeiPatternEncodeEntryStatus.ENCODED, encodedPattern, null);
		}

		static PreparedEntry skipped(JeiPatternEncodeRequestWire request, JeiPatternEncodeEntryStatus status, @Nullable String message) {
			return new PreparedEntry(request, status, null, message);
		}

		JeiPatternEncodeEntryResult toResult() {
			return entry(request, status, message);
		}
	}
}
