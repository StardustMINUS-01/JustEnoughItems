package mezz.jei.forge.compat.ae2;

import mezz.jei.forge.compat.CompatUtil;

/**
 * Registers the bookmark-group drop bridge for AE2 terminals.
 * Ported from JEI 1.21.1 (neoforge Ae2GroupDropCompat.java); guards on the ae2 mod being loaded.
 */
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
