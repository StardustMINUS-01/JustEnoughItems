package mezz.jei.neoforge.network;

import mezz.jei.common.Internal;
import mezz.jei.common.config.IServerConfig;
import mezz.jei.common.network.ClientPacketContext;
import mezz.jei.common.network.IConnectionToClient;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.ServerPacketContext;
import mezz.jei.common.network.packets.PacketCheatPermission;
import mezz.jei.common.network.packets.PacketCraftingGridCraft;
import mezz.jei.common.network.packets.PacketCraftingGridCraftAck;
import mezz.jei.common.network.packets.PacketDeletePlayerItem;
import mezz.jei.common.network.packets.PacketFastPickupItemStack;
import mezz.jei.common.network.packets.PacketFillCraftingGrid;
import mezz.jei.common.network.packets.PacketGiveItemStack;
import mezz.jei.common.network.packets.PacketPullBookmarkItems;
import mezz.jei.common.network.packets.PacketRecipeTransfer;
import mezz.jei.common.network.packets.PacketRecipeTransferCounted;
import mezz.jei.common.network.packets.PacketRequestCheatPermission;
import mezz.jei.common.network.packets.PacketSetHotbarItemStack;
import mezz.jei.common.network.packets.PlayToClientPacket;
import mezz.jei.common.network.packets.PlayToServerPacket;
import mezz.jei.neoforge.compat.CompatUtil;
import mezz.jei.neoforge.events.PermanentEventSubscriptions;
import mezz.jei.neoforge.compat.ae2.patternencoding.PacketEncodeRecipeChainPatterns;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.BiConsumer;

public class NetworkHandler {
	private final String protocolVersion;
	private final IServerConfig serverConfig;
	private final IConnectionToServer connectionToServer;
	private final IConnectionToClient connectionToClient;

	public NetworkHandler(String protocolVersion, IServerConfig serverConfig) {
		this.protocolVersion = protocolVersion;
		this.serverConfig = serverConfig;

		this.connectionToServer = new ConnectionToServer();
		Internal.setServerConnection(this.connectionToServer);
		this.connectionToClient = new ConnectionToClient();
	}

	public void registerPacketHandlers(PermanentEventSubscriptions subscriptions) {
		subscriptions.register(RegisterPayloadHandlersEvent.class, ev -> {
			var registrar = ev.registrar(this.protocolVersion)
			.executesOn(HandlerThread.MAIN)
			.optional()
			.playToServer(PacketDeletePlayerItem.TYPE, PacketDeletePlayerItem.STREAM_CODEC, wrapServerHandler(PacketDeletePlayerItem::process))
			.playToServer(PacketGiveItemStack.TYPE, PacketGiveItemStack.STREAM_CODEC, wrapServerHandler(PacketGiveItemStack::process))
			.playToServer(PacketFastPickupItemStack.TYPE, PacketFastPickupItemStack.STREAM_CODEC, wrapServerHandler(PacketFastPickupItemStack::process))
			.playToServer(PacketRecipeTransfer.TYPE, PacketRecipeTransfer.STREAM_CODEC, wrapServerHandler(PacketRecipeTransfer::process))
			.playToServer(PacketRecipeTransferCounted.TYPE, PacketRecipeTransferCounted.STREAM_CODEC, wrapServerHandler(PacketRecipeTransferCounted::process))
			.playToServer(PacketSetHotbarItemStack.TYPE, PacketSetHotbarItemStack.STREAM_CODEC, wrapServerHandler(PacketSetHotbarItemStack::process))
			.playToServer(PacketRequestCheatPermission.TYPE, PacketRequestCheatPermission.STREAM_CODEC, wrapServerHandler(PacketRequestCheatPermission::process))
			.playToServer(PacketPullBookmarkItems.TYPE, PacketPullBookmarkItems.STREAM_CODEC, wrapServerHandler(PacketPullBookmarkItems::process))
			.playToServer(PacketFillCraftingGrid.TYPE, PacketFillCraftingGrid.STREAM_CODEC, wrapServerHandler(PacketFillCraftingGrid::process))
			.playToServer(PacketCraftingGridCraft.TYPE, PacketCraftingGridCraft.STREAM_CODEC, wrapServerHandler(PacketCraftingGridCraft::process))
			.playToClient(PacketCheatPermission.TYPE, PacketCheatPermission.STREAM_CODEC, wrapClientHandler(PacketCheatPermission::process))
			.playToClient(PacketCraftingGridCraftAck.TYPE, PacketCraftingGridCraftAck.STREAM_CODEC, wrapClientHandler(PacketCraftingGridCraftAck::process));
			if (CompatUtil.isModLoaded("ae2")) {
				registrar.playToServer(PacketEncodeRecipeChainPatterns.TYPE, PacketEncodeRecipeChainPatterns.STREAM_CODEC, wrapServerHandler(PacketEncodeRecipeChainPatterns::process));
			}
		});
	}

	private <T extends PlayToClientPacket<T>> IPayloadHandler<T> wrapClientHandler(BiConsumer<T, ClientPacketContext> consumer) {
		return (t, payloadContext) -> {
			LocalPlayer player = (LocalPlayer) payloadContext.player();
			var clientPacketContext = new ClientPacketContext(player, connectionToServer);
			payloadContext.enqueueWork(() -> {
				consumer.accept(t, clientPacketContext);
			});
		};
	}

	private <T extends PlayToServerPacket<T>> IPayloadHandler<T> wrapServerHandler(BiConsumer<T, ServerPacketContext> consumer) {
		return (t, payloadContext) -> {
			ServerPlayer player = (ServerPlayer) payloadContext.player();
			var serverPacketContext = new ServerPacketContext(player, serverConfig, connectionToClient);
			payloadContext.enqueueWork(() -> {
				consumer.accept(t, serverPacketContext);
			});
		};
	}

	public IConnectionToServer getConnectionToServer() {
		return connectionToServer;
	}
}
