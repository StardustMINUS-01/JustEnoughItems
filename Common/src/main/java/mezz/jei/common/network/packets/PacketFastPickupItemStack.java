package mezz.jei.common.network.packets;

import mezz.jei.common.network.IPacketId;
import mezz.jei.common.network.PacketIdServer;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.ServerPacketData;
import mezz.jei.common.util.ServerCommandUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.CompletableFuture;

public class PacketFastPickupItemStack extends PacketJei {
	private final ItemStack itemStack;

	public PacketFastPickupItemStack(ItemStack itemStack) {
		this.itemStack = itemStack;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.FAST_PICKUP_ITEM;
	}

	@Override
	public void writePacketData(FriendlyByteBuf buf) {
		buf.writeItem(itemStack);
	}

	public static CompletableFuture<Void> readPacketData(ServerPacketData data) {
		FriendlyByteBuf buf = data.buf();
		ItemStack itemStack = buf.readItem();
		ServerPacketContext context = data.context();
		ServerPlayer player = context.player();
		MinecraftServer server = player.server;
		return server.submit(() -> ServerCommandUtil.executeFastPickup(context, itemStack));
	}
}
