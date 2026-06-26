/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JEI-side equivalent of GTNH NEI's DefaultOverlayRenderer state.
 * It only records ghost items for empty target slots; item movement belongs to the fill bridge.
 */
public record BookmarkGhostOverlay(List<Entry> entries) {
	public BookmarkGhostOverlay {
		entries = List.copyOf(entries);
	}

	public static Optional<BookmarkGhostOverlay> create(
		BookmarkRecipeOverlayPlan plan,
		IRecipeLayoutDrawable<?> recipeLayout,
		List<TargetSlot> targetSlots
	) {
		Objects.requireNonNull(recipeLayout, "recipeLayout");
		List<IRecipeSlotView> inputSlots = recipeLayout.getRecipeSlotsView()
			.getSlotViews(RecipeIngredientRole.INPUT);
		return create(plan, inputSlots, targetSlots);
	}

	public static Optional<BookmarkGhostOverlay> create(
		BookmarkRecipeOverlayPlan plan,
		List<IRecipeSlotView> recipeSlots,
		List<TargetSlot> targetSlots
	) {
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(recipeSlots, "recipeSlots");
		Objects.requireNonNull(targetSlots, "targetSlots");
		if (plan.kind() != BookmarkRecipeOverlayPlan.Kind.GHOST_OVERLAY) {
			return Optional.empty();
		}

		List<IRecipeSlotView> inputSlots = recipeSlots.stream()
			.filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT)
			.toList();
		int slotCount = Math.min(inputSlots.size(), targetSlots.size());
		List<Entry> entries = new ArrayList<>(slotCount);
		for (int i = 0; i < slotCount; i++) {
			TargetSlot targetSlot = targetSlots.get(i);
			if (!targetSlot.isEmpty()) {
				continue;
			}
			inputSlots.get(i)
				.getDisplayedItemStack()
				.filter(stack -> !stack.isEmpty())
				.map(ItemStack::copy)
				.map(stack -> new Entry(stack, targetSlot.x(), targetSlot.y()))
				.ifPresent(entries::add);
		}
		return Optional.of(new BookmarkGhostOverlay(entries));
	}

	public boolean movesItems() {
		return false;
	}

	public record Entry(ItemStack itemStack, int x, int y) {
		public Entry {
			Objects.requireNonNull(itemStack, "itemStack");
			itemStack = itemStack.copy();
		}
	}

	public record TargetSlot(int x, int y, ItemStack currentStack) {
		public TargetSlot {
			currentStack = currentStack == null ? ItemStack.EMPTY : currentStack.copy();
		}

		public boolean isEmpty() {
			return currentStack.isEmpty();
		}
	}
}
