package mezz.jei.neoforge.compat.ae2;

import net.neoforged.fml.ModList;

public final class Ae2CompatUtil {
	private Ae2CompatUtil() {
	}

	public static boolean isLoaded() {
		return ModList.get().isLoaded("ae2");
	}
}
