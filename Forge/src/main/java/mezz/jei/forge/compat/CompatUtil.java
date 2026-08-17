package mezz.jei.forge.compat;

import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.function.Supplier;

public final class CompatUtil {
	private CompatUtil() {
	}

	public static boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	/**
	 * Checks whether a class exists without loading it, safe during early mixin phases
	 * when ModList is not available yet.
	 */
	public static boolean isClassPresent(String className) {
		String resourceName = className.replace('.', '/') + ".class";
		return CompatUtil.class.getClassLoader().getResource(resourceName) != null;
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
