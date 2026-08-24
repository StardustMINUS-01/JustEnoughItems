package mezz.jei.common.network;

import mezz.jei.common.network.packets.IServerPacketHandler;
import mezz.jei.common.network.packets.PacketCraftingGridCraft;
import mezz.jei.common.network.packets.PacketDeletePlayerItem;
import mezz.jei.common.network.packets.PacketFastPickupItemStack;
import mezz.jei.common.network.packets.PacketFillCraftingGrid;
import mezz.jei.common.network.packets.PacketGiveItemStack;
import mezz.jei.common.network.packets.PacketPullBookmarkItems;
import mezz.jei.common.network.packets.PacketRecipeTransfer;
import mezz.jei.common.network.packets.PacketRecipeTransferCounted;
import mezz.jei.common.network.packets.PacketRequestCheatPermission;
import mezz.jei.common.network.packets.PacketSetHotbarItemStack;
import mezz.jei.common.config.IServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ServerPacketRouter {
	private static final Logger LOGGER = LogManager.getLogger();

	public final EnumMap<PacketIdServer, IServerPacketHandler> handlers = new EnumMap<>(PacketIdServer.class);
	private final IConnectionToClient connection;
	private final IServerConfig serverConfig;

	public ServerPacketRouter(IConnectionToClient connection, IServerConfig serverConfig) {
		this.connection = connection;
		this.serverConfig = serverConfig;
		handlers.put(PacketIdServer.RECIPE_TRANSFER, PacketRecipeTransfer::readPacketData);
		handlers.put(PacketIdServer.DELETE_ITEM, PacketDeletePlayerItem::readPacketData);
		handlers.put(PacketIdServer.GIVE_ITEM, PacketGiveItemStack::readPacketData);
		handlers.put(PacketIdServer.SET_HOTBAR_ITEM, PacketSetHotbarItemStack::readPacketData);
		handlers.put(PacketIdServer.CHEAT_PERMISSION_REQUEST, PacketRequestCheatPermission::readPacketData);
		handlers.put(PacketIdServer.RECIPE_TRANSFER_COUNTED, PacketRecipeTransferCounted::readPacketData);
		handlers.put(PacketIdServer.PULL_BOOKMARK_ITEMS, PacketPullBookmarkItems::readPacketData);
		handlers.put(PacketIdServer.CRAFTING_GRID_CRAFT, PacketCraftingGridCraft::readPacketData);
		handlers.put(PacketIdServer.FILL_CRAFTING_GRID, PacketFillCraftingGrid::readPacketData);
		handlers.put(PacketIdServer.FAST_PICKUP_ITEM, PacketFastPickupItemStack::readPacketData);
		registerEncodeRecipeChainPatternsHandler(handlers);
	}

	private static void registerEncodeRecipeChainPatternsHandler(EnumMap<PacketIdServer, IServerPacketHandler> handlers) {
		try {
			Class<?> packetClass = Class.forName("mezz.jei.forge.compat.ae2.patternencoding.PacketEncodeRecipeChainPatterns");
			Method readPacketData = packetClass.getMethod("readPacketData", ServerPacketData.class);
			handlers.put(PacketIdServer.ENCODE_RECIPE_CHAIN_PATTERNS, data -> {
				try {
					Object result = readPacketData.invoke(null, data);
					if (result instanceof CompletableFuture<?> future) {
						@SuppressWarnings("unchecked")
						CompletableFuture<Void> voidFuture = (CompletableFuture<Void>) future;
						return voidFuture;
					}
					return CompletableFuture.completedFuture(null);
				} catch (ReflectiveOperationException e) {
					LOGGER.error("Failed to execute recipe chain pattern encoding packet", e);
					return CompletableFuture.completedFuture(null);
				}
			});
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.debug("Recipe chain pattern encoding packet handler is not available", e);
		}
	}

	public void onPacket(FriendlyByteBuf packetBuffer, ServerPlayer player) {
		getPacketId(packetBuffer)
			.ifPresent(packetId -> {
				IServerPacketHandler packetHandler = handlers.get(packetId);
				ServerPacketContext context = new ServerPacketContext(player, serverConfig, connection);
				ServerPacketData data = new ServerPacketData(packetBuffer, context);
				try {
					packetHandler.readPacketData(data)
						.exceptionally(e -> {
							LOGGER.error("Packet error while executing packet on the server thread: {}", packetId.name(), e);
							return null;
						});
				} catch (Throwable e) {
					LOGGER.error("Packet error when reading packet: {}", packetId.name(), e);
				}
			});
	}

	private Optional<PacketIdServer> getPacketId(FriendlyByteBuf packetBuffer) {
		try {
			int packetIdOrdinal = packetBuffer.readByte();
			PacketIdServer packetId = PacketIdServer.VALUES[packetIdOrdinal];
			return Optional.of(packetId);
		} catch (RuntimeException e) {
			LOGGER.error("Packet error when trying to read packet id", e);
			return Optional.empty();
		}
	}
}
