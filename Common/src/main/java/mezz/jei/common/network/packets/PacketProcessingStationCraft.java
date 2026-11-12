package mezz.jei.common.network.packets;

import mezz.jei.common.bookmarks.ServerProcessingStationCraft;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class PacketProcessingStationCraft extends PlayToServerPacket<PacketProcessingStationCraft> {
	public static final Type<PacketProcessingStationCraft> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jei", "processing_station_craft"));
	private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> INPUTS = ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(64));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketProcessingStationCraft> STREAM_CODEC = StreamCodec.ofMember(PacketProcessingStationCraft::write, PacketProcessingStationCraft::read);
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
		this.multiplier = multiplier == 0 ? 64 : Math.clamp(multiplier, 1, 64);
		this.inputs = inputs.stream().map(ItemStack::copy).toList();
		this.output = output.copy();
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(containerId);
		buffer.writeVarInt(taskId);
		buffer.writeVarInt(requestId);
		buffer.writeResourceLocation(recipeType);
		ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buffer, recipeId);
		buffer.writeVarInt(multiplier);
		INPUTS.encode(buffer, inputs);
		ItemStack.STREAM_CODEC.encode(buffer, output);
	}

	private static PacketProcessingStationCraft read(RegistryFriendlyByteBuf buffer) {
		return new PacketProcessingStationCraft(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readResourceLocation(),
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buffer), buffer.readVarInt(), INPUTS.decode(buffer), ItemStack.STREAM_CODEC.decode(buffer));
	}

	public int containerId() { return containerId; }
	public int taskId() { return taskId; }
	public int requestId() { return requestId; }
	public ResourceLocation recipeType() { return recipeType; }
	public Optional<ResourceLocation> recipeId() { return recipeId; }
	public int multiplier() { return multiplier; }
	public List<ItemStack> inputs() { return inputs; }
	public ItemStack output() { return output; }
	@Override
	public Type<PacketProcessingStationCraft> type() { return TYPE; }
	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketProcessingStationCraft> streamCodec() { return STREAM_CODEC; }
	@Override
	public void process(ServerPacketContext context) { ServerProcessingStationCraft.start(context.player(), this, context.connection()); }
}
