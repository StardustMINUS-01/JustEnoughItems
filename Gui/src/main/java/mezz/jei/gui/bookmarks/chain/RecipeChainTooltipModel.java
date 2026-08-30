package mezz.jei.gui.bookmarks.chain;

import mezz.jei.common.util.SaturatedMath;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkIngredientAmountResolver;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
		RecipeChainDetails baseRecipeDetails = baseDetails.orElseGet(() -> RecipeChainMath.refresh(recipeInputs, collapsedRecipes));
		if (!shiftDown) {
			return create(recipeInputs, baseRecipeDetails);
		}
		boolean hasOrdinaryTargets = recipeInputs.stream()
			.anyMatch(input -> input.metadata().type() == BookmarkItemType.ITEM);
		RecipeChainDetails recipeDetails = hasOrdinaryTargets ?
			RecipeChainMath.refresh(recipeInputs.stream()
				.filter(input -> input.metadata().type() != BookmarkItemType.ITEM)
				.toList(), collapsedRecipes) :
			baseRecipeDetails;
		List<RecipeChainInput> adjustedRecipeInputs = !controlDown ?
			expandOutputRecipeMultipliers(recipeInputs, recipeDetails, inventoryInputs) :
			recipeInputs;
		List<RecipeChainInput> calculationInputs = new ArrayList<>(adjustedRecipeInputs.size() + inventoryInputs.size());
		for (RecipeChainInput input : adjustedRecipeInputs) {
			if (input.metadata().type() != BookmarkItemType.ITEM) {
				calculationInputs.add(input);
			}
		}
		calculationInputs.addAll(inventoryInputs);

		RecipeChainDetails details = RecipeChainMath.refresh(calculationInputs, collapsedRecipes);
		Map<BookmarkIngredientKey, Long> usedAmounts = hasOrdinaryTargets ? new LinkedHashMap<>() : Map.of();
		List<Item> available = collectAvailable(calculationInputs, details, usedAmounts, hasOrdinaryTargets);
		List<Item> missing = hasOrdinaryTargets ?
			collectMissing(calculationInputs, details, adjustedRecipeInputs, inventoryInputs, usedAmounts, ingredientManager) :
			sorted(collectMissing(calculationInputs, details));
		List<Section> sections = new ArrayList<>();
		addSection(sections, RecipeChainTooltipSectionType.OUTPUT, collectOutputs(adjustedRecipeInputs, details));
		addSection(sections, RecipeChainTooltipSectionType.MISSING, missing);
		addSection(sections, RecipeChainTooltipSectionType.NEEDED, collectNeeded(calculationInputs, details));
		addSection(sections, RecipeChainTooltipSectionType.AVAILABLE, available);
		addSection(sections, RecipeChainTooltipSectionType.REMAINDER, collectRemainders(calculationInputs, details));
		return new RecipeChainTooltipModel(sections);
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

	private static List<Item> collectMissing(
		List<RecipeChainInput> inputs,
		RecipeChainDetails details,
		List<RecipeChainInput> groupInputs,
		List<RecipeChainInput> inventoryInputs,
		Map<BookmarkIngredientKey, Long> usedAmounts,
		IIngredientManager ingredientManager
	) {
		Map<BookmarkIngredientKey, Item> items = collectMissing(inputs, details);
		Map<BookmarkIngredientKey, Item> targets = new LinkedHashMap<>();
		for (RecipeChainInput input : groupInputs) {
			if (input.metadata().type() == BookmarkItemType.ITEM) {
				ITypedIngredient<?> ingredient = input.selectedIngredient();
				long amount = BookmarkIngredientAmountResolver.getAmount(ingredient, ingredientManager);
				toItem(input, amount).ifPresent(target -> merge(targets, target));
			}
		}
		if (targets.isEmpty()) {
			return sorted(items);
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
			long available = Math.max(0, inventoryAmount - Math.min(inventoryAmount, usedAmount));
			if (available < target.amount()) {
				merge(items, target.withAmount(target.amount() - available));
			}
		}
		return sorted(items);
	}

	private static List<Item> collectAvailable(
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
		return sorted(items);
	}

	private static List<Item> collectNeeded(List<RecipeChainInput> inputs, RecipeChainDetails details) {
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
		return sorted(items);
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
				containerItem.getValue(),
				null
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
			.map(key -> new Item(key, amount, input.selectedIngredient()));
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

	private static List<Item> sorted(Map<BookmarkIngredientKey, Item> items) {
		return items.values().stream()
			.sorted(Comparator.comparing(Item::key))
			.toList();
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
		long amount,
		@Nullable ITypedIngredient<?> ingredient
	) {
		private Item withAmount(long amount) {
			return new Item(key, amount, ingredient);
		}
	}
}
