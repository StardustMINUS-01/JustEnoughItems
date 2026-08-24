package mezz.jei.gui.compat.ae2;

public final class Ae2RecipeChainPatternEncodingBridgeRegistry {
	private static Ae2RecipeChainPatternEncodingBridge bridge = Ae2RecipeChainPatternEncodingBridge.UNAVAILABLE;

	private Ae2RecipeChainPatternEncodingBridgeRegistry() {
	}

	public static Ae2RecipeChainPatternEncodingBridge getBridge() {
		return bridge;
	}

	public static void register(Ae2RecipeChainPatternEncodingBridge bridge) {
		Ae2RecipeChainPatternEncodingBridgeRegistry.bridge = bridge;
	}

	public static void reset() {
		bridge = Ae2RecipeChainPatternEncodingBridge.UNAVAILABLE;
	}
}
