package mezz.jei.forge.compat.ae2.patternencoding;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ForkPatternEncodingAccess {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final ForkPatternEncodingAccess INSTANCE = create();

	private final Method hasBlankPatterns;
	private final Method consumeBlankPatterns;
	private final Method encodeProcessingPattern;
	private final Constructor<?> catalystConstructor;

	public static ForkPatternEncodingAccess get() {
		return INSTANCE;
	}

	private static ForkPatternEncodingAccess create() {
		try {
			Class<?> menuClass = Class.forName("appeng.menu.me.items.PatternEncodingTermMenu");
			Method hasBlankPatterns = menuClass.getMethod("hasBlankPatterns", int.class);
			Method consumeBlankPatterns = menuClass.getMethod("consumeBlankPatterns", int.class);
			Class<?> helperClass = Class.forName("appeng.crafting.pattern.PatternVirtualInputHelper");
			Method encodeProcessingPattern = helperClass.getMethod("encodeProcessingPattern", List.class, List.class, List.class);
			Class<?> catalystClass = Class.forName("appeng.crafting.pattern.PatternCatalyst");
			Constructor<?> catalystConstructor = catalystClass.getConstructor(int.class, GenericStack.class);
			return new ForkPatternEncodingAccess(hasBlankPatterns, consumeBlankPatterns, encodeProcessingPattern, catalystConstructor);
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.debug("AE2 fork pattern encoding interfaces are not available", e);
			return null;
		}
	}

	private ForkPatternEncodingAccess(
		Method hasBlankPatterns,
		Method consumeBlankPatterns,
		Method encodeProcessingPattern,
		Constructor<?> catalystConstructor
	) {
		this.hasBlankPatterns = hasBlankPatterns;
		this.consumeBlankPatterns = consumeBlankPatterns;
		this.encodeProcessingPattern = encodeProcessingPattern;
		this.catalystConstructor = catalystConstructor;
	}

	public boolean hasBlankPatterns(PatternEncodingTermMenu menu, int amount) {
		return invokeBoolean(hasBlankPatterns, menu, amount);
	}

	public boolean consumeBlankPatterns(PatternEncodingTermMenu menu, int amount) {
		return invokeBoolean(consumeBlankPatterns, menu, amount);
	}

	public ItemStack encodeProcessingPattern(
		List<GenericStack> inputs,
		List<GenericStack> outputs,
		List<JeiPatternCatalystWire> catalysts
	) {
		List<Object> forkCatalysts = new ArrayList<>(catalysts.size());
		for (JeiPatternCatalystWire catalyst : catalysts) {
			try {
				forkCatalysts.add(catalystConstructor.newInstance(catalyst.sourceSlot(), catalyst.stack()));
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		try {
			return (ItemStack) encodeProcessingPattern.invoke(null, inputs, outputs, forkCatalysts);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean invokeBoolean(Method method, PatternEncodingTermMenu menu, int amount) {
		try {
			return (boolean) method.invoke(menu, amount);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
