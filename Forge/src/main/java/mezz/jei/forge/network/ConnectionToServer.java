package mezz.jei.forge.network;

import mezz.jei.common.network.packets.PacketShareIngredient;
import mezz.jei.common.network.packets.PacketShareRecipe;
import com.google.common.collect.ImmutableMap;
import mezz.jei.common.network.ClientConnectionHelper;
import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PacketJei;
import mezz.jei.common.network.packets.PacketShareBookmarkGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.ICustomPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class ConnectionToServer implements IConnectionToServer {
	private static final String FORGE_SERVER_BRAND = "forge";

	@Nullable
	private static UUID jeiOnServerCacheUuid = null;
	private static boolean jeiOnServerCacheValue = false;
	private final NetworkHandler networkHandler;

	public ConnectionToServer(NetworkHandler networkHandler) {
		this.networkHandler = networkHandler;
	}

	@Override
	public boolean isJeiOnServer() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener clientPacketListener = minecraft.getConnection();
		if (clientPacketListener == null || !clientPacketListener.getConnection().isConnected()) {
			return false;
		}
		UUID id = clientPacketListener.getId();
		if (!id.equals(jeiOnServerCacheUuid)) {
			jeiOnServerCacheUuid = id;
			jeiOnServerCacheValue = Optional.of(clientPacketListener)
				.map(ClientPacketListener::getConnection)
				.map(NetworkHooks::getConnectionData)
				.map(ConnectionData::getChannels)
				.map(ImmutableMap::keySet)
				.map(keys -> keys.contains(networkHandler.getChannelId()))
				.orElse(false);
		}
		return jeiOnServerCacheValue;
	}

	@Override
	public boolean isSameModLoader() {
		return ClientConnectionHelper.hasServerBrand(FORGE_SERVER_BRAND);
	}

	@Override
	public boolean supportsRecipeTransferResults() {
		return Optional.ofNullable(ClientConnectionHelper.getConnectedClientPacketListener())
			.map(ClientPacketListener::getConnection)
			.map(NetworkHooks::getConnectionData)
			.map(ConnectionData::getChannels)
			.map(channels -> channels.containsKey(networkHandler.getRecipeTransferResultChannelId()))
			.orElse(false);
	}

	@Override
	public void sendPacketToServer(PacketJei packet) {
		if (packet instanceof mezz.jei.common.network.packets.PacketCraftingGridCraft && !canCraftBookmarks()) {
			return;
		}
		boolean chatShare = packet instanceof PacketShareRecipe || packet instanceof PacketShareIngredient;
		if (chatShare && !canShareChat()) {
			return;
		}
		boolean groupShare = packet instanceof PacketShareBookmarkGroup || chatShare;
		if (groupShare && !canShareBookmarkGroup()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener netHandler = minecraft.getConnection();
		if (netHandler != null && isJeiOnServer()) {
			Pair<FriendlyByteBuf, Integer> packetData = packet.getPacketData();
			ICustomPacket<Packet<?>> payload = NetworkDirection.PLAY_TO_SERVER.buildPacket(packetData, groupShare ? NetworkHandler.BOOKMARK_GROUP_CHANNEL : packet.getChannelId());
			netHandler.send(payload.getThis());
		}
	}

	@Override
	public boolean canShareBookmarkGroup() {
		return Optional.ofNullable(Minecraft.getInstance().getConnection())
			.map(ClientPacketListener::getConnection)
			.filter(connection -> connection.isConnected())
			.map(NetworkHooks::getConnectionData)
			.map(ConnectionData::getChannels)
			.map(channels -> channels.containsKey(NetworkHandler.BOOKMARK_GROUP_CHANNEL))
			.orElse(false);
	}

	@Override
	public boolean canCraftBookmarks() {
		return Optional.ofNullable(Minecraft.getInstance().getConnection())
			.map(ClientPacketListener::getConnection)
			.filter(connection -> connection.isConnected())
			.map(NetworkHooks::getConnectionData)
			.map(ConnectionData::getChannels)
			.map(channels -> channels.containsKey(mezz.jei.common.network.packets.PacketCraftingGridCraft.CHANNEL))
			.orElse(false);
	}

	@Override
	public boolean canShareChat() {
		return Optional.ofNullable(Minecraft.getInstance().getConnection())
			.map(ClientPacketListener::getConnection)
			.filter(connection -> connection.isConnected())
			.map(NetworkHooks::getConnectionData)
			.map(ConnectionData::getChannels)
			.map(channels -> "2".equals(channels.get(NetworkHandler.BOOKMARK_GROUP_CHANNEL)))
			.orElse(false);
	}

	@Override
	public void onRuntimeStopped() {
		jeiOnServerCacheUuid = null;
		jeiOnServerCacheValue = false;
	}
}
