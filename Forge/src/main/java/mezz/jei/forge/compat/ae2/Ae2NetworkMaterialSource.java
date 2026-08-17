package mezz.jei.forge.compat.ae2;

import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.items.storage.ViewCellItem;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.util.prioritylist.IPartitionList;

/**
 * Extracts crafting ingredients from an AE2 terminal's network storage. 1.20.1 adaptation of the
 * JEI 1.21.1 implementation (no CraftingInput/RecipeHolder API, no menu energy-source access).
 * Loaded only when AE2 is installed.
 */
class Ae2NetworkMaterialSource implements ServerBookmarkCraftingGridFill.ExternalIngredientSource {
	private final CraftingTermMenu menu;
	private final MEStorage storage;
	private final IActionSource actionSource;
	private final @Nullable IPartitionList viewCellFilter;
	private final Level level;
	private final List<ItemStack> intendedItems;
	private @Nullable KeyCounter snapshot;
	private @Nullable CraftingRecipe recipe;
	private @Nullable ItemStack output;

	Ae2NetworkMaterialSource(CraftingTermMenu menu, ServerPlayer player, List<ItemStack> targetStacks) {
		this.menu = menu;
		this.storage = menu.getHost().getInventory();
		this.actionSource = menu.getActionSource();
		this.viewCellFilter = ViewCellItem.createItemFilter(menu.getViewCells());
		this.level = player.level();
		this.intendedItems = targetStacks.stream()
			.map(ItemStack::copy)
			.toList();
	}

	@Override
	public ItemStack extract(int slotIndex, ItemStack template, int amount) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack exact = extractExact(AEItemKey.of(template), amount);
		if (!exact.isEmpty()) {
			return exact;
		}
		return extractFuzzy(slotIndex, template, amount);
	}

	@Override
	public long countAvailable(int slotIndex, ItemStack template) {
		AEItemKey exactKey = AEItemKey.of(template);
		if (isListed(exactKey)) {
			long exact = getSnapshot().get(exactKey);
			if (exact > 0) {
				return exact;
			}
		}
		AEItemKey candidate = findFuzzyCandidate(slotIndex, template);
		return candidate == null ? 0 : getSnapshot().get(candidate);
	}

	private ItemStack extractExact(AEItemKey key, int amount) {
		if (!isListed(key)) {
			return ItemStack.EMPTY;
		}
		long extracted = storage.extract(key, amount, Actionable.MODULATE, actionSource);
		if (extracted <= 0) {
			return ItemStack.EMPTY;
		}
		return key.toStack((int) extracted);
	}

	private ItemStack extractFuzzy(int slotIndex, ItemStack template, int amount) {
		AEItemKey candidate = findFuzzyCandidate(slotIndex, template);
		if (candidate == null) {
			return ItemStack.EMPTY;
		}
		return extractExact(candidate, amount);
	}

	private @Nullable AEItemKey findFuzzyCandidate(int slotIndex, ItemStack template) {
		CraftingRecipe recipe = getRecipe();
		if (recipe == null || output == null) {
			return null;
		}
		for (var entry : getSnapshot()) {
			if (!(entry.getKey() instanceof AEItemKey itemKey)) {
				continue;
			}
			if (itemKey.getItem() != template.getItem() || itemKey.matches(output) || !isListed(itemKey)) {
				continue;
			}
			List<ItemStack> adjustedItems = new ArrayList<>(intendedItems);
			adjustedItems.set(slotIndex, itemKey.toStack(Math.max(1, intendedItems.get(slotIndex).getCount())));
			TransientCraftingContainer adjustedInput = new TransientCraftingContainer(menu, 3, 3);
			for (int i = 0; i < adjustedItems.size() && i < 9; i++) {
				adjustedInput.setItem(i, adjustedItems.get(i));
			}
			if (!recipe.matches(adjustedInput, level)) {
				continue;
			}
			if (!ItemStack.matches(recipe.assemble(adjustedInput, level.registryAccess()), output)) {
				continue;
			}
			return itemKey;
		}
		return null;
	}

	private @Nullable CraftingRecipe getRecipe() {
		if (recipe == null) {
			TransientCraftingContainer intendedInput = new TransientCraftingContainer(menu, 3, 3);
			for (int i = 0; i < intendedItems.size() && i < 9; i++) {
				intendedInput.setItem(i, intendedItems.get(i));
			}
			Optional<CraftingRecipe> optionalRecipe = level.getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, intendedInput, level);
			if (optionalRecipe.isPresent()) {
				recipe = optionalRecipe.get();
				output = recipe.assemble(intendedInput, level.registryAccess());
			}
		}
		return recipe;
	}

	private KeyCounter getSnapshot() {
		if (snapshot == null) {
			snapshot = storage.getAvailableStacks();
		}
		return snapshot;
	}

	private boolean isListed(AEItemKey key) {
		// An empty partition list means "no filter" and allows everything.
		return viewCellFilter == null || viewCellFilter.isEmpty() || viewCellFilter.isListed(key);
	}
}
