package mezz.jei.forge.compat.tconstruct;

import mezz.jei.common.bookmarks.ICraftingGridFillExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import mezz.jei.common.config.DebugConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Fills the Tinkers workstation crafting grid (CraftingStation 3x3 or TinkerStation input slots).
 * Loaded only when Tinkers' Construct is installed.
 */
public class TinkerCraftingGridFillExecutor implements ICraftingGridFillExecutor {
	private static final Logger LOGGER = LogManager.getLogger();

	public static Optional<TinkerCraftingGridFillExecutor> createIfLoaded() {
		return mezz.jei.forge.compat.CompatUtil.createIfLoaded(
			"slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu",
			TinkerCraftingGridFillExecutor::new
		);
	}

	@Override
	public boolean canHandle(AbstractContainerMenu menu) {
		return TinkerCraftingGridAccess.find(menu).isPresent();
	}

	@Override
	public int fill(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
		AbstractContainerMenu menu = player.containerMenu;
		Optional<TinkerCraftingGridAccess> access = TinkerCraftingGridAccess.find(menu);
		if (access.isEmpty() || menu.containerId != containerId || targetStacks.isEmpty()) {
			return 0;
		}
		int filled = ServerBookmarkCraftingGridFill.fill(
			menu,
			containerId,
			player.getInventory(),
			player,
			targetStacks,
			multiplier,
			access.get().craftingSlots(),
			null
		);
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] COMPAT-TINKER-FILL containerId={} multiplier={} filled={}", containerId, multiplier, filled);
		}
		return filled;
	}
}
