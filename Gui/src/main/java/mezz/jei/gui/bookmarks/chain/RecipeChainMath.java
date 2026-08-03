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
import java.util.Comparator;
import java.util.HashMap;
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
	private RecipeChainGraph graph;
	private RecipeChainPlan plan;

	private final Map<ResourceLocation, Long> outputRecipes = new LinkedHashMap<>();
	private final Map<ResourceLocation, RecipeChainInput> outputTargets = new LinkedHashMap<>();
	private final Map<RecipeChainInput, RecipeChainInput> preferredItems = new LinkedHashMap<>();
	private final Map<RecipeChainInput, Long> requiredAmount = new HashMap<>();
	private final Map<RecipeChainInput, Long> workingMultipliers = new HashMap<>();
	private final Map<BookmarkIngredientKey, Long> containerItems = new LinkedHashMap<>();
	private final Map<BookmarkIngredientKey, List<RecipeChainRemainderStack>> reusableContainerItems = new LinkedHashMap<>();
	private final Set<BookmarkIngredientKey> containerItemsBlacklist = new LinkedHashSet<>();
	private int nextSyntheticIndex = -1;

	private RecipeChainMath(List<RecipeChainInput> inputs, Set<ResourceLocation> collapsedRecipes) {
		this.collapsedRecipes = Set.copyOf(collapsedRecipes);
		for (RecipeChainInput input : inputs) {
			BookmarkItemMetadata metadata = input.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			BookmarkItemType type = metadata.type();
			if (recipeUid == null || !type.isRecipeAssociated()) {
				initialItems.add(input);
			} else if (type.isGraphInput()) {
				recipeIngredients.add(input);
				workingMultipliers.put(input, 0L);
			} else if (type.isGraphOutput()) {
				recipeResults.add(input);
				workingMultipliers.put(input, 0L);
				outputTargets.putIfAbsent(recipeUid, input);
			}
		}
		rebuildPlan();
		outputTargets.clear();
		outputTargets.putAll(plan.outputTargets());
		outputRecipes.putAll(plan.outputRecipes());
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
			if (recipeUid != null && outputRecipes.containsKey(recipeUid) && isOutputTarget(result)) {
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
		outputTargets.clear();
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
		rebuildPlan();
		outputTargets.putAll(plan.outputTargets());
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
		rebuildPlan();
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

	private RecipeChainDetails refresh() {
		resetCalculation();
		for (Map.Entry<ResourceLocation, Long> outputRecipe : outputRecipes.entrySet()) {
			ResourceLocation recipeUid = outputRecipe.getKey();
			long multiplier = outputRecipe.getValue();
			for (RecipeChainInput result : graph.resultsFor(recipeUid)) {
				if (!result.metadata().emptyFactor() && isOutputTarget(result) && result.metadata().equalsRecipe(recipeUid, result.metadata().groupId())) {
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
			for (RecipeChainInput result : graph.resultsFor(recipeUid)) {
				if (!result.metadata().emptyFactor() && isOutputTarget(result) && result.metadata().equalsRecipe(recipeUid, result.metadata().groupId())) {
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

		preferredItems.putAll(plan.preferredResults());
	}

	private void rebuildPlan() {
		List<RecipeChainInput> graphInputs = new ArrayList<>(recipeIngredients.size() + recipeResults.size());
		graphInputs.addAll(recipeIngredients);
		graphInputs.addAll(recipeResults);
		this.plan = RecipeChainPlan.compile(graphInputs, collapsedRecipes);
		this.graph = plan.graph();
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
		for (RecipeChainInput ingredient : graph.ingredientsFor(recipeUid)) {
			if (!ingredient.metadata().emptyFactor()) {
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
		for (RecipeChainInput ingredient : graph.ingredientsFor(recipeUid)) {
			workingMultipliers.merge(ingredient, shift, RecipeChainMath::saturatedAdd);
		}
		for (RecipeChainInput result : graph.resultsFor(recipeUid)) {
			workingMultipliers.merge(result, shift, RecipeChainMath::saturatedAdd);
		}
	}

	private RecipeChainDetails createDetails() {
		Map<Integer, RecipeChainItem> calculatedItems = new LinkedHashMap<>();
		Set<ResourceLocation> topLevelRecipes = getTopLevelRecipes();
		Map<ResourceLocation, Set<ResourceLocation>> recipeRelations = createRecipeRelations(topLevelRecipes);
		Map<Integer, ResourceLocation> itemToRecipe = createItemToRecipe(recipeRelations);
		Set<ResourceLocation> middleRecipes = getMiddleRecipes();
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
			RecipeChainItemType type = recipeUid != null && outputRecipes.containsKey(recipeUid) && isOutputTarget(result) ?
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

		Map<ResourceLocation, RecipeChainDetails.CollapsedBlock> collapsedBlocks = createCollapsedBlocks(topLevelRecipes, itemToRecipe, recipeRelations);

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
			remainderItems,
			collapsedBlocks
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

	private Map<ResourceLocation, Set<ResourceLocation>> createRecipeRelations(Set<ResourceLocation> topLevelRecipes) {
		Map<ResourceLocation, Set<ResourceLocation>> recipeRelations = new LinkedHashMap<>();
		for (ResourceLocation recipeUid : sortedCollapsedRecipes()) {
			recipeRelations.put(recipeUid, getRecipeRelations(recipeUid, topLevelRecipes, new LinkedHashSet<>(Set.of(recipeUid))));
		}
		return recipeRelations;
	}

	private Set<ResourceLocation> getRecipeRelations(ResourceLocation recipeUid, Set<ResourceLocation> topLevelRecipes, Set<ResourceLocation> recipes) {
		for (RecipeChainInput ingredient : graph.ingredientsFor(recipeUid)) {
			RecipeChainInput preferred = preferredItems.get(ingredient);
			ResourceLocation preferredRecipe = preferred == null ? null : preferred.metadata().recipeUid();
			if (preferredRecipe != null && !topLevelRecipes.contains(preferredRecipe) && recipes.add(preferredRecipe)) {
				getRecipeRelations(preferredRecipe, topLevelRecipes, recipes);
			}
		}
		return recipes;
	}

	private Set<ResourceLocation> getTopLevelRecipes() {
		Set<ResourceLocation> topLevelRecipes = new LinkedHashSet<>(outputRecipes.keySet());
		for (RecipeChainInput result : recipeResults) {
			ResourceLocation recipeUid = result.metadata().recipeUid();
			if (recipeUid == null || topLevelRecipes.contains(recipeUid)) {
				continue;
			}
			Set<ResourceLocation> parents = getRecipeParents(recipeUid, new LinkedHashSet<>(), new LinkedHashSet<>(Set.of(recipeUid)));
			if (parents.size() != 1 || parents.stream().noneMatch(collapsedRecipes::contains)) {
				topLevelRecipes.add(recipeUid);
			}
		}
		return topLevelRecipes;
	}

	private Set<ResourceLocation> getRecipeParents(ResourceLocation recipeUid, Set<ResourceLocation> parents, Set<ResourceLocation> visited) {
		Set<ResourceLocation> consumers = new LinkedHashSet<>();
		for (Map.Entry<RecipeChainInput, RecipeChainInput> entry : preferredItems.entrySet()) {
			RecipeChainInput preferred = entry.getValue();
			if (preferred != null && recipeUid.equals(preferred.metadata().recipeUid())) {
				ResourceLocation consumer = entry.getKey().metadata().recipeUid();
				if (consumer != null) {
					consumers.add(consumer);
				}
			}
		}
		if (consumers.isEmpty()) {
			parents.add(recipeUid);
		} else {
			for (ResourceLocation consumer : consumers) {
				if (visited.add(consumer)) {
					if (outputRecipes.containsKey(consumer)) {
						parents.add(consumer);
					} else {
						getRecipeParents(consumer, parents, visited);
					}
				}
			}
		}
		return parents;
	}

	private Map<Integer, ResourceLocation> createItemToRecipe(Map<ResourceLocation, Set<ResourceLocation>> recipeRelations) {
		Map<Integer, ResourceLocation> itemToRecipe = new LinkedHashMap<>();
		if (collapsedRecipes.isEmpty()) {
			return itemToRecipe;
		}
		for (ResourceLocation collapsedRecipe : sortedCollapsedRecipes()) {
			Set<ResourceLocation> relations = recipeRelations.getOrDefault(collapsedRecipe, Set.of(collapsedRecipe));
			for (ResourceLocation recipeUid : relations) {
				for (RecipeChainInput input : graph.itemsFor(recipeUid)) {
					itemToRecipe.put(input.index(), collapsedRecipe);
				}
			}
		}
		return itemToRecipe;
	}

	private Map<ResourceLocation, RecipeChainDetails.CollapsedBlock> createCollapsedBlocks(
		Set<ResourceLocation> topLevelRecipes,
		Map<Integer, ResourceLocation> itemToRecipe,
		Map<ResourceLocation, Set<ResourceLocation>> recipeRelations
	) {
		Map<ResourceLocation, RecipeChainDetails.CollapsedBlock> collapsedBlocks = new LinkedHashMap<>();
		if (collapsedRecipes.isEmpty()) {
			return collapsedBlocks;
		}
		Set<ResourceLocation> middleRecipes = getMiddleRecipes();
		for (ResourceLocation root : sortedCollapsedRecipes()) {
			Set<ResourceLocation> relations = recipeRelations.getOrDefault(root, Set.of(root));
			List<RecipeChainInput> closureResults = recipeResults.stream()
				.filter(result -> root.equals(itemToRecipe.get(result.index())))
				.filter(result -> {
					ResourceLocation recipeUid = result.metadata().recipeUid();
					return recipeUid != null && relations.contains(recipeUid);
				})
				.toList();
			List<RecipeChainInput> closureIngredients = recipeIngredients.stream()
				.filter(ingredient -> root.equals(itemToRecipe.get(ingredient.index())))
				.filter(ingredient -> {
					ResourceLocation recipeUid = ingredient.metadata().recipeUid();
					return recipeUid != null && relations.contains(recipeUid);
				})
				.toList();
			Map<String, Long> shadowShifts = computeShadowShifts(root, relations);
			List<BlockAccumulator> collected = collectCollapsedItems(
				root,
				relations,
				topLevelRecipes,
				outputRecipes.keySet(),
				closureResults,
				closureIngredients,
				preferredItems,
				requiredAmount,
				workingMultipliers
			);
			for (BlockAccumulator accumulator : collected) {
				long realMultiplier;
				if (accumulator.anchor()) {
					realMultiplier = middleRecipes.contains(root) ?
						Math.max(0, accumulator.metadata().multiplier() - 1) :
						accumulator.metadata().multiplier();
				} else {
					realMultiplier = accumulator.metadata()
						.multiplierFromAmount(shadowShifts.getOrDefault(accumulator.key(), 0L));
				}
				accumulator.setRealMultiplier(realMultiplier);
			}

			List<RecipeChainDetails.CollapsedBlockItem> blockItems = new ArrayList<>();
			Comparator<BlockAccumulator> blockComparator = Comparator
				.comparingInt(BlockAccumulator::typeWeight)
				.thenComparingInt(BlockAccumulator::sourceIndex);
			collected.stream()
				.sorted(blockComparator)
				.map(BlockAccumulator::toBlockItem)
				.forEach(blockItems::add);
			collapsedBlocks.put(root, new RecipeChainDetails.CollapsedBlock(root, List.copyOf(blockItems)));
		}
		return collapsedBlocks;
	}

	/**
	 * Derived from GTNH NEI RecipeChainDetails.generateShadowItems(): re-runs the closure math
	 * with the bookmark multipliers (the collapsed root keeps its multiplier, every other
	 * closure recipe is zeroed) so the block can show the real, bookmark-driven amounts.
	 */
	private Map<String, Long> computeShadowShifts(ResourceLocation root, Set<ResourceLocation> relations) {
		List<RecipeChainInput> subResults = new ArrayList<>();
		List<RecipeChainInput> subIngredients = new ArrayList<>();
		for (RecipeChainInput result : recipeResults) {
			ResourceLocation recipeUid = result.metadata().recipeUid();
			if (recipeUid != null && relations.contains(recipeUid)) {
				long multiplier = root.equals(recipeUid) ? result.metadata().multiplier() : 0;
				BookmarkItemMetadata metadata = result.metadata().withMultiplier(multiplier);
				subResults.add(new RecipeChainInput(result.index(), metadata, result.selectedKey(), result.selectedIngredient()));
			}
		}
		for (RecipeChainInput ingredient : recipeIngredients) {
			ResourceLocation recipeUid = ingredient.metadata().recipeUid();
			if (recipeUid != null && relations.contains(recipeUid)) {
				long multiplier = root.equals(recipeUid) ? ingredient.metadata().multiplier() : 0;
				BookmarkItemMetadata metadata = ingredient.metadata().withMultiplier(multiplier);
				subIngredients.add(new RecipeChainInput(ingredient.index(), metadata, ingredient.selectedKey(), ingredient.selectedIngredient()));
			}
		}
		List<RecipeChainInput> subInputs = new ArrayList<>(subResults.size() + subIngredients.size());
		subInputs.addAll(subResults);
		subInputs.addAll(subIngredients);
		RecipeChainMath shadowMath = RecipeChainMath.of(subInputs, Set.of());
		shadowMath.refresh();
		List<BlockAccumulator> shadowItems = collectCollapsedItems(
			root,
			relations,
			Set.of(),
			shadowMath.outputRecipes.keySet(),
			subResults,
			subIngredients,
			shadowMath.preferredItems,
			shadowMath.requiredAmount,
			shadowMath.workingMultipliers
		);
		Map<String, Long> shifts = new HashMap<>();
		for (BlockAccumulator item : shadowItems) {
			shifts.put(item.key(), item.shiftAmount());
		}
		return shifts;
	}

	private static List<BlockAccumulator> collectCollapsedItems(
		ResourceLocation root,
		Set<ResourceLocation> relations,
		Set<ResourceLocation> topLevelRecipes,
		Set<ResourceLocation> outputRecipes,
		List<RecipeChainInput> closureResults,
		List<RecipeChainInput> closureIngredients,
		Map<RecipeChainInput, RecipeChainInput> preferredItems,
		Map<RecipeChainInput, Long> requiredAmount,
		Map<RecipeChainInput, Long> workingMultipliers
	) {
		Map<String, BlockAccumulator> results = new LinkedHashMap<>();
		Map<String, BlockAccumulator> ingredients = new LinkedHashMap<>();
		long rootMultiplier = 0;
		for (RecipeChainInput result : closureResults) {
			BookmarkItemMetadata metadata = result.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (recipeUid == null || !relations.contains(recipeUid)) {
				continue;
			}
			long itemAmount = metadata.amount(workingMultipliers.getOrDefault(result, 0L));
			long required = requiredAmount.getOrDefault(result, 0L);
			long amount = itemAmount - required;
			boolean anchor = root.equals(recipeUid);
			if (anchor) {
				rootMultiplier = workingMultipliers.getOrDefault(result, 0L);
			}
			if (anchor || amount > 0) {
				String key = aggregationKey(metadata, result.index());
				RecipeChainItemType type = outputRecipes.contains(recipeUid) ?
					RecipeChainItemType.RESULT :
					RecipeChainItemType.REMAINDER;
				BlockAccumulator accumulator = results.computeIfAbsent(
					key,
					ignored -> new BlockAccumulator(key, result.index(), metadata, anchor ? RecipeChainItemType.RESULT : type, anchor)
				);
				accumulator.append(amount, itemAmount, metadata);
			}
		}
		for (RecipeChainInput ingredient : closureIngredients) {
			BookmarkItemMetadata metadata = ingredient.metadata();
			ResourceLocation recipeUid = metadata.recipeUid();
			if (recipeUid == null || !relations.contains(recipeUid)) {
				continue;
			}
			RecipeChainInput preferred = preferredItems.get(ingredient);
			long itemAmount = metadata.amount(workingMultipliers.getOrDefault(ingredient, 0L));
			long amount = requiredAmount.containsKey(preferred) ?
				0 :
				requiredAmount.getOrDefault(ingredient, itemAmount);
			long refAmount = preferred != null && !topLevelRecipes.contains(preferred.metadata().recipeUid()) ?
				requiredAmount.getOrDefault(preferred, 0L) :
				0;
			boolean include = amount != 0 ||
				(itemAmount > refAmount && requiredAmount.containsKey(preferred)) ||
				(root.equals(recipeUid) && rootMultiplier == 0);
			if (!include) {
				continue;
			}
			String key = aggregationKey(metadata, ingredient.index());
			BlockAccumulator accumulator = ingredients.computeIfAbsent(
				key,
				ignored -> new BlockAccumulator(key, ingredient.index(), metadata, RecipeChainItemType.INGREDIENT, false)
			);
			accumulator.append(amount, itemAmount - refAmount, metadata);
		}
		List<BlockAccumulator> collected = new ArrayList<>(results.size() + ingredients.size());
		collected.addAll(results.values());
		collected.addAll(ingredients.values());
		return collected;
	}

	private List<ResourceLocation> sortedCollapsedRecipes() {
		return collapsedRecipes.stream().sorted().toList();
	}

	private static String aggregationKey(BookmarkItemMetadata metadata, int sourceIndex) {
		return metadata.permutations().stream()
			.min(Comparator.naturalOrder())
			.map(BookmarkIngredientKey::stableKey)
			.orElse("index:" + sourceIndex);
	}

	private static final class BlockAccumulator {
		private final String key;
		private final int sourceIndex;
		private final BookmarkItemMetadata metadata;
		private final RecipeChainItemType type;
		private final boolean anchor;
		private long shiftAmount;
		private long calculatedAmount;
		private long calculatedMultiplier;
		private long realMultiplier;

		private BlockAccumulator(
			String key,
			int sourceIndex,
			BookmarkItemMetadata metadata,
			RecipeChainItemType type,
			boolean anchor
		) {
			this.key = key;
			this.sourceIndex = sourceIndex;
			this.metadata = metadata;
			this.type = type;
			this.anchor = anchor;
		}

		private void append(long amount, long calculatedAmount, BookmarkItemMetadata metadata) {
			shiftAmount = saturatedAdd(shiftAmount, amount);
			this.calculatedAmount = saturatedAdd(this.calculatedAmount, calculatedAmount);
			calculatedMultiplier = saturatedAdd(calculatedMultiplier, metadata.multiplierFromAmount(calculatedAmount));
		}

		private String key() {
			return key;
		}

		private long shiftAmount() {
			return shiftAmount;
		}

		private BookmarkItemMetadata metadata() {
			return metadata;
		}

		private boolean anchor() {
			return anchor;
		}

		private void setRealMultiplier(long realMultiplier) {
			this.realMultiplier = realMultiplier;
		}

		private int typeWeight() {
			return switch (type) {
				case RESULT -> 0;
				case REMAINDER -> 1;
				case INGREDIENT -> 2;
			};
		}

		private int sourceIndex() {
			return sourceIndex;
		}

		private RecipeChainDetails.CollapsedBlockItem toBlockItem() {
			RecipeChainItem item = new RecipeChainItem(
				sourceIndex,
				metadata,
				type,
				metadata.amount(realMultiplier),
				shiftAmount,
				calculatedAmount,
				realMultiplier,
				calculatedMultiplier
			);
			return new RecipeChainDetails.CollapsedBlockItem(sourceIndex, metadata, item, anchor);
		}
	}

	private static long getRealMultiplier(BookmarkItemMetadata metadata, Set<ResourceLocation> middleRecipes) {
		ResourceLocation recipeUid = metadata.recipeUid();
		if (recipeUid != null && middleRecipes.contains(recipeUid)) {
			return Math.max(0, metadata.multiplier() - 1);
		}
		return metadata.multiplier();
	}

	private boolean isOutputTarget(RecipeChainInput result) {
		ResourceLocation recipeUid = result.metadata().recipeUid();
		return recipeUid != null && outputTargets.get(recipeUid) == result;
	}

	private static long saturatedAdd(long first, long second) {
		try {
			return Math.addExact(first, second);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}
}
