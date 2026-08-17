package mezz.jei.common.network.packets;

import mezz.jei.common.config.DebugConfig;
import mezz.jei.common.network.ClientPacketContext;
import mezz.jei.common.network.ClientPacketData;
import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PacketCraftingGridCraftAck extends PacketJei {
	private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
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
	public IPacketId getPacketId() {
		return PacketIdClient.CRAFTING_GRID_CRAFT_ACK;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeVarInt(taskId);
		buf.writeVarInt(requestId);
		buf.writeVarInt(craftedCount);
	}

	public static CompletableFuture<Void> readPacketData(ClientPacketData data) {
		FriendlyByteBuf buf = data.buf();
		int taskId = buf.readVarInt();
		int requestId = buf.readVarInt();
		int craftedCount = buf.readVarInt();
		if (DebugConfig.isDebugModeEnabled()) {
			LOGGER.info("[Bug6] PACKET-ACK-RECEIVED taskId={} requestId={} craftedCount={}", taskId, requestId, craftedCount);
		}
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.submit(() -> listener.accept(new Ack(taskId, requestId, craftedCount)));
	}

	public record Ack(int taskId, int requestId, int craftedCount) {
	}
}
