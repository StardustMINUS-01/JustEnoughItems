package mezz.jei.common.network;

import mezz.jei.common.network.packets.PacketJei;

public interface IConnectionToServer {
	default boolean canCraftBookmarks() {
		return false;
	}

	default boolean canShareChat() {
		return false;
	}

	boolean isJeiOnServer();

	/**
	 * Returns true when the connected server is using the same mod loader as the client.
	 */
	boolean isSameModLoader();

	/**
	 * Returns true when the server supports reporting the result of recipe transfer packets.
	 */
	default boolean supportsRecipeTransferResults() {
		return false;
	}

	void sendPacketToServer(PacketJei packet);

	default boolean canShareBookmarkGroup() {
		return false;
	}

	default void onRuntimeStopped() {

	}
}
