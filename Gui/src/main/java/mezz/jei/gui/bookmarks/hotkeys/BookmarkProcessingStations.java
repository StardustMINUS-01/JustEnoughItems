package mezz.jei.gui.bookmarks.hotkeys;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.common.bookmarks.ProcessingStations;
import mezz.jei.common.network.packets.PacketProcessingStationCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Optional;

public final class BookmarkProcessingStations {
	private static int activeContainerId = -1;
	private static int activeTaskId;
	private static int activeRequestId;

	public static void markDispatched(PacketProcessingStationCraft request) {
		activeContainerId = request.containerId();
		activeTaskId = request.taskId();
		activeRequestId = request.requestId();
	}

	public static void acknowledge(int taskId, int requestId) {
		if (activeTaskId == taskId && activeRequestId == requestId)
			activeContainerId = -1;
	}

	public static void cancel() {
		if (activeContainerId >= 0) {
			Internal.getServerConnection().sendPacketToServer(new mezz.jei.common.network.packets.PacketCancelProcessingStation(activeContainerId));
			activeContainerId = -1;
		}
	}
	private BookmarkProcessingStations() {}

	public static Optional<PacketProcessingStationCraft> createRequest(IRecipeLayoutDrawable<?> layout, AbstractContainerMenu menu, int multiplier, int taskId, int requestId, List<ItemStack> available) {
		return ProcessingStations.find(menu, layout.getRecipeCategory().getRecipeType().getUid()).flatMap(station -> {
			ItemStack output = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
				.flatMap(slot -> slot.getDisplayedItemStack().stream()).findFirst().orElse(ItemStack.EMPTY);
			if (output.isEmpty())
				return Optional.empty();
			List<ItemStack> inputs = new java.util.ArrayList<>(available);
			station.getInputSlots(menu).stream().map(slot -> slot.getItem()).filter(stack -> !stack.isEmpty()).forEach(inputs::add);
			return BookmarkCraftingGridFill.create(layout, station.getInputSlots(menu).size(), multiplier, inputs)
				.filter(fill -> fill.multiplier() > 0)
				.map(fill -> new PacketProcessingStationCraft(menu.containerId, taskId, requestId, station.getRecipeTypeId(),
					layout.getRecipe() instanceof RecipeHolder<?> holder ? Optional.of(holder.id()) : Optional.empty(), fill.multiplier(), fill.targetStacks(), output));
		});
	}

	public static Optional<PacketProcessingStationCraft> createCurrentRequest(IRecipeLayoutDrawable<?> layout, int containerId, int multiplier, int taskId, int requestId, List<ItemStack> available) {
		var player = Minecraft.getInstance().player;
		if (player == null || player.containerMenu.containerId != containerId ||
			!Internal.getServerConnection().canSendPacket(PacketProcessingStationCraft.TYPE))
			return Optional.empty();
		return createRequest(layout, player.containerMenu, multiplier, taskId, requestId, available);
	}
}
