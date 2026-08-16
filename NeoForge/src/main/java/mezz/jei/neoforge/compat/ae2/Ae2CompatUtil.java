package mezz.jei.neoforge.compat.ae2;

import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.function.Supplier;

public final class Ae2CompatUtil {
	private Ae2CompatUtil() {
	}

	public static boolean isLoaded() {
		return ModList.get().isLoaded("ae2");
	}

	/**
	 * Checks whether a class exists without loading it, safe during early mixin phases
	 * when ModList is not available yet.
	 */
	public static boolean isClassPresent(String className) {
		String resourceName = className.replace('.', '/') + ".class";
		return Ae2CompatUtil.class.getClassLoader().getResource(resourceName) != null;
	}

	public static <T> Optional<T> createIfLoaded(String className, Supplier<T> factory) {
		try {
			Class.forName(className);
			return Optional.of(factory.get());
		} catch (ReflectiveOperationException | LinkageError e) {
			return Optional.empty();
		}
	}
}
