package mezz.jei.neoforge.compat.ae2;

import mezz.jei.common.bookmarks.ICraftingGridCraftExecutor;
import mezz.jei.common.bookmarks.ServerBookmarkCraftingGridFill;
import mezz.jei.neoforge.compat.CompatUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.CraftingTermMenu;

/**
 * Optional auto-crafting executor for AE2 crafting terminals and wireless crafting terminals.
 * Loaded only when AE2 is installed.
 */
public class Ae2CraftingGridCraftExecutor implements ICraftingGridCraftExecutor {
	private static final int MAX_MULTIPLIER = 64;

	private final CraftingAccess craftingAccess;

	public static Optional<Ae2CraftingGridCraftExecutor> createIfLoaded() {
		return CompatUtil.createIfLoaded(
			"appeng.menu.me.items.CraftingTermMenu",
			() -> new Ae2CraftingGridCraftExecutor(new DirectCraftingAccess())
		);
	}

	private Ae2CraftingGridCraftExecutor(CraftingAccess craftingAccess) {
		this.craftingAccess = craftingAccess;
	}

	@Override
	public boolean canHandle(AbstractContainerMenu menu) {
		return craftingAccess.canHandle(menu);
	}

	@Override
	public int craft(ServerPlayer player, int containerId, @Nullable ResourceLocation recipeId, List<ItemStack> targetStacks, int multiplier) {
		return craftingAccess.craft(player, containerId, targetStacks, multiplier);
	}

	private interface CraftingAccess {
		boolean canHandle(AbstractContainerMenu menu);

		int craft(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier);
	}

	private static final class DirectCraftingAccess implements CraftingAccess {
		@Override
		public boolean canHandle(AbstractContainerMenu menu) {
			return menu instanceof CraftingTermMenu;
		}

		@Override
		public int craft(ServerPlayer player, int containerId, List<ItemStack> targetStacks, int multiplier) {
			AbstractContainerMenu menu = player.containerMenu;
			if (!(menu instanceof CraftingTermMenu craftingMenu) || menu.containerId != containerId || targetStacks.isEmpty()) {
				return 0;
			}
			if (!clearGridToNetwork(craftingMenu)) {
				player.displayClientMessage(Component.translatable("jei.ae2.crafting.grid_full"), false);
				return 0;
			}
			ServerBookmarkCraftingGridFill.ExternalIngredientSource networkSource = craftingMenu.getLinkStatus().connected()
				? new Ae2NetworkMaterialSource(craftingMenu, player, targetStacks)
				: null;

			List<Slot> craftingSlots = craftingMenu.getSlots(SlotSemantics.CRAFTING_GRID);
			List<Slot> resultSlots = craftingMenu.getSlots(SlotSemantics.CRAFTING_RESULT);
			if (craftingSlots.isEmpty() || resultSlots.isEmpty()) {
				return 0;
			}
			Slot resultSlot = resultSlots.getFirst();
			if (!(resultSlot instanceof IJeiCraftingTermSlotExtension extension)) {
				player.displayClientMessage(Component.translatable("jei.ae2.crafting.unsupported"), false);
				return 0;
			}

			int remaining = multiplier == 0 ? MAX_MULTIPLIER : Math.min(MAX_MULTIPLIER, Math.max(1, multiplier));
			int crafted = 0;
			while (remaining > 0) {
				int filled = ServerBookmarkCraftingGridFill.fill(
					menu,
					containerId,
					player.getInventory(),
					player,
					targetStacks,
					remaining,
					craftingSlots,
					networkSource
				);
				if (filled <= 0) {
					break;
				}
				menu.slotsChanged(player.getInventory());
				int done = extension.jei$craftBatch(menu, player, resultSlot.getItem(), Math.min(filled, remaining));
				if (done <= 0) {
					return crafted;
				}
				crafted += done;
				remaining -= done;
				menu.broadcastChanges();
			}
			if (crafted > 0) {
				player.getInventory().setChanged();
				menu.broadcastChanges();
			}
			return crafted;
		}

		private static boolean clearGridToNetwork(CraftingTermMenu menu) {
			InternalInventory grid = menu.getCraftingMatrix();
			boolean hasItems = false;
			for (int i = 0; i < grid.size(); i++) {
				if (!grid.getStackInSlot(i).isEmpty()) {
					hasItems = true;
					break;
				}
			}
			if (!hasItems) {
				return true;
			}

			MEStorage storage = menu.getHost().getInventory();
			IActionSource actionSource = menu.getActionSource();
			for (int i = 0; i < grid.size(); i++) {
				ItemStack stack = grid.getStackInSlot(i);
				if (stack.isEmpty()) {
					continue;
				}
				AEItemKey key = AEItemKey.of(stack);
				long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, actionSource);
				if (inserted < stack.getCount()) {
					grid.setItemDirect(i, stack.copyWithCount((int) (stack.getCount() - inserted)));
					return false;
				}
				grid.setItemDirect(i, ItemStack.EMPTY);
			}
			menu.slotsChanged(grid.toContainer());
			return true;
		}

	}
}
