package mezz.jei.neoforge.compat.ae2;

import mezz.jei.neoforge.compat.CompatUtil;

public final class Ae2GroupDropCompat {
	private Ae2GroupDropCompat() {
	}

	public static void register() {
		if (!CompatUtil.isModLoaded("ae2")) {
			return;
		}
		Ae2GroupDropCompatInternal.register();
	}
}
