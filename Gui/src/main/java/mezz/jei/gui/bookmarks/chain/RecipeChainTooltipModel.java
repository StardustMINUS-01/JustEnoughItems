package mezz.jei.gui.bookmarks.chain;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.SaturatedMath;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Ported from 1.21.1 fork: the create() overload that takes IIngredientManager
// now actually uses it (the 1.20.1 fork's 7-param overload was a thin
// delegate that dropped the parameter). Adds collectOrdinaryTargets /
// allocateOrdinaryTargets so groups that contain a mix of BookmarkItemType.ITEM
// (regular bookmarks) and recipe-graph bookmarks still resolve the chain
// correctly. The 4- and 6-param overloads are kept for source compatibility
// with 1.20.1 fork callers.
public record RecipeChainTooltipModel(
	List<Section> sections
) {
	public RecipeChainTooltipModel {
		sections = List.copyOf(sections);
	}

	public static RecipeChainTooltipModel create(
		List<RecipeChainInput> recipeInputs,
		Optional<RecipeChainDetails> baseDetails,
		Set<ResourceLocation> collapsedRecipes,
		List<RecipeChainInput> inventoryInputs,
		boolean shiftDown,
		boolean controlDown,
		IIngredientManager ingredientManager
	) {
		// Ported from 1.21.1 fork: 1.20.1 fork previously dropped ingredientManager on
		// the floor here, so collectOrdinaryTargets / allocateOrdinaryTargets were
		// never reachable. Without them, groups that contain a mix of ITEM-type and
		// recipe-graph bookmarks (e.g. a plain "need N cobblestone" next to a
		// shaped-recipe result) miscounted ordinary targets during refresh, which
		// visually displaced the chain by one slot per refresh and put the
		// per-slot amount text out of step with the icon after the second adjust.
		RecipeChainDetails baseRecipeDetails = baseDetails.orElseGet(() -> RecipeChainMath.refresh(recipeInputs, collapsedRecipes));
		Map<BookmarkIngredientKey, Item> targets = collectOrdinaryTargets(recipeInputs, ingredientManager);
		boolean hasOrdinaryTargets = recipeInputs.stream()
			.anyMatch(input -> input.metadata().type() == BookmarkItemType.ITEM);
		RecipeChainDetails recipeDetails = hasOrdinaryTargets ? RecipeChainMath.refresh(recipeInputs.stream()
			.filter(input -> input.metadata().type() != BookmarkItemType.ITEM)
			.toList(), collapsedRecipes) : baseRecipeDetails;
		if (!shiftDown) {
			Map<BookmarkIngredientKey, Item> inputs = collectMissing(recipeInputs, recipeDetails);
			targets.values().forEach(target -> merge(inputs, target));
			List<Section> sections = new ArrayList<>();
			addSection(sections, RecipeChainTooltipSectionType.OUTPUT, collectOutputs(recipeInputs, recipeDetails));
			addSection(sections, RecipeChainTooltipSectionType.INPUT, sorted(inputs));
			return new RecipeChainTooltipModel(sections);
		}
		List<RecipeChainInput> adjustedRecipeInputs = !controlDown ? expandOutputRecipeMultipliers(recipeInputs, recipeDetails, inventoryInputs) : recipeInputs;
		List<RecipeChainInput> calculationInputs = new ArrayList<>(adjustedRecipeInputs.size() + inventoryInputs.size());
		for (RecipeChainInput input : adjustedRecipeInputs) {
			if (input.metadata().type() != BookmarkItemType.ITEM) {
				calculationInputs.add(input);
			}
		}
		calculationInputs.addAll(inventoryInputs);

		RecipeChainDetails details = RecipeChainMath.refresh(calculationInputs, collapsedRecipes);
		Map<BookmarkIngredientKey, Long> usedAmounts = hasOrdinaryTargets ? new LinkedHashMap<>() : Map.of();
		Map<BookmarkIngredientKey, Item> available = collectAvailable(calculationInputs, details, usedAmounts, hasOrdinaryTargets);
		Map<BookmarkIngredientKey, Item> missing = collectMissing(calculationInputs, details);
		allocateOrdinaryTargets(targets, inventoryInputs, usedAmounts, missing, available);
		List<Section> sections = new ArrayList<>();
		addSection(sections, RecipeChainTooltipSectionType.OUTPUT, collectOutputs(adjustedRecipeInputs, details));
		addSection(sections, RecipeChainTooltipSectionType.MISSING, sorted(missing));
		addSection(sections, RecipeChainTooltipSectionType.NEEDED, sorted(collectNeeded(calculationInputs, details)));
		addSection(sections, RecipeChainTooltipSectionType.AVAILABLE, sorted(available));
		addSection(sections, RecipeChainTooltipSectionType.REMAINDER, sorted(collectRemainders(calculationInputs, details)));
		return new RecipeChainTooltipModel(sections);
	}

	// 1.20.1 fork source-compat overload — no ingredientManager, no ordinary-target
	// handling. Kept so callers that still pass without an IIngredientManager
	// (older bookmarks panel, tests) still compile. New code should call the
	// 7-param overload above.
	public static RecipeChainTooltipModel create(
		List<RecipeChainInput> recipeInputs,
		Set<ResourceLocation> collapsedRecipes,
		List<RecipeChainInput> inventoryInputs,
		boolean shiftDown,
		boolean controlDown
	) {
		RecipeChainDetails baseDetails = RecipeChainMath.refresh(recipeInputs, collapsedRecipes);
		return create(recipeInputs, Optional.of(baseDetails), collapsedRecipes, inventoryInputs, shiftDown, controlDown, null);
	}

	// 1.20.1 fork source-compat overload — no shiftDown/controlDown path.
	public static RecipeChainTooltipModel create(
		List<RecipeChainInput> recipeInputs,
		RecipeChainDetails baseDetails,
		Set<ResourceLocation> collapsedRecipes,
		List<RecipeChainInput> inventoryInputs,
		boolean shiftDown,
		boolean controlDown
	) {
		return create(recipeInputs, Optional.ofNullable(baseDetails), collapsedRecipes, inventoryInputs, shiftDown, controlDown, null);
	}

	public static RecipeChainTooltipModel create(List<RecipeChainInput> recipeInputs, RecipeChainDetails details) {
		List<Section> sections = new ArrayList<>();
		addSection(sections, RecipeChainTooltipSectionType.OUTPUT, collectOutputs(recipeInputs, details));
		addSection(sections, RecipeChainTooltipSectionType.INPUT, sorted(collectMissing(recipeInputs, details)));
		return new RecipeChainTooltipModel(sections);
	}

	private static List<RecipeChainInput> expandOutputRecipeMultipliers(
		List<RecipeChainInput> recipeInputs,
		RecipeChainDetails originalDetails,
		List<RecipeChainInput> inventoryInputs
	) {
		if (inventoryInputs.isEmpty()) {
			return recipeInputs;
		}
		if (originalDetails.outputRecipes().isEmpty()) {
			return recipeInputs;
		}

		List<RecipeChainInput> adjusted = new ArrayList<>(recipeInputs.size());
		boolean changed = false;
		for (RecipeChainInput input : recipeInputs) {
			BookmarkItemMetadata metadata = input.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (
				!metadata.type().isGraphOutput() ||
					recipeUid == null ||
					metadata.emptyFactor() ||
					!originalDetails.outputRecipes().contains(recipeUid)
			) {
				adjusted.add(input);
				continue;
			}

			long inventoryAmount = getMatchingInventoryAmount(metadata, inventoryInputs);
			long currentMultiplier = Math.max(0, metadata.multiplier());
			long currentOutputAmount = metadata.amount(currentMultiplier);
			long expandedMultiplier = currentMultiplier;
			if (inventoryAmount >= metadata.factor() && currentOutputAmount > 0) {
				long targetAmount = inventoryAmount + currentOutputAmount - inventoryAmount % currentOutputAmount;
				expandedMultiplier = Math.max(currentMultiplier, metadata.multiplierFromAmount(targetAmount));
			}
			if (expandedMultiplier > currentMultiplier) {
				adjusted.add(new RecipeChainInput(input.index(), metadata.withMultiplier(expandedMultiplier), input.selectedKey(), input.selectedIngredient()));
				changed = true;
			} else {
				adjusted.add(input);
			}
		}
		return changed ? List.copyOf(adjusted) : recipeInputs;
	}

	private static long getMatchingInventoryAmount(BookmarkItemMetadata metadata, List<RecipeChainInput> inventoryInputs) {
		long amount = 0;
		for (RecipeChainInput inventoryInput : inventoryInputs) {
			BookmarkItemMetadata inventoryMetadata = inventoryInput.metadata();
			if (metadata.isSatisfiedBy(inventoryMetadata)) {
				amount = SaturatedMath.add(amount, inventoryMetadata.amount());
			}
		}
		return amount;
	}

	private static List<Item> collectOutputs(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		Map<BookmarkIngredientKey, Item> items = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			RecipeChainItem item = details.calculatedItems().get(input.index());
			if (item != null && item.type() == RecipeChainItemType.RESULT && item.providedAmount() > 0) {
				toItem(input, item.providedAmount()).ifPresent(output -> merge(items, output));
			}
		}
		return sorted(items);
	}

	private static Map<BookmarkIngredientKey, Item> collectMissing(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		Map<BookmarkIngredientKey, Item> items = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			RecipeChainItem item = details.calculatedItems().get(input.index());
			if (item != null && item.type() == RecipeChainItemType.INGREDIENT && item.requiredAmount() > 0 && !details.initialItems().contains(input.index())) {
				toItem(input, item.requiredAmount()).ifPresent(missing -> merge(items, missing));
			}
		}
		return items;
	}

	// Ported from 1.21.1 fork: tracks "ordinary" ITEM-type bookmarks alongside the
	// recipe graph so the chain calculation can subtract them from the demand.
	// 1.20.1 fork had no equivalent — ITEM-type bookmarks were silently dropped
	// from the calculation, which led to chain items being shown out of position
	// (the demand math treated them as if they didn't exist).
	private static Map<BookmarkIngredientKey, Item> collectOrdinaryTargets(
		List<RecipeChainInput> groupInputs,
		IIngredientManager ingredientManager
	) {
		Map<BookmarkIngredientKey, Item> targets = new LinkedHashMap<>();
		for (RecipeChainInput input : groupInputs) {
			BookmarkItemMetadata metadata = input.metadata();
			if (metadata.type() == BookmarkItemType.ITEM) {
				ITypedIngredient<?> ingredient = input.selectedIngredient();
				long amount = metadata.amount();
				// Ordinary bookmarks can retain their stack amount without explicit quantity metadata.
				// Metadata-only chain snapshots have no selected ingredient to read in that case.
				if (metadata.multiplier() == 1 && metadata.factor() == 1 && metadata.chance() == BookmarkItemMetadata.CHANCE_FULL && ingredient != null && ingredientManager != null) {
					amount = BookmarkIngredientAmountResolver.getAmount(ingredient, ingredientManager);
				}
				if (amount > 0) {
					toItem(input, amount).ifPresent(target -> merge(targets, target));
				}
			}
		}
		return targets;
	}

	private static void allocateOrdinaryTargets(
		Map<BookmarkIngredientKey, Item> targets,
		List<RecipeChainInput> inventoryInputs,
		Map<BookmarkIngredientKey, Long> usedAmounts,
		Map<BookmarkIngredientKey, Item> missing,
		Map<BookmarkIngredientKey, Item> available
	) {
		if (targets.isEmpty()) {
			return;
		}
		Map<BookmarkIngredientKey, Long> inventoryAmounts = new LinkedHashMap<>();
		Set<BookmarkIngredientKey> indexedKeys = new HashSet<>();
		for (RecipeChainInput inventoryInput : inventoryInputs) {
			indexedKeys.clear();
			for (BookmarkIngredientKey key : inventoryInput.metadata().permutations()) {
				BookmarkIngredientKey availabilityKey = key.getCraftingAvailabilityKey();
				if (indexedKeys.add(availabilityKey)) {
					inventoryAmounts.merge(availabilityKey, inventoryInput.metadata().amount(), SaturatedMath::add);
				}
			}
		}
		for (Item target : targets.values()) {
			BookmarkIngredientKey availabilityKey = target.key().getCraftingAvailabilityKey();
			long inventoryAmount = inventoryAmounts.getOrDefault(availabilityKey, 0L);
			long usedAmount = usedAmounts.getOrDefault(availabilityKey, 0L);
			if (inventoryAmount <= usedAmount) {
				continue;
			}
			long remaining = inventoryAmount - usedAmount;
			if (remaining >= target.amount()) {
				merge(available, target.withAmount(target.amount()));
				usedAmounts.merge(availabilityKey, target.amount(), SaturatedMath::add);
			} else {
				merge(missing, target.withAmount(remaining));
				usedAmounts.merge(availabilityKey, remaining, SaturatedMath::add);
			}
		}
	}

	private static Map<BookmarkIngredientKey, Item> collectAvailable(
		List<RecipeChainInput> inputs,
		RecipeChainDetails details,
		Map<BookmarkIngredientKey, Long> usedAmounts,
		boolean trackUsedAmounts
	) {
		Map<BookmarkIngredientKey, Item> items = new LinkedHashMap<>();
		Set<BookmarkIngredientKey> indexedKeys = trackUsedAmounts ? new HashSet<>() : Set.of();
		for (RecipeChainInput input : inputs) {
			RecipeChainItem item = details.calculatedItems().get(input.index());
			if (item != null && item.type() == RecipeChainItemType.INGREDIENT && item.requiredAmount() > 0 && details.initialItems().contains(input.index())) {
				toItem(input, item.requiredAmount()).ifPresent(available -> merge(items, available));
				if (trackUsedAmounts) {
					indexedKeys.clear();
					for (BookmarkIngredientKey key : input.metadata().permutations()) {
						BookmarkIngredientKey availabilityKey = key.getCraftingAvailabilityKey();
						if (indexedKeys.add(availabilityKey)) {
							usedAmounts.merge(availabilityKey, item.requiredAmount(), SaturatedMath::add);
						}
					}
				}
			}
		}
		return items;
	}

	private static List<Item> collectNeeded(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		return sorted(collectNeededMap(inputs, details));
	}

	private static Map<BookmarkIngredientKey, Item> collectNeededMap(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		Map<BookmarkIngredientKey, Item> items = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			RecipeChainItem item = details.calculatedItems().get(input.index());
			if (
				item != null &&
					input.metadata().type().isGraphOutput() &&
					item.type() == RecipeChainItemType.REMAINDER &&
					item.requiredAmount() > 0
			) {
				toItem(input, item.requiredAmount()).ifPresent(needed -> merge(items, needed));
			}
		}
		return items;
	}

	private static List<Item> collectRemainders(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		Map<BookmarkIngredientKey, Item> byKey = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			RecipeChainItem item = details.calculatedItems().get(input.index());
			if (item != null && item.type() == RecipeChainItemType.REMAINDER && item.providedAmount() > 0) {
				toItem(input, item.providedAmount()).ifPresent(remainder -> merge(byKey, remainder));
			}
		}
		for (Map.Entry<BookmarkIngredientKey, Long> containerItem : details.containerItems().entrySet()) {
			merge(byKey, new Item(
				containerItem.getKey(),
				containerMetadata(containerItem.getKey(), containerItem.getValue()),
				containerItem.getValue(),
				Integer.MIN_VALUE
			));
		}
		return sorted(byKey);
	}

	private static void addSection(List<Section> sections, RecipeChainTooltipSectionType type, List<Item> items) {
		if (!items.isEmpty()) {
			sections.add(new Section(type, items));
		}
	}

	private static Optional<Item> toItem(RecipeChainInput input, long amount) {
		return getDisplayKey(input)
			.map(key -> new Item(key, input.metadata(), amount, input.index()));
	}

	private static Optional<BookmarkIngredientKey> getDisplayKey(RecipeChainInput input) {
		BookmarkIngredientKey selectedKey = input.selectedKey();
		if (selectedKey != null && input.metadata().permutations().contains(selectedKey)) {
			return Optional.of(selectedKey);
		}
		return input.metadata().permutations().stream().findFirst();
	}

	private static void merge(Map<BookmarkIngredientKey, Item> byKey, Item item) {
		Item existing = byKey.get(item.key());
		if (existing == null) {
			byKey.put(item.key(), item);
		} else {
			byKey.put(item.key(), existing.withAmount(SaturatedMath.add(existing.amount(), item.amount())));
		}
	}

	private static List<Item> sorted(List<Item> items) {
		return items.stream()
			.sorted(Comparator.comparing(Item::key).thenComparingInt(Item::sourceIndex))
			.toList();
	}

	private static List<Item> sorted(Map<BookmarkIngredientKey, Item> items) {
		return sorted(new ArrayList<>(items.values()));
	}

	private static BookmarkItemMetadata containerMetadata(BookmarkIngredientKey key, long amount) {
		return new BookmarkItemMetadata(
			BookmarkGroupManager.DEFAULT_GROUP_ID,
			BookmarkItemType.ITEM,
			1,
			amount,
			BookmarkItemMetadata.CHANCE_FULL,
			null,
			null,
			Set.of(key)
		);
	}


	public record Section(
		RecipeChainTooltipSectionType type,
		List<Item> items
	) {
		public Section {
			items = List.copyOf(items);
		}
	}

	public record Item(
		BookmarkIngredientKey key,
		BookmarkItemMetadata metadata,
		long amount,
		int sourceIndex
	) {
		private Item withAmount(long amount) {
			return new Item(key, metadata, amount, sourceIndex);
		}

		public @org.jetbrains.annotations.Nullable ITypedIngredient<?> ingredient() {
			return key.typedIngredient();
		}
	}
}
