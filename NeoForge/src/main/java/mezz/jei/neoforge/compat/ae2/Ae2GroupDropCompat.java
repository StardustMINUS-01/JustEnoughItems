package mezz.jei.neoforge.compat.ae2;

public final class Ae2GroupDropCompat {
	private Ae2GroupDropCompat() {
	}

	public static void register() {
		if (!Ae2CompatUtil.isLoaded()) {
			return;
		}
		Ae2GroupDropCompatInternal.register();
	}
}
