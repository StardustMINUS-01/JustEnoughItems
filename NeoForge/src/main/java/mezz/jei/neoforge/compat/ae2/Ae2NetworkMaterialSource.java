package mezz.jei.neoforge.compat.ae2;

import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.items.storage.ViewCellItem;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.util.prioritylist.IPartitionList;

/**
 * Extracts crafting ingredients from an AE2 terminal's network storage, charging AE power per item.
 * Loaded only when AE2 is installed.
 */
class Ae2NetworkMaterialSource implements ServerBookmarkCraftingGridFill.ExternalIngredientSource {
	private final CraftingTermMenu menu;
	private final MEStorage storage;
	private final IEnergySource energySource;
	private final IActionSource actionSource;
	private final @Nullable IPartitionList viewCellFilter;
	private final Level level;
	private final List<ItemStack> intendedItems;
	private @Nullable KeyCounter snapshot;
	private @Nullable RecipeHolder<CraftingRecipe> recipe;
	private @Nullable ItemStack output;

	Ae2NetworkMaterialSource(CraftingTermMenu menu, ServerPlayer player, List<ItemStack> targetStacks) {
		this.menu = menu;
		this.storage = menu.getHost().getInventory();
		this.energySource = menu.getEnergySource();
		this.actionSource = menu.getActionSource();
		this.viewCellFilter = ViewCellItem.createItemFilter(menu.getViewCells());
		this.level = player.level();
		this.intendedItems = targetStacks.stream()
			.map(ItemStack::copy)
			.toList();
	}

	@Override
	public ItemStack extract(int slotIndex, ItemStack template, int amount) {
		if (!menu.getLinkStatus().connected() || amount <= 0) {
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
		if (!menu.getLinkStatus().connected()) {
			return 0;
		}
		AEItemKey exactKey = AEItemKey.of(template);
		if (isListed(exactKey)) {
			long exact = getSnapshot().get(exactKey);
			if (exact > 0) {
				return exact;
			}
		}
		if (template.getComponents().isEmpty() && !template.isDamageableItem()) {
			return 0;
		}
		AEItemKey candidate = findFuzzyCandidate(slotIndex, template);
		return candidate == null ? 0 : getSnapshot().get(candidate);
	}

	private ItemStack extractExact(AEItemKey key, int amount) {
		if (!isListed(key)) {
			return ItemStack.EMPTY;
		}
		if (!chargePower(amount)) {
			return ItemStack.EMPTY;
		}
		long extracted = storage.extract(key, amount, Actionable.MODULATE, actionSource);
		if (extracted <= 0) {
			return ItemStack.EMPTY;
		}
		consumePower(extracted);
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
		RecipeHolder<CraftingRecipe> recipe = getRecipe();
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
			CraftingInput adjustedInput = CraftingInput.of(3, 3, adjustedItems);
			if (!recipe.value().matches(adjustedInput, level)) {
				continue;
			}
			if (!ItemStack.matches(recipe.value().assemble(adjustedInput, level.registryAccess()), output)) {
				continue;
			}
			return itemKey;
		}
		return null;
	}

	private @Nullable RecipeHolder<CraftingRecipe> getRecipe() {
		if (recipe == null) {
			CraftingInput intendedInput = CraftingInput.of(3, 3, intendedItems);
			recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, intendedInput, level).orElse(null);
			if (recipe != null) {
				output = recipe.value().assemble(intendedInput, level.registryAccess());
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

	private boolean chargePower(long amount) {
		return energySource.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG) >= amount;
	}

	private void consumePower(long amount) {
		energySource.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
	}
}
