package mezz.jei.common.network.packets;

import mezz.jei.api.constants.ModIds;
import mezz.jei.common.network.ClientPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public class PacketCraftingGridCraftAck extends PlayToClientPacket<PacketCraftingGridCraftAck> {
	public static final CustomPacketPayload.Type<PacketCraftingGridCraftAck> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "crafting_grid_craft_ack"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PacketCraftingGridCraftAck> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		p -> p.taskId,
		ByteBufCodecs.VAR_INT,
		p -> p.requestId,
		ByteBufCodecs.VAR_INT,
		p -> p.craftedCount,
		PacketCraftingGridCraftAck::new
	);
	private static Consumer<Ack> listener = ignored -> {
	};

	private final int taskId;
	private final int requestId;
	private final int craftedCount;

	public PacketCraftingGridCraftAck(int requestId, int craftedCount) {
		this(0, requestId, craftedCount);
	}

	public PacketCraftingGridCraftAck(int taskId, int requestId, int craftedCount) {
		this.taskId = Math.max(0, taskId);
		this.requestId = Math.max(0, requestId);
		this.craftedCount = Math.max(0, craftedCount);
	}

	public static void setListener(Consumer<Ack> listener) {
		PacketCraftingGridCraftAck.listener = listener == null ? ignored -> {
		} : listener;
	}

	@Override
	public Type<PacketCraftingGridCraftAck> type() {
		return TYPE;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, PacketCraftingGridCraftAck> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public void process(ClientPacketContext context) {
		listener.accept(new Ack(taskId, requestId, craftedCount));
	}

	public record Ack(int taskId, int requestId, int craftedCount) {
	}
}
