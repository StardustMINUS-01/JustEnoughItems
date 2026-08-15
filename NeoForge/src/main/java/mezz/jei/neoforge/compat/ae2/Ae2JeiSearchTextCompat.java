package mezz.jei.neoforge.compat.ae2;

public final class Ae2JeiSearchTextCompat {
	private Ae2JeiSearchTextCompat() {
	}

	public static void register() {
		if (!Ae2CompatUtil.isLoaded()) {
			return;
		}
		Ae2JeiSearchTextCompatInternal.register();
	}
}
