/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Portions of this file are adapted from GTNewHorizons NotEnoughItems:
 * https://github.com/GTNewHorizons/NotEnoughItems
 *
 * GTNH NEI modifications copyright (c) 2019-2024 mitchej123 and the GTNH Team.
 */
package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.common.bookmarks.CraftingStackMatcher;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side fallback state machine for the vanilla part of GTNH NEI DefaultOverlayHandler.craft().
 * It is intentionally limited to one recipe step; chain fallback is a separate later phase.
 */
public final class ClientCraftingGridClickTask {
	// Safety timeout for the click state machine (clear/fill/wait/take/cleanup).
	// Lowered from 40 to 10 ticks (0.5s) to keep consecutive Shift+C activations responsive.
	private static final int DEFAULT_TIMEOUT_TICKS = 10;

	private final Environment environment;
	private final List<ItemStack> targetStacks;
	private final int timeoutTicks;
	private State state = State.CLEAR_GRID;
	private boolean complete;
	private boolean failed;
	private int ticks;

	public ClientCraftingGridClickTask(Environment environment, List<ItemStack> targetStacks) {
		this(environment, targetStacks, DEFAULT_TIMEOUT_TICKS);
	}

	public ClientCraftingGridClickTask(Environment environment, List<ItemStack> targetStacks, int timeoutTicks) {
		this.environment = environment;
		this.targetStacks = copyTargets(targetStacks);
		this.timeoutTicks = Math.max(1, timeoutTicks);
	}

	public boolean tick() {
		if (complete || failed) {
			return false;
		}
		if (!environment.stillValid() || !environment.carriedStackEmpty() || ++ticks > timeoutTicks) {
			return fail();
		}

		return switch (state) {
			case CLEAR_GRID -> clearGrid();
			case FILL_GRID -> fillGrid();
			case WAIT_RESULT -> waitForResult();
			case TAKE_RESULT -> takeResult();
			case CLEANUP_GRID -> cleanupGrid();
		};
	}

	public boolean isComplete() {
		return complete;
	}

	public boolean isFailed() {
		return failed;
	}

	private boolean clearGrid() {
		for (int i = 0; i < environment.craftingSlotCount(); i++) {
			if (!environment.getCraftingStack(i).isEmpty()) {
				return environment.clearCraftingSlot(i) || fail();
			}
		}
		state = State.FILL_GRID;
		return true;
	}

	private boolean fillGrid() {
		int slotCount = environment.craftingSlotCount();
		for (int i = 0; i < slotCount; i++) {
			ItemStack target = i < targetStacks.size() ? targetStacks.get(i) : ItemStack.EMPTY;
			ItemStack current = environment.getCraftingStack(i);
			if (target.isEmpty()) {
				if (!current.isEmpty()) {
					state = State.CLEAR_GRID;
				}
				continue;
			}
			if (!sameStackAndCount(current, target)) {
				return environment.fillCraftingSlot(i, target) || fail();
			}
		}
		state = State.WAIT_RESULT;
		return true;
	}

	private boolean waitForResult() {
		if (environment.hasResult()) {
			state = State.TAKE_RESULT;
		}
		return true;
	}

	private boolean takeResult() {
		if (!environment.takeResult()) {
			return fail();
		}
		state = State.CLEANUP_GRID;
		return true;
	}

	private boolean cleanupGrid() {
		for (int i = 0; i < environment.craftingSlotCount(); i++) {
			if (!environment.getCraftingStack(i).isEmpty()) {
				return environment.clearCraftingSlot(i) || fail();
			}
		}
		complete = true;
		return false;
	}

	private boolean fail() {
		failed = true;
		return false;
	}

	private static boolean sameStackAndCount(ItemStack first, ItemStack second) {
		if (first.isEmpty() || second.isEmpty()) {
			return first.isEmpty() && second.isEmpty();
		}
		return first.getCount() == second.getCount() && CraftingStackMatcher.matchesIngredientTemplate(second, first);
	}

	private static List<ItemStack> copyTargets(List<ItemStack> stacks) {
		List<ItemStack> copies = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			copies.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		return List.copyOf(copies);
	}

	private enum State {
		CLEAR_GRID,
		FILL_GRID,
		WAIT_RESULT,
		TAKE_RESULT,
		CLEANUP_GRID
	}

	public interface Environment {
		boolean stillValid();

		int craftingSlotCount();

		ItemStack getCraftingStack(int slotIndex);

		boolean carriedStackEmpty();

		boolean clearCraftingSlot(int slotIndex);

		boolean fillCraftingSlot(int slotIndex, ItemStack targetStack);

		boolean hasResult();

		boolean takeResult();
	}
}
