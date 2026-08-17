package mezz.jei.forge.compat.sophisticated;

import mezz.jei.common.bookmarks.ICraftingGridCraftExecutor;
import mezz.jei.common.config.DebugConfig;
import mezz.jei.forge.compat.CompatUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Optional auto-crafting executor for Sophisticated Backpacks / Storage crafting upgrades.
 * Loaded only when SophisticatedCore is installed.
 */
public class SophisticatedCraftingGridCraftExecutor implements ICraftingGridCraftExecutor {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MAX_MULTIPLIER = 64;

	public static Optional<SophisticatedCraftingGridCraftExecutor> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase",
			SophisticatedCraftingGridCraftExecutor::new
		);
	}

	private SophisticatedCraftingGridCraftExecutor() {
	}

	@Override
	public boolean canHandle(AbstractContainerMenu menu) {
		return SophisticatedCraftingGridAccess.find(menu).isPresent();
	}

	@Override
	public int craft(ServerPlayer player, int containerId, @Nullable ResourceLocation recipeId, List<ItemStack> targetStacks, int multiplier) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu.containerId != containerId || targetStacks.isEmpty()) {
			return 0;
		}
		Optional<SophisticatedCraftingGridAccess> optionalAccess = SophisticatedCraftingGridAccess.find(menu);
		if (optionalAccess.isEmpty()) {
			return 0;
		}
		SophisticatedCraftingGridAccess access = optionalAccess.get();
		access.ensureOpen();

		ResourceLocation resolvedRecipeId = recipeId != null ? recipeId : SophisticatedCraftingGridAccess.resolveRecipeId(player, targetStacks);
		if (resolvedRecipeId == null) {
			return 0;
		}
		access.fillGrid(player, resolvedRecipeId, normalizeTargetStacks(targetStacks));
		menu.slotsChanged(player.getInventory());

		Slot resultSlot = access.getResultSlot();
		int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
		int crafted = 0;
		while (remaining > 0) {
			if (resultSlot.getItem().isEmpty()) {
				break;
			}
			ItemStack moved = menu.quickMoveStack(player, resultSlot.index);
			if (moved.isEmpty()) {
				// The result could not be merged into storage/player inventory; stop and keep the grid as-is.
				break;
			}
			crafted++;
			remaining--;
		}
		if (crafted > 0) {
			player.getInventory().setChanged();
			menu.broadcastChanges();
		}
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] COMPAT-SOPHISTICATED-CRAFT containerId={} multiplier={} crafted={}", containerId, multiplier, crafted);
		}
		return crafted;
	}

	private static List<ItemStack> normalizeTargetStacks(List<ItemStack> targetStacks) {
		ItemStack[] stacks = new ItemStack[SophisticatedCraftingGridAccess.GRID_SIZE];
		Arrays.fill(stacks, ItemStack.EMPTY);
		for (int i = 0; i < SophisticatedCraftingGridAccess.GRID_SIZE && i < targetStacks.size(); i++) {
			stacks[i] = targetStacks.get(i);
		}
		return Arrays.asList(stacks);
	}
}
