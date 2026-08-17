package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.bookmarks.CraftingStackMatcher;
import mezz.jei.common.config.DebugConfig;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record BookmarkCraftingGridFill(
	List<ItemStack> targetStacks,
	int multiplier
) {
	private static final Logger LOGGER = LogManager.getLogger();

	public BookmarkCraftingGridFill {
		targetStacks = targetStacks.stream()
			.map(ItemStack::copy)
			.toList();
		multiplier = Math.max(0, multiplier);
	}

	public static Optional<BookmarkCraftingGridFill> create(
		IRecipeLayoutDrawable<?> recipeLayout,
		int targetSlotCount,
		int multiplier
	) {
		return create(recipeLayout, targetSlotCount, multiplier, List.of());
	}

	public static Optional<BookmarkCraftingGridFill> create(
		IRecipeLayoutDrawable<?> recipeLayout,
		int targetSlotCount,
		int multiplier,
		List<ItemStack> availableStacks
	) {
		if (targetSlotCount <= 0) {
			return Optional.empty();
		}

		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] GRIDFILL-CREATE targetSlotCount={} inputSlots={} multiplier={} availableStacks={}",
				targetSlotCount, inputSlots.size(), multiplier, availableStacks.size());
		}
		List<IngredientGroup> groups = new ArrayList<>();
		List<List<Candidate>> candidateSlots = createCandidateSlots(inputSlots, targetSlotCount, groups);
		findInventoryQuantities(groups, availableStacks);

		List<ItemStack> targetStacks = new ArrayList<>(targetSlotCount);
		boolean hasInput = false;
		for (int i = 0; i < targetSlotCount; i++) {
			List<Candidate> slotCandidates = candidateSlots.get(i);
			ItemStack stack = selectTargetStack(slotCandidates);
			targetStacks.add(stack.copy());
			hasInput |= !stack.isEmpty();
			if (DebugConfig.isDebugModeEnabled()) {
				LOGGER.info("[Bug6] GRIDFILL-SLOT slot={} candidates={} selected={}",
					i, slotCandidates.size(), stack);
			}
		}
		if (!hasInput) {
			return Optional.empty();
		}
		int effectiveMultiplier = calculateRecipeQuantity(groups, multiplier);
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] GRIDFILL-DONE targetStacks={} multiplier={}",
				targetStacks, effectiveMultiplier);
		}
		return Optional.of(new BookmarkCraftingGridFill(targetStacks, effectiveMultiplier));
	}

	private static List<List<Candidate>> createCandidateSlots(
		List<IRecipeSlotView> inputSlots,
		int targetSlotCount,
		List<IngredientGroup> groups
	) {
		List<List<Candidate>> candidateSlots = new ArrayList<>(targetSlotCount);
		for (int i = 0; i < targetSlotCount; i++) {
			candidateSlots.add(new ArrayList<>());
		}
		if (addPositionedCandidateSlots(inputSlots, targetSlotCount, groups, candidateSlots)) {
			return candidateSlots;
		}
		for (int i = 0; i < Math.min(targetSlotCount, inputSlots.size()); i++) {
			candidateSlots.set(i, createCandidates(inputSlots.get(i), groups));
		}
		return candidateSlots;
	}

	private static boolean addPositionedCandidateSlots(
		List<IRecipeSlotView> inputSlots,
		int targetSlotCount,
		List<IngredientGroup> groups,
		List<List<Candidate>> candidateSlots
	) {
		int columns = switch (targetSlotCount) {
			case 4 -> 2;
			case 9 -> 3;
			default -> 0;
		};
		if (columns == 0 || inputSlots.isEmpty()) {
			return false;
		}
		List<PositionedSlot> positionedSlots = new ArrayList<>(inputSlots.size());
		for (IRecipeSlotView slot : inputSlots) {
			if (!(slot instanceof IRecipeSlotDrawable drawable)) {
				return false;
			}
			positionedSlots.add(new PositionedSlot(slot, getSlotRect(drawable)));
		}
		List<Integer> xs = distinctSortedPositions(positionedSlots.stream()
			.map(slot -> slot.rect.getX())
			.toList());
		List<Integer> ys = distinctSortedPositions(positionedSlots.stream()
			.map(slot -> slot.rect.getY())
			.toList());
		if (xs.size() > columns || ys.size() > columns) {
			return false;
		}
		List<Integer> targetIndexes = new ArrayList<>(positionedSlots.size());
		for (PositionedSlot positionedSlot : positionedSlots) {
			int column = xs.indexOf(positionedSlot.rect.getX());
			int row = ys.indexOf(positionedSlot.rect.getY());
			if (column < 0 || row < 0) {
				return false;
			}
			targetIndexes.add(row * columns + column);
		}
		for (int i = 0; i < positionedSlots.size(); i++) {
			candidateSlots.set(targetIndexes.get(i), createCandidates(positionedSlots.get(i).slot, groups));
		}
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] GRIDFILL-POSITIONED xs={} ys={} targetIndexes={}",
				xs, ys, targetIndexes);
		}
		return true;
	}

	@SuppressWarnings("removal")
	private static Rect2i getSlotRect(IRecipeSlotDrawable slot) {
		return slot.getRect();
	}

	private static List<Integer> distinctSortedPositions(List<Integer> positions) {
		return positions.stream()
			.distinct()
			.sorted(Comparator.naturalOrder())
			.toList();
	}

	private static List<Candidate> createCandidates(IRecipeSlotView slot, List<IngredientGroup> groups) {
		List<Candidate> candidates = new ArrayList<>();
		for (ItemStack stack : orderedCandidates(slot)) {
			IngredientGroup group = findOrCreateGroup(groups, stack);
			group.recipeAmount += stack.getCount();
			candidates.add(new Candidate(stack.copy(), group));
		}
		return candidates;
	}

	private static List<ItemStack> orderedCandidates(IRecipeSlotView slot) {
		List<ItemStack> candidates = new ArrayList<>();
		slot.getDisplayedItemStack()
			.filter(stack -> !stack.isEmpty())
			.ifPresent(stack -> addCandidate(candidates, stack));
		slot.getItemStacks()
			.filter(stack -> !stack.isEmpty())
			.forEach(stack -> addCandidate(candidates, stack));
		return candidates;
	}

	private static void addCandidate(List<ItemStack> candidates, ItemStack stack) {
		boolean duplicate = candidates.stream()
			.anyMatch(candidate -> CraftingStackMatcher.matchesExactStack(candidate, stack));
		if (!duplicate) {
			candidates.add(stack.copy());
		}
	}

	private static IngredientGroup findOrCreateGroup(List<IngredientGroup> groups, ItemStack stack) {
		for (IngredientGroup group : groups) {
			if (CraftingStackMatcher.matchesIngredientTemplate(group.template, stack)) {
				return group;
			}
		}
		IngredientGroup group = new IngredientGroup(stack);
		groups.add(group);
		return group;
	}

	private static void findInventoryQuantities(List<IngredientGroup> groups, List<ItemStack> availableStacks) {
		for (ItemStack available : availableStacks) {
			if (available.isEmpty()) {
				continue;
			}
			IngredientGroup group = findGroup(groups, available);
			if (group != null) {
				group.inventoryAmount += available.getCount();
				if (group.representative.isEmpty()) {
					group.representative = available.copy();
					group.representative.setCount(group.template.getCount());
				}
			}
		}
	}

	private static IngredientGroup findGroup(List<IngredientGroup> groups, ItemStack stack) {
		for (IngredientGroup group : groups) {
			if (CraftingStackMatcher.matchesIngredientTemplate(group.template, stack)) {
				return group;
			}
		}
		return null;
	}

	private static ItemStack selectTargetStack(List<Candidate> candidates) {
		if (candidates.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Candidate bestCandidate = null;
		double bestScore = -1;
		for (Candidate candidate : candidates) {
			int required = candidate.stack.getCount();
			IngredientGroup group = candidate.group;
			int remaining = group.inventoryAmount - group.distributed;
			if (required <= 0 || remaining < required || group.recipeAmount <= 0) {
				continue;
			}
			double score = (double) remaining / required;
			if (score > bestScore) {
				bestScore = score;
				bestCandidate = candidate;
			}
		}
		if (bestCandidate == null) {
			return candidates.get(0).stack.copy();
		}
		bestCandidate.group.distributed += bestCandidate.stack.getCount();
		bestCandidate.group.distributedSlots++;
		ItemStack stack = bestCandidate.group.representative.isEmpty() ?
			bestCandidate.stack.copy() :
			bestCandidate.group.representative.copy();
		stack.setCount(bestCandidate.stack.getCount());
		return stack;
	}

	private static int calculateRecipeQuantity(List<IngredientGroup> groups, int multiplier) {
		int requested = multiplier == 0 ? 64 : Math.max(1, multiplier);
		int quantity = Integer.MAX_VALUE;
		for (IngredientGroup group : groups) {
			if (group.distributed <= 0) {
				continue;
			}
			if (group.distributedSlots <= 0) {
				return 0;
			}
			int slotCapacity = group.distributedSlots * group.template.getMaxStackSize();
			int available = Math.min(group.inventoryAmount, slotCapacity);
			quantity = Math.min(quantity, available / group.distributed);
		}
		if (quantity == Integer.MAX_VALUE) {
			return multiplier;
		}
		return Math.min(requested, quantity);
	}

	private record Candidate(ItemStack stack, IngredientGroup group) {
	}

	private record PositionedSlot(IRecipeSlotView slot, Rect2i rect) {
	}

	private static final class IngredientGroup {
		private final ItemStack template;
		private ItemStack representative = ItemStack.EMPTY;
		private int inventoryAmount;
		private int distributed;
		private int distributedSlots;
		private int recipeAmount;

		private IngredientGroup(ItemStack template) {
			this.template = template.copy();
			this.template.setCount(template.getCount());
		}
	}
}
