package mezz.jei.common.bookmarks;

import mezz.jei.api.recipe.transfer.IProcessingStation;
import mezz.jei.common.network.IConnectionToClient;
import mezz.jei.common.network.packets.PacketProcessingStationCraft;
import mezz.jei.common.network.packets.PacketCraftingGridCraftAck;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One active current-menu request per player; each server tick executes at most one recipe. */
public final class ServerProcessingStationCraft {
	private static final Map<ServerPlayer, Task> TASKS = new WeakHashMap<>();

	private ServerProcessingStationCraft() {}

	public static void start(ServerPlayer player, PacketProcessingStationCraft request, IConnectionToClient connection) {
		cancel(player);
		AbstractContainerMenu menu = player.containerMenu;
		if (menu.containerId != request.containerId() || !menu.stillValid(player) || !menu.getCarried().isEmpty())
			return;
		ProcessingStations.find(menu, request.recipeType()).ifPresent(station -> {
			if (request.inputs().size() == station.getInputSlots(menu).size() && !request.output().isEmpty()) {
				TASKS.put(player, new Task(menu, request, station, connection));
			}
		});
	}

	public static void cancel(ServerPlayer player) { TASKS.remove(player); }

	public static void tick(ServerPlayer player) {
		Task task = TASKS.get(player);
		if (task == null)
			return;
		if (player.containerMenu != task.menu || !task.menu.stillValid(player) || !task.menu.getCarried().isEmpty() || !task.matchesSlots()) {
			TASKS.remove(player);
			return;
		}
		boolean success = task.craft(player);
		if (success)
			task.completed++;
		task.menu.broadcastChanges();
		if (!success || task.completed >= task.request.multiplier() || !task.menu.stillValid(player)) {
			TASKS.remove(player);
			task.connection.sendPacketToClient(new PacketCraftingGridCraftAck(task.request.taskId(), task.request.requestId(), task.completed), player);
		} else {
			task.captureSlots();
		}
	}

	private static final class Task {
		final AbstractContainerMenu menu;
		final PacketProcessingStationCraft request;
		final IProcessingStation station;
		final IConnectionToClient connection;
		List<ItemStack> slots;
		int completed;

		Task(AbstractContainerMenu menu, PacketProcessingStationCraft request, IProcessingStation station, IConnectionToClient connection) {
			this.menu = menu;
			this.request = request;
			this.station = station;
			this.connection = connection;
			captureSlots();
		}

		void captureSlots() { slots = menu.slots.stream().map(s -> s.getItem().copy()).toList(); }

		boolean matchesSlots() {
			if (slots.size() != menu.slots.size())
				return false;
			for (int i = 0; i < slots.size(); i++) {
				if (!ItemStack.matches(slots.get(i), menu.getSlot(i).getItem()))
					return false;
			}
			return true;
		}

		boolean craft(ServerPlayer player) {
			if (ServerBookmarkCraftingGridFill.fill(menu, menu.containerId, player.getInventory(), player,
				request.inputs(), 1, station.getInputSlots(menu), null) <= 0 ||
				!station.selectRecipe(player, request.recipeId().orElse(null)))
				return false;
			Slot resultSlot = station.getResultSlot(menu);
			ItemStack result = resultSlot.getItem();
			List<Slot> inventory = ServerBookmarkCraftingGridFill.getPlayerSlots(menu, player.getInventory());
			if (!station.matchesOutput(request.output(), result) || !resultSlot.mayPickup(player) ||
				!ServerBookmarkCraftingGridFill.canInsert(inventory, result))
				return false;
			// PICKUP takes one output stack and invokes the menu's vanilla consumption/experience callbacks.
			menu.clicked(resultSlot.index, 0, ClickType.PICKUP, player);
			if (menu.getCarried().isEmpty())
				return false;
			for (Slot slot : inventory) {
				ItemStack carried = menu.getCarried();
				if (carried.isEmpty())
					break;
				if (slot.mayPlace(carried) && (slot.getItem().isEmpty() ||
					ItemStack.isSameItemSameTags(slot.getItem(), carried) && slot.getItem().getCount() < slot.getMaxStackSize(carried))
				) {
					menu.clicked(slot.index, 0, ClickType.PICKUP, player);
				}
			}
			return menu.getCarried().isEmpty();
		}
	}
}
