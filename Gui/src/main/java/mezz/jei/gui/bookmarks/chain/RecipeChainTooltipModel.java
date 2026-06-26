package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
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
		Set<ResourceLocation> collapsedRecipes,
		List<RecipeChainInput> inventoryInputs,
		boolean shiftDown,
		boolean controlDown
	) {
		List<RecipeChainInput> adjustedRecipeInputs = shiftDown && !controlDown ?
			expandOutputRecipeMultipliers(recipeInputs, collapsedRecipes, inventoryInputs) :
			recipeInputs;
		List<RecipeChainInput> calculationInputs = new ArrayList<>(adjustedRecipeInputs);
		if (shiftDown) {
			calculationInputs.addAll(inventoryInputs);
		}

		RecipeChainDetails details = RecipeChainMath.refresh(calculationInputs, collapsedRecipes);
		List<Section> sections = new ArrayList<>();
		addSection(sections, RecipeChainTooltipSectionType.OUTPUT, collectOutputs(adjustedRecipeInputs, details));
		if (shiftDown) {
			addSection(sections, RecipeChainTooltipSectionType.MISSING, collectMissing(calculationInputs, details));
			addSection(sections, RecipeChainTooltipSectionType.NEEDED, collectNeeded(calculationInputs, details));
			addSection(sections, RecipeChainTooltipSectionType.AVAILABLE, collectAvailable(calculationInputs, details));
			addSection(sections, RecipeChainTooltipSectionType.REMAINDER, collectRemainders(calculationInputs, details));
		} else {
			addSection(sections, RecipeChainTooltipSectionType.INPUT, collectMissing(calculationInputs, details));
		}
		return new RecipeChainTooltipModel(sections);
	}

	private static List<RecipeChainInput> expandOutputRecipeMultipliers(
		List<RecipeChainInput> recipeInputs,
		Set<ResourceLocation> collapsedRecipes,
		List<RecipeChainInput> inventoryInputs
	) {
		if (inventoryInputs.isEmpty()) {
			return recipeInputs;
		}
		RecipeChainDetails originalDetails = RecipeChainMath.refresh(recipeInputs, collapsedRecipes);
		if (originalDetails.outputRecipes().isEmpty()) {
			return recipeInputs;
		}

		List<RecipeChainInput> adjusted = new ArrayList<>(recipeInputs.size());
		boolean changed = false;
		for (RecipeChainInput input : recipeInputs) {
			BookmarkItemMetadata metadata = input.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (
				metadata.type() != BookmarkItemType.RESULT ||
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
				adjusted.add(new RecipeChainInput(input.index(), metadata.withMultiplier(expandedMultiplier), input.selectedKey()));
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
				amount = saturatedAdd(amount, inventoryMetadata.amount());
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

	private static List<Item> collectMissing(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		Map<BookmarkIngredientKey, Item> items = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			RecipeChainItem item = details.calculatedItems().get(input.index());
			if (item != null && item.type() == RecipeChainItemType.INGREDIENT && item.requiredAmount() > 0 && !details.initialItems().contains(input.index())) {
				toItem(input, item.requiredAmount()).ifPresent(missing -> merge(items, missing));
			}
		}
		return sorted(items);
	}

	private static List<Item> collectAvailable(List<RecipeChainInput> inputs, RecipeChainDetails details) {
		Map<BookmarkIngredientKey, Item> items = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			RecipeChainItem item = details.calculatedItems().get(input.index());
			if (item != null && item.type() == RecipeChainItemType.INGREDIENT && item.requiredAmount() > 0 && details.initialItems().contains(input.index())) {
				toItem(input, item.requiredAmount()).ifPresent(available -> merge(items, available));
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
					input.metadata().type() == BookmarkItemType.RESULT &&
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
			byKey.put(item.key(), existing.withAmount(saturatedAdd(existing.amount(), item.amount())));
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

	private static long saturatedAdd(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
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
	}
}
