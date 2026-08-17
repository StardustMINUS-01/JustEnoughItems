package mezz.jei.common.network;

public enum PacketIdClient implements IPacketId {
	CHEAT_PERMISSION,
	CRAFTING_GRID_CRAFT_ACK;

	public static final PacketIdClient[] VALUES = values();
}
