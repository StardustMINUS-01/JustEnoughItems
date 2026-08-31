package mezz.jei.forge.compat.ae2.patternencoding;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ForkPatternEncodingAccess {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final ForkPatternEncodingAccess INSTANCE = create();

	private final Method hasBlankPatterns;
	private final Method consumeBlankPatterns;
	private final Method encodeProcessingPattern;
	private final Constructor<?> catalystConstructor;
	private final Method gridNodeGetter;
	private final Method gridGetter;
	private final Method craftingServiceGetter;
	private final Method isCraftable;

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
			Class<?> gridNodeClass = Class.forName("appeng.api.networking.IGridNode");
			Method gridNodeGetter = PatternEncodingTermMenu.class.getMethod("getGridNode");
			Method gridGetter = gridNodeClass.getMethod("getGrid");
			Class<?> gridClass = Class.forName("appeng.api.networking.IGrid");
			Method craftingServiceGetter = gridClass.getMethod("getCraftingService");
			Class<?> craftingServiceClass = Class.forName("appeng.api.crafting.ICraftingService");
			Method isCraftable = craftingServiceClass.getMethod("isCraftable", AEKey.class);
			return new ForkPatternEncodingAccess(hasBlankPatterns, consumeBlankPatterns, encodeProcessingPattern, catalystConstructor, gridNodeGetter, gridGetter, craftingServiceGetter, isCraftable);
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.debug("AE2 fork pattern encoding interfaces are not available", e);
			return null;
		}
	}

	private ForkPatternEncodingAccess(
		Method hasBlankPatterns,
		Method consumeBlankPatterns,
		Method encodeProcessingPattern,
		Constructor<?> catalystConstructor,
		Method gridNodeGetter,
		Method gridGetter,
		Method craftingServiceGetter,
		Method isCraftable
	) {
		this.hasBlankPatterns = hasBlankPatterns;
		this.consumeBlankPatterns = consumeBlankPatterns;
		this.encodeProcessingPattern = encodeProcessingPattern;
		this.catalystConstructor = catalystConstructor;
		this.gridNodeGetter = gridNodeGetter;
		this.gridGetter = gridGetter;
		this.craftingServiceGetter = craftingServiceGetter;
		this.isCraftable = isCraftable;
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

	public Predicate<AEKey> getNetworkPatternLookup(PatternEncodingTermMenu menu) {
		Object gridNode = invokeObject(gridNodeGetter, menu);
		if (gridNode == null) {
			return key -> false;
		}
		Object grid = invokeObject(gridGetter, gridNode);
		if (grid == null) {
			return key -> false;
		}
		Object craftingService = invokeObject(craftingServiceGetter, grid);
		if (craftingService == null) {
			return key -> false;
		}
		return key -> invokeBoolean(isCraftable, craftingService, (AEKey) key);
	}

	private static Object invokeObject(Method method, Object target) {
		try {
			return method.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean invokeBoolean(Method method, Object target, Object arg) {
		try {
			return (boolean) method.invoke(target, arg);
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
