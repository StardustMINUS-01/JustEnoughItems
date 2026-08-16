package mezz.jei.neoforge.compat.ae2;

import mezz.jei.neoforge.compat.CompatUtil;

public final class Ae2JeiSearchTextCompat {
	private Ae2JeiSearchTextCompat() {
	}

	public static void register() {
		if (!CompatUtil.isModLoaded("ae2")) {
			return;
		}
		Ae2JeiSearchTextCompatInternal.register();
	}
}
