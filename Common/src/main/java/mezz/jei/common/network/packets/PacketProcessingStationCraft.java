package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.ServerProcessingStationCraft;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;

public final class PacketProcessingStationCraft extends PacketJei {
	public static final ResourceLocation CHANNEL = new ResourceLocation("jei", "processing_station_craft");
	private static final int MAX_INPUTS = 64;
	private final int containerId;
	private final int taskId;
	private final int requestId;
	private final ResourceLocation recipeType;
	private final Optional<ResourceLocation> recipeId;
	private final int multiplier;
	private final List<ItemStack> inputs;
	private final ItemStack output;

	public PacketProcessingStationCraft(int containerId, int taskId, int requestId, ResourceLocation recipeType, Optional<ResourceLocation> recipeId, int multiplier, List<ItemStack> inputs, ItemStack output) {
		this.containerId = containerId;
		this.taskId = taskId;
		this.requestId = requestId;
		this.recipeType = recipeType;
		this.recipeId = recipeId;
		this.multiplier = multiplier == 0 ? 64 : Math.max(1, Math.min(multiplier, 64));
		this.inputs = inputs.stream().limit(MAX_INPUTS).map(ItemStack::copy).toList();
		this.output = output.copy();
	}

	@Override
	public ResourceLocation getChannelId() {
		return CHANNEL;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.PROCESSING_STATION_CRAFT;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buffer) {
		buffer.writeVarInt(containerId);
		buffer.writeVarInt(taskId);
		buffer.writeVarInt(requestId);
		buffer.writeResourceLocation(recipeType);
		buffer.writeNullable(recipeId.orElse(null), FriendlyByteBuf::writeResourceLocation);
		buffer.writeVarInt(multiplier);
		buffer.writeVarInt(inputs.size());
		for (ItemStack input : inputs) {
			buffer.writeItem(input);
		}
		buffer.writeItem(output);
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		FriendlyByteBuf buffer = data.buf();
		int containerId = buffer.readVarInt();
		int taskId = buffer.readVarInt();
		int requestId = buffer.readVarInt();
		ResourceLocation recipeType = buffer.readResourceLocation();
		var recipeId = Optional.ofNullable(buffer.readNullable(FriendlyByteBuf::readResourceLocation));
		int multiplier = buffer.readVarInt();
		int count = buffer.readVarInt();
		if (count < 0 || count > MAX_INPUTS) {
			throw new IllegalArgumentException("Invalid processing input slot count: " + count);
		}
		List<ItemStack> inputs = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			inputs.add(buffer.readItem());
		}
		var request = new PacketProcessingStationCraft(containerId, taskId, requestId, recipeType, recipeId, multiplier, inputs, buffer.readItem());
		var context = data.context();
		return context.player().server.submit(() -> ServerProcessingStationCraft.start(context.player(), request, context.connection()));
	}

	public int containerId() { return containerId; }
	public int taskId() { return taskId; }
	public int requestId() { return requestId; }
	public ResourceLocation recipeType() { return recipeType; }
	public Optional<ResourceLocation> recipeId() { return recipeId; }
	public int multiplier() { return multiplier; }
	public List<ItemStack> inputs() { return inputs; }
	public ItemStack output() { return output; }
}
