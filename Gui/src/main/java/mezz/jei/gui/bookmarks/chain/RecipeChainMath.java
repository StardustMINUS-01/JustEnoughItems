/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.chain;

import mezz.jei.gui.bookmarks.BookmarkItemMetadata;
import mezz.jei.gui.bookmarks.BookmarkItemType;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.bookmarks.BookmarkGroupManager;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipeChainMath {
	public static final ResourceLocation ROOT_RECIPE_UID = ResourceLocation.fromNamespaceAndPath("jei", "recipe-autocrafting");
	private static final ResourceLocation ROOT_RECIPE_TYPE_UID = ResourceLocation.fromNamespaceAndPath("jei", "autocrafting");
	private static final BookmarkIngredientKey ROOT_KEY = new BookmarkIngredientKey("minecraft:item", "minecraft:fire", null);

	private final List<RecipeChainInput> initialItems = new ArrayList<>();
	private final List<RecipeChainInput> recipeIngredients = new ArrayList<>();
	private final List<RecipeChainInput> recipeResults = new ArrayList<>();
	private final Set<ResourceLocation> collapsedRecipes;

	private final Map<ResourceLocation, Long> outputRecipes = new LinkedHashMap<>();
	private final Map<RecipeChainInput, RecipeChainInput> preferredItems = new LinkedHashMap<>();
	private final Map<RecipeChainInput, Long> requiredAmount = new HashMap<>();
	private final Map<RecipeChainInput, Long> workingMultipliers = new HashMap<>();
	private final Map<BookmarkIngredientKey, Long> containerItems = new LinkedHashMap<>();
	private final Map<BookmarkIngredientKey, List<RecipeChainRemainderStack>> reusableContainerItems = new LinkedHashMap<>();
	private final Set<BookmarkIngredientKey> containerItemsBlacklist = new LinkedHashSet<>();
	private int nextSyntheticIndex = -1;

	private RecipeChainMath(List<RecipeChainInput> inputs, Set<ResourceLocation> collapsedRecipes) {
		this.collapsedRecipes = Set.copyOf(collapsedRecipes);
		Map<ResourceLocation, Long> requestedMultipliers = new LinkedHashMap<>();
		for (RecipeChainInput input : inputs) {
			BookmarkItemMetadata metadata = input.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (recipeUid == null || metadata.type() == BookmarkItemType.ITEM) {
				initialItems.add(input);
			} else if (metadata.type() == BookmarkItemType.INGREDIENT) {
				recipeIngredients.add(input);
				workingMultipliers.put(input, 0L);
			} else {
				recipeResults.add(input);
				workingMultipliers.put(input, 0L);
				requestedMultipliers.merge(recipeUid, Math.max(0, metadata.multiplier()), Math::max);
			}
		}

		selectOutputRecipes(requestedMultipliers);
	}

	public static RecipeChainDetails refresh(List<RecipeChainInput> inputs, Set<ResourceLocation> collapsedRecipes) {
		return new RecipeChainMath(inputs, collapsedRecipes).refresh();
	}

	public static RecipeChainMath of(List<RecipeChainInput> inputs, Set<ResourceLocation> collapsedRecipes) {
		return new RecipeChainMath(inputs, collapsedRecipes);
	}

	public ResourceLocation createMasterRoot() {
		if (hasMasterRoot()) {
			return ROOT_RECIPE_UID;
		}

		List<RecipeChainInput> rootIngredients = new ArrayList<>();
		for (RecipeChainInput result : recipeResults) {
			BookmarkItemMetadata metadata = result.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (recipeUid != null && outputRecipes.containsKey(recipeUid)) {
				long multiplier = outputRecipes.get(recipeUid);
				rootIngredients.add(new RecipeChainInput(
					nextSyntheticIndex--,
					new BookmarkItemMetadata(
						metadata.groupId(),
						BookmarkItemType.INGREDIENT,
						1,
						metadata.amount(multiplier),
						BookmarkItemMetadata.CHANCE_FULL,
						metadata.recipeTypeUid(),
						ROOT_RECIPE_UID,
						metadata.permutations()
					)
				));
				containerItemsBlacklist.addAll(metadata.permutations());
			}
		}

		outputRecipes.clear();
		outputRecipes.put(ROOT_RECIPE_UID, 1L);
		recipeResults.removeIf(result -> ROOT_RECIPE_UID.equals(result.metadata().recipeUid()));
		RecipeChainInput rootResult = new RecipeChainInput(
			nextSyntheticIndex--,
			new BookmarkItemMetadata(
				BookmarkGroupManager.DEFAULT_GROUP_ID,
				BookmarkItemType.RESULT,
				1,
				1,
				BookmarkItemMetadata.CHANCE_FULL,
				ROOT_RECIPE_TYPE_UID,
				ROOT_RECIPE_UID,
				Set.of(ROOT_KEY)
			)
		);
		recipeResults.add(rootResult);
		recipeIngredients.addAll(rootIngredients);
		return ROOT_RECIPE_UID;
	}

	/**
	 * Derived from GTNH NEI ShortcutInputHandler.autocraftingIgnoreInventory().
	 * Craft All keeps existing root results and expands the synthetic root demand
	 * to the next requested batch boundary, so autocrafting still produces another
	 * batch when the player already has enough of the final result.
	 */
	public void expandRootDemandForCraftAll(List<RecipeChainInput> inventory) {
		ResourceLocation rootRecipeUid = createMasterRoot();
		for (int i = 0; i < recipeIngredients.size(); i++) {
			RecipeChainInput ingredient = recipeIngredients.get(i);
			BookmarkItemMetadata metadata = ingredient.metadata();
			long requiredAmount = metadata.amount();
			if (requiredAmount <= 0 || !rootRecipeUid.equals(metadata.recipeUid())) {
				continue;
			}

			long availableAmount = 0;
			for (RecipeChainInput available : inventory) {
				BookmarkItemMetadata availableMetadata = available.metadata();
				if (metadata.isSatisfiedBy(availableMetadata)) {
					availableAmount = saturatedAdd(availableAmount, availableMetadata.amount());
				}
			}

			if (availableAmount >= requiredAmount) {
				long remainder = availableAmount % requiredAmount;
				long expandedAmount = saturatedAdd(availableAmount, requiredAmount - remainder);
				recipeIngredients.set(i, new RecipeChainInput(
					ingredient.index(),
					new BookmarkItemMetadata(
						metadata.groupId(),
						metadata.type(),
						1,
						expandedAmount,
						BookmarkItemMetadata.CHANCE_FULL,
						metadata.recipeTypeUid(),
						metadata.recipeUid(),
						metadata.permutations(),
						metadata.containerItem(),
						metadata.containerItemCraftingUses(),
						metadata.brokenContainerItem()
					)
				));
			}
		}
	}

	public boolean hasMasterRoot() {
		return outputRecipes.containsKey(ROOT_RECIPE_UID);
	}

	public RecipeChainDetails refreshDetails() {
		return refresh();
	}

	void replaceInitialItems(List<RecipeChainInput> inputs) {
		initialItems.clear();
		initialItems.addAll(inputs);
	}

	Set<ResourceLocation> outputRecipesSnapshot() {
		return Set.copyOf(outputRecipes.keySet());
	}

	Map<RecipeChainInput, RecipeChainInput> preferredItemsSnapshot() {
		return Map.copyOf(preferredItems);
	}

	private void selectOutputRecipes(Map<ResourceLocation, Long> requestedMultipliers) {
		for (Map.Entry<ResourceLocation, Long> entry : requestedMultipliers.entrySet()) {
			if (entry.getValue() > 1 || collapsedRecipes.contains(entry.getKey())) {
				collectPreferredItems(entry.getKey(), preferredItems, new HashSet<>());
				removeLoop(entry.getKey(), preferredItems, new HashSet<>());
				outputRecipes.put(entry.getKey(), entry.getValue());
			}
		}

		while (true) {
			Map<RecipeChainInput, RecipeChainInput> bestReferences = Map.of();
			ResourceLocation bestRecipeUid = null;
			long bestMultiplier = 0;
			int bestDepth = 0;

			for (Map.Entry<ResourceLocation, Long> entry : requestedMultipliers.entrySet()) {
				ResourceLocation recipeUid = entry.getKey();
				if (!outputRecipes.containsKey(recipeUid) && preferredItems.values().stream()
					.noneMatch(result -> recipeUid.equals(result.metadata().recipeUid()))) {
					Map<RecipeChainInput, RecipeChainInput> references = new LinkedHashMap<>(preferredItems);
					collectPreferredItems(recipeUid, references, new HashSet<>());
					removeLoop(recipeUid, references, new HashSet<>());
					int depth = getMaxDepth(recipeUid, references);
					if (bestDepth < depth || bestDepth == depth && entry.getValue() > bestMultiplier) {
						bestReferences = references;
						bestRecipeUid = recipeUid;
						bestMultiplier = entry.getValue();
						bestDepth = depth;
					}
				}
			}

			if (bestReferences.isEmpty() || bestRecipeUid == null) {
				break;
			}
			preferredItems.putAll(bestReferences);
			outputRecipes.put(bestRecipeUid, requestedMultipliers.get(bestRecipeUid));
		}

		for (Map.Entry<ResourceLocation, Long> entry : requestedMultipliers.entrySet()) {
			ResourceLocation recipeUid = entry.getKey();
			boolean outputRecipe = outputRecipes.containsKey(recipeUid);
			boolean recipeInMiddle = preferredItems.values().stream()
				.anyMatch(result -> recipeUid.equals(result.metadata().recipeUid()));
			if (!outputRecipe && !recipeInMiddle) {
				outputRecipes.put(recipeUid, entry.getValue());
			} else if (outputRecipe && recipeInMiddle) {
				outputRecipes.put(recipeUid, Math.max(0, entry.getValue() - 1));
			}
		}
	}

	private void collectPreferredItems(
		ResourceLocation recipeUid,
		Map<RecipeChainInput, RecipeChainInput> preferredItems,
		Set<ResourceLocation> visited
	) {
		visited.add(recipeUid);
		for (RecipeChainInput ingredient : recipeIngredients) {
			BookmarkItemMetadata ingredientMetadata = ingredient.metadata();
			if (ingredientMetadata.emptyFactor() ||
				!recipeUid.equals(ingredientMetadata.recipeUid()) ||
				preferredItems.containsKey(ingredient)) {
				continue;
			}

			RecipeChainInput preferred = findPreferredResult(ingredient, visited);
			if (preferred != null) {
				preferredItems.put(ingredient, preferred);
				ResourceLocation preferredRecipe = preferred.metadata().recipeUid();
				if (preferredRecipe != null) {
					collectPreferredItems(preferredRecipe, preferredItems, visited);
				}
			}
		}
		visited.remove(recipeUid);
	}

	private RecipeChainInput findPreferredResult(RecipeChainInput ingredient, Set<ResourceLocation> visited) {
		RecipeChainInput exact = null;
		RecipeChainInput fallback = null;
		for (RecipeChainInput result : recipeResults) {
			BookmarkItemMetadata resultMetadata = result.metadata();
			ResourceLocation recipeUid = resultMetadata.recipeUid();
			if (resultMetadata.emptyFactor() ||
				recipeUid == null ||
				visited.contains(recipeUid) ||
				!ingredient.metadata().isSatisfiedBy(resultMetadata)) {
				continue;
			}
			if (samePermutationSet(resultMetadata, ingredient.metadata())) {
				if (exact == null || resultMetadata.amount(1) > exact.metadata().amount(1)) {
					exact = result;
				}
			} else if (fallback == null || resultMetadata.amount(1) > fallback.metadata().amount(1)) {
				fallback = result;
			}
		}
		return exact == null ? fallback : exact;
	}

	private int getMaxDepth(ResourceLocation recipeUid, Map<RecipeChainInput, RecipeChainInput> preferredItems) {
		int maxDepth = 0;
		for (RecipeChainInput ingredient : preferredItems.keySet()) {
			if (!ingredient.metadata().emptyFactor() && recipeUid.equals(ingredient.metadata().recipeUid())) {
				RecipeChainInput preferred = preferredItems.get(ingredient);
				ResourceLocation preferredRecipe = preferred.metadata().recipeUid();
				if (preferredRecipe != null) {
					maxDepth = Math.max(maxDepth, getMaxDepth(preferredRecipe, preferredItems) + 1);
				}
			}
		}
		return maxDepth;
	}

	private void removeLoop(
		ResourceLocation recipeUid,
		Map<RecipeChainInput, RecipeChainInput> preferredItems,
		Set<ResourceLocation> visited
	) {
		visited.add(recipeUid);
		for (RecipeChainInput ingredient : recipeIngredients) {
			if (!ingredient.metadata().emptyFactor() &&
				recipeUid.equals(ingredient.metadata().recipeUid()) &&
				preferredItems.containsKey(ingredient)) {
				RecipeChainInput preferred = preferredItems.get(ingredient);
				ResourceLocation preferredRecipe = preferred.metadata().recipeUid();
				if (visited.contains(preferredRecipe)) {
					preferredItems.remove(ingredient);
				} else if (preferredRecipe != null) {
					removeLoop(preferredRecipe, preferredItems, visited);
				}
			}
		}
		visited.remove(recipeUid);
	}

	private RecipeChainDetails refresh() {
		resetCalculation();
		for (Map.Entry<ResourceLocation, Long> outputRecipe : outputRecipes.entrySet()) {
			ResourceLocation recipeUid = outputRecipe.getKey();
			long multiplier = outputRecipe.getValue();
			for (RecipeChainInput result : recipeResults) {
				if (!result.metadata().emptyFactor() && result.metadata().equalsRecipe(recipeUid, result.metadata().groupId())) {
					long resultAmount = result.metadata().amount(multiplier);
					preferredItems.put(result, result);
					calculateSuitableRecipe(result, resultAmount, new ArrayList<>());
					preferredItems.remove(result);
				}
			}
		}

		for (Map.Entry<ResourceLocation, Long> outputRecipe : outputRecipes.entrySet()) {
			ResourceLocation recipeUid = outputRecipe.getKey();
			long multiplier = outputRecipe.getValue();
			for (RecipeChainInput result : recipeResults) {
				if (!result.metadata().emptyFactor() && result.metadata().equalsRecipe(recipeUid, result.metadata().groupId())) {
					requiredAmount.computeIfPresent(result, (ignored, amount) -> amount - result.metadata().amount(multiplier));
				}
			}
		}

		return createDetails();
	}

	private void resetCalculation() {
		preferredItems.clear();
		requiredAmount.clear();
		containerItems.clear();
		reusableContainerItems.clear();
		workingMultipliers.clear();

		for (RecipeChainInput ingredient : recipeIngredients) {
			workingMultipliers.put(ingredient, 0L);
		}
		for (RecipeChainInput result : recipeResults) {
			workingMultipliers.put(result, 0L);
		}

		for (ResourceLocation recipeUid : outputRecipes.keySet()) {
			collectPreferredItems(recipeUid, preferredItems, new HashSet<>());
			removeLoop(recipeUid, preferredItems, new HashSet<>());
		}
	}

	private void calculateSuitableRecipe(RecipeChainInput ingredient, long amount, List<ResourceLocation> visited) {
		RecipeChainInput preferred = preferredItems.get(ingredient);

		if (amount > 0) {
			amount = shiftContainerItems(ingredient.metadata(), amount);
		}

		if (amount > 0) {
			for (RecipeChainInput initialItem : initialItems) {
				if (ingredient.metadata().isSatisfiedBy(initialItem.metadata())) {
					amount = addRequiredAmount(initialItem, amount, initialItem.metadata().amount());
					if (amount == 0) {
						break;
					}
				}
			}
		}

		if (preferred == null) {
			addRequiredAmount(ingredient, amount, Long.MAX_VALUE);
			return;
		}

		ResourceLocation preferredRecipe = preferred.metadata().recipeUid();
		if (preferredRecipe == null || visited.contains(preferredRecipe)) {
			addRequiredAmount(preferred, amount, Long.MAX_VALUE);
			return;
		}

		addRequiredAmount(preferred, amount, Long.MAX_VALUE);
		long multiplier = Math.max(
			0,
			preferred.metadata().multiplierFromAmount(requiredAmount.getOrDefault(preferred, 0L)) -
				workingMultipliers.getOrDefault(preferred, 0L)
		);
		if (multiplier > 0) {
			addShift(preferredRecipe, multiplier);
			visited.add(preferredRecipe);
			prepareIngredients(preferredRecipe, multiplier, visited);
			visited.remove(preferredRecipe);
		}
	}

	private void prepareIngredients(ResourceLocation recipeUid, long multiplier, List<ResourceLocation> visited) {
		for (RecipeChainInput ingredient : recipeIngredients) {
			if (!ingredient.metadata().emptyFactor() && recipeUid.equals(ingredient.metadata().recipeUid())) {
				calculateSuitableRecipe(ingredient, ingredient.metadata().amount(multiplier), visited);
			}
		}
	}

	private long addRequiredAmount(RecipeChainInput input, long amount, long maxAmount) {
		if (amount <= 0) {
			requiredAmount.putIfAbsent(input, 0L);
			return 0;
		}
		if (isReusableContainerItem(input.metadata())) {
			return addReusableContainerRequiredAmount(input, amount, maxAmount);
		}
		long currentAmount = requiredAmount.getOrDefault(input, 0L);
		long available = Math.max(0, maxAmount - currentAmount);
		long consumed = Math.min(amount, available);
		addContainerItems(input.metadata(), consumed);
		if (consumed > 0 || requiredAmount.containsKey(input)) {
			requiredAmount.put(input, saturatedAdd(currentAmount, consumed));
		}
		return amount - consumed;
	}

	private long addReusableContainerRequiredAmount(RecipeChainInput input, long amount, long maxAmount) {
		BookmarkItemMetadata metadata = input.metadata();
		long currentAmount = requiredAmount.getOrDefault(input, 0L);
		long remaining = amount;
		long usesPerItem = metadata.containerItemCraftingUses();
		while (remaining > 0 && currentAmount < maxAmount) {
			currentAmount++;
			long covered = Math.min(remaining, usesPerItem);
			remaining -= covered;
			long remainingUses = usesPerItem - covered;
			addReusableContainerItem(metadata, remainingUses);
			if (remainingUses == 0) {
				addContainerItem(metadata.brokenContainerItem(), 1);
			}
		}
		if (currentAmount > 0 || requiredAmount.containsKey(input)) {
			requiredAmount.put(input, currentAmount);
		}
		return remaining;
	}

	private long shiftContainerItems(BookmarkItemMetadata metadata, long amount) {
		long remaining = shiftReusableContainerItems(metadata, amount);
		if (remaining <= 0) {
			return 0;
		}
		if (metadata.containerItem() == null) {
			return remaining;
		}
		for (BookmarkIngredientKey permutation : metadata.permutations()) {
			if (remaining <= 0) {
				break;
			}
			long available = containerItems.getOrDefault(permutation, 0L);
			if (available <= 0) {
				continue;
			}
			long consumed = Math.min(remaining, available);
			remaining -= consumed;
			long left = available - consumed;
			if (left > 0) {
				containerItems.put(permutation, left);
			} else {
				containerItems.remove(permutation);
			}
		}
		return remaining;
	}

	private void addContainerItems(BookmarkItemMetadata metadata, long amount) {
		BookmarkIngredientKey containerItem = metadata.containerItem();
		boolean blacklisted = metadata.permutations().stream()
			.anyMatch(containerItemsBlacklist::contains);
		if (containerItem != null && metadata.containerItemCraftingUses() > 0 && amount > 0 && !blacklisted) {
			addContainerItem(containerItem, amount);
		}
	}

	private void addReusableContainerItem(BookmarkItemMetadata metadata, long remainingUses) {
		BookmarkIngredientKey containerItem = metadata.containerItem();
		boolean blacklisted = metadata.permutations().stream()
			.anyMatch(containerItemsBlacklist::contains);
		if (containerItem != null && remainingUses > 0 && !blacklisted) {
			reusableContainerItems.computeIfAbsent(containerItem, ignored -> new ArrayList<>())
				.add(new RecipeChainRemainderStack(containerItem, remainingUses, metadata.brokenContainerItem()));
			addContainerItem(containerItem, 1);
		}
	}

	private long shiftReusableContainerItems(BookmarkItemMetadata metadata, long amount) {
		long remaining = amount;
		for (BookmarkIngredientKey permutation : metadata.permutations()) {
			if (remaining <= 0) {
				break;
			}
			List<RecipeChainRemainderStack> reusableItems = reusableContainerItems.get(permutation);
			if (reusableItems == null) {
				continue;
			}
			for (int i = 0; i < reusableItems.size() && remaining > 0;) {
				RecipeChainRemainderStack reusableItem = reusableItems.get(i);
				long availableUses = reusableItem.remainingUses();
				long consumed = Math.min(remaining, availableUses);
				remaining -= consumed;
				availableUses -= consumed;
				if (availableUses > 0) {
					reusableItems.set(i, reusableItem.withRemainingUses(availableUses));
					i++;
				} else {
					reusableItems.remove(i);
					decrementContainerItem(permutation);
					addContainerItem(reusableItem.brokenKey(), 1);
				}
			}
			if (reusableItems.isEmpty()) {
				reusableContainerItems.remove(permutation);
			}
		}
		return remaining;
	}

	private void addContainerItem(BookmarkIngredientKey key, long amount) {
		if (key != null && amount > 0) {
			containerItems.merge(key, amount, RecipeChainMath::saturatedAdd);
		}
	}

	private void decrementContainerItem(BookmarkIngredientKey key) {
		long amount = containerItems.getOrDefault(key, 0L);
		if (amount <= 1) {
			containerItems.remove(key);
		} else {
			containerItems.put(key, amount - 1);
		}
	}

	private static boolean isReusableContainerItem(BookmarkItemMetadata metadata) {
		return metadata.containerItem() != null && metadata.containerItemCraftingUses() > 1;
	}

	private void addShift(ResourceLocation recipeUid, long shift) {
		for (RecipeChainInput ingredient : recipeIngredients) {
			if (recipeUid.equals(ingredient.metadata().recipeUid())) {
				workingMultipliers.merge(ingredient, shift, RecipeChainMath::saturatedAdd);
			}
		}
		for (RecipeChainInput result : recipeResults) {
			if (recipeUid.equals(result.metadata().recipeUid())) {
				workingMultipliers.merge(result, shift, RecipeChainMath::saturatedAdd);
			}
		}
	}

	private RecipeChainDetails createDetails() {
		Map<Integer, RecipeChainItem> calculatedItems = new LinkedHashMap<>();
		Map<Integer, ResourceLocation> itemToRecipe = createItemToRecipe();
		Set<ResourceLocation> middleRecipes = getMiddleRecipes();
		Map<ResourceLocation, Set<ResourceLocation>> recipeRelations = createRecipeRelations();
		Map<BookmarkIngredientKey, Long> missedItems = new LinkedHashMap<>();
		Set<Integer> initialItemIndexes = new LinkedHashSet<>();
		Set<Integer> missingIngredients = new LinkedHashSet<>();
		Set<Integer> remainderItems = new LinkedHashSet<>();

		for (RecipeChainInput initialItem : initialItems) {
			initialItemIndexes.add(initialItem.index());
			BookmarkItemMetadata metadata = initialItem.metadata();
			long required = requiredAmount.getOrDefault(initialItem, 0L);
			calculatedItems.put(initialItem.index(), new RecipeChainItem(
				initialItem.index(),
				metadata,
				RecipeChainItemType.INGREDIENT,
				metadata.amount(),
				required,
				metadata.amount(),
				metadata.multiplier(),
				metadata.multiplier()
			));
		}

		for (RecipeChainInput result : recipeResults) {
			BookmarkItemMetadata metadata = result.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			long calculatedMultiplier = workingMultipliers.getOrDefault(result, 0L);
			long required = requiredAmount.getOrDefault(result, 0L);
			long calculatedAmount = metadata.amount(calculatedMultiplier);
			long shiftAmount = Math.max(0, calculatedAmount - required);
			RecipeChainItemType type = recipeUid != null && outputRecipes.containsKey(recipeUid) ?
				RecipeChainItemType.RESULT :
				RecipeChainItemType.REMAINDER;
			if (shiftAmount > 0 && type == RecipeChainItemType.REMAINDER) {
				remainderItems.add(result.index());
			}
			long realMultiplier = getRealMultiplier(metadata, middleRecipes);
			calculatedItems.put(result.index(), new RecipeChainItem(
				result.index(),
				metadata,
				type,
				metadata.amount(realMultiplier),
				shiftAmount,
				calculatedAmount,
				realMultiplier,
				calculatedMultiplier
			));
		}

		for (RecipeChainInput ingredient : recipeIngredients) {
			BookmarkItemMetadata metadata = ingredient.metadata();
			long calculatedMultiplier = workingMultipliers.getOrDefault(ingredient, 0L);
			RecipeChainInput preferred = preferredItems.get(ingredient);
			long required = requiredAmount.containsKey(preferred) ?
				0 :
				requiredAmount.getOrDefault(ingredient, metadata.amount(calculatedMultiplier));
			if (required > 0) {
				missingIngredients.add(ingredient.index());
				addMissedItem(missedItems, metadata, required);
			}
			long realMultiplier = getRealMultiplier(metadata, middleRecipes);
			calculatedItems.put(ingredient.index(), new RecipeChainItem(
				ingredient.index(),
				metadata,
				RecipeChainItemType.INGREDIENT,
				metadata.amount(realMultiplier),
				required,
				metadata.amount(calculatedMultiplier),
				realMultiplier,
				calculatedMultiplier
			));
		}

		return new RecipeChainDetails(
			calculatedItems,
			itemToRecipe,
			outputRecipes.keySet(),
			middleRecipes,
			recipeRelations,
			missedItems,
			containerItems,
			initialItemIndexes,
			missingIngredients,
			remainderItems
		);
	}

	private static void addMissedItem(Map<BookmarkIngredientKey, Long> missedItems, BookmarkItemMetadata metadata, long amount) {
		metadata.permutations().stream()
			.findFirst()
			.ifPresent(key -> missedItems.merge(key, amount, RecipeChainMath::saturatedAdd));
	}

	private Set<ResourceLocation> getMiddleRecipes() {
		Set<ResourceLocation> middleRecipes = new LinkedHashSet<>();
		for (RecipeChainInput result : preferredItems.values()) {
			ResourceLocation recipeUid = result.metadata().recipeUid();
			if (recipeUid != null) {
				middleRecipes.add(recipeUid);
			}
		}
		return middleRecipes;
	}

	private Map<ResourceLocation, Set<ResourceLocation>> createRecipeRelations() {
		Map<ResourceLocation, Set<ResourceLocation>> recipeRelations = new LinkedHashMap<>();
		for (ResourceLocation recipeUid : collapsedRecipes) {
			recipeRelations.put(recipeUid, getRecipeRelations(recipeUid, new LinkedHashSet<>(Set.of(recipeUid))));
		}
		return recipeRelations;
	}

	private Set<ResourceLocation> getRecipeRelations(ResourceLocation recipeUid, Set<ResourceLocation> recipes) {
		for (RecipeChainInput ingredient : recipeIngredients) {
			if (recipeUid.equals(ingredient.metadata().recipeUid())) {
				RecipeChainInput preferred = preferredItems.get(ingredient);
				ResourceLocation preferredRecipe = preferred == null ? null : preferred.metadata().recipeUid();
				if (preferredRecipe != null && recipes.add(preferredRecipe)) {
					getRecipeRelations(preferredRecipe, recipes);
				}
			}
		}
		return recipes;
	}

	private Map<Integer, ResourceLocation> createItemToRecipe() {
		Map<Integer, ResourceLocation> itemToRecipe = new LinkedHashMap<>();
		if (collapsedRecipes.isEmpty()) {
			return itemToRecipe;
		}
		Map<ResourceLocation, Set<ResourceLocation>> recipeRelations = createRecipeRelations();
		for (ResourceLocation collapsedRecipe : collapsedRecipes) {
			Set<ResourceLocation> relations = recipeRelations.getOrDefault(collapsedRecipe, Set.of(collapsedRecipe));
			for (RecipeChainInput input : recipeIngredients) {
				if (relations.contains(input.metadata().recipeUid())) {
					itemToRecipe.put(input.index(), collapsedRecipe);
				}
			}
			for (RecipeChainInput input : recipeResults) {
				if (relations.contains(input.metadata().recipeUid())) {
					itemToRecipe.put(input.index(), collapsedRecipe);
				}
			}
		}
		return itemToRecipe;
	}

	private static long getRealMultiplier(BookmarkItemMetadata metadata, Set<ResourceLocation> middleRecipes) {
		ResourceLocation recipeUid = metadata.recipeUid();
		if (recipeUid != null && middleRecipes.contains(recipeUid)) {
			return Math.max(0, metadata.multiplier() - 1);
		}
		return metadata.multiplier();
	}

	private static boolean samePermutationSet(BookmarkItemMetadata first, BookmarkItemMetadata second) {
		return first.permutations().equals(second.permutations());
	}

	private static long saturatedAdd(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}
}
