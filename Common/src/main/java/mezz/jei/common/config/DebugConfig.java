package mezz.jei.common.config;

import mezz.jei.common.config.file.IConfigCategoryBuilder;
import mezz.jei.common.config.file.IConfigSchemaBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class DebugConfig {
	@Nullable
	private static DebugConfig instance;

	public static void create(IConfigSchemaBuilder schema) {
		instance = new DebugConfig(schema);
	}

	private final Supplier<Boolean> debugModeEnabled;
	private final Supplier<Boolean> debugIngredientsEnabled;
	private final Supplier<Boolean> debugGuisEnabled;
	private final Supplier<Boolean> debugInputsEnabled;
	private final Supplier<Boolean> debugInfoTooltipsEnabled;
	private final Supplier<Boolean> logSuffixTreeStats;

	private DebugConfig(IConfigSchemaBuilder schema) {
		IConfigCategoryBuilder advanced = schema.addCategory("debug");
		// Master switch for the temporary diagnostic logs ([Bug5]/[Bug6]/[FavTree]...).
		// Default OFF for releases; enable in the debug config category when investigating.
		debugModeEnabled = advanced.addBoolean("debugMode", false);
		debugIngredientsEnabled = advanced.addBoolean("debugIngredientsEnabled", false);
		debugGuisEnabled = advanced.addBoolean("debugGuis", false);
		debugInputsEnabled = advanced.addBoolean("debugInputs", false);
		debugInfoTooltipsEnabled = advanced.addBoolean("debugInfoTooltipsEnabled", false);
		logSuffixTreeStats = advanced.addBoolean("logSuffixTreeStats", false);
	}

	public static boolean isDebugModeEnabled() {
		if (instance == null) {
			// Config not loaded yet (early startup, or dedicated server without client config).
			// Default to OFF so release builds never spam diagnostic logs.
			return false;
		}
		return instance.debugModeEnabled.get();
	}

	public static boolean isDebugIngredientsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugIngredientsEnabled.get();
	}

	public static boolean isDebugGuisEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugGuisEnabled.get();
	}

	public static boolean isDebugInputsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugInputsEnabled.get();
	}

	public static boolean isDebugInfoTooltipsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.debugInfoTooltipsEnabled.get();
	}

	public static boolean isLogSuffixTreeStatsEnabled() {
		if (instance == null) {
			return false;
		}
		return instance.logSuffixTreeStats.get();
	}
}
