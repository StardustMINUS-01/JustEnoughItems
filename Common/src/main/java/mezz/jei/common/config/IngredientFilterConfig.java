package mezz.jei.common.config;

import mezz.jei.common.search.compact.PinyinOption;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.schema.builder.IConfigCategoryBuilder;

public class IngredientFilterConfig implements IIngredientFilterConfig {
	public enum SearchBackendPriority {
		RESPECT_PLUGIN_OVERRIDE, PREFER_NATIVE_WHEN_PINYIN_ENABLED
	}

	private final java.util.EnumMap<PinyinOption, IConfigValue<Boolean>> pinyin = new java.util.EnumMap<>(PinyinOption.class);
	private final IConfigValue<SearchBackendPriority> priority;

	public int pinyinFlags() {
		if (!pinyin.get(PinyinOption.ENABLED).get()) {
			return pinyin.get(PinyinOption.FOLD).get() ? PinyinOption.FOLD.bit : 0;
		}
		int flags = 0;
		for (var entry : pinyin.entrySet()) {
			if (entry.getValue().get()) {
				flags |= entry.getKey().bit;
			}
		}
		return flags;
	}

	public boolean preferNativeSearch() {
		return priority.get() == SearchBackendPriority.PREFER_NATIVE_WHEN_PINYIN_ENABLED && pinyin.get(PinyinOption.ENABLED).get();
	}

	public java.util.Map<PinyinOption, IConfigValue<Boolean>> pinyinOptions() {
		return java.util.Collections.unmodifiableMap(pinyin);
	}

	private final IConfigValue<SearchMode> modNameSearchMode;
	private final IConfigValue<SearchMode> tooltipSearchMode;
	private final IConfigValue<SearchMode> tagSearchMode;
	private final IConfigValue<SearchMode> colorSearchMode;
	private final IConfigValue<SearchMode> resourceLocationSearchMode;
	private final IConfigValue<SearchMode> creativeTabSearchMode;
	private final IConfigValue<Boolean> searchAdvancedTooltips;
	private final IConfigValue<Boolean> searchModIds;
	private final IConfigValue<Boolean> searchModAliases;
	private final IConfigValue<Boolean> searchShortModNames;
	private final IConfigValue<Boolean> searchIngredientAliases;

	public IngredientFilterConfig(IConfigCategoryBuilder search) {
		for (var option : PinyinOption.values()) {
			pinyin.put(option, search.addBoolean(option.key, false).build());
		}
		priority = search.addEnum("searchBackendPriority", SearchBackendPriority.RESPECT_PLUGIN_OVERRIDE).build();
		modNameSearchMode = search.addEnum("modNameSearchMode", SearchMode.REQUIRE_PREFIX)
			.build();
		tagSearchMode = search.addEnum("tagSearchMode", SearchMode.REQUIRE_PREFIX)
			.build();
		tooltipSearchMode = search.addEnum("tooltipSearchMode", SearchMode.ENABLED)
			.build();
		colorSearchMode = search.addEnum("colorSearchMode", SearchMode.DISABLED)
			.build();
		resourceLocationSearchMode = search.addEnum("resourceLocationSearchMode", SearchMode.DISABLED)
			.addLegacyValue("search", "identifierSearchMode")
			.build();
		creativeTabSearchMode = search.addEnum("creativeTabSearchMode", SearchMode.DISABLED)
			.build();
		searchAdvancedTooltips = search.addBoolean("searchAdvancedTooltips", false)
			.build();
		searchModIds = search.addBoolean("searchModIds", true)
			.build();
		searchModAliases = search.addBoolean("searchModAliases", true)
			.build();
		searchShortModNames = search.addBoolean("searchShortModNames", false)
			.build();
		searchIngredientAliases = search.addBoolean("searchIngredientAliases", true)
			.build();
	}

	@Override
	public IConfigValue<SearchMode> modNameSearchMode() {
		return modNameSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> tooltipSearchMode() {
		return tooltipSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> tagSearchMode() {
		return tagSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> colorSearchMode() {
		return colorSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> resourceLocationSearchMode() {
		return resourceLocationSearchMode;
	}

	@Override
	public IConfigValue<SearchMode> creativeTabSearchMode() {
		return creativeTabSearchMode;
	}

	@Override
	public IConfigValue<Boolean> searchAdvancedTooltips() {
		return searchAdvancedTooltips;
	}

	@Override
	public IConfigValue<Boolean> searchModIds() {
		return searchModIds;
	}

	@Override
	public IConfigValue<Boolean> searchModAliases() {
		return searchModAliases;
	}

	@Override
	public IConfigValue<Boolean> searchIngredientAliases() {
		return searchIngredientAliases;
	}

	@Override
	public IConfigValue<Boolean> searchShortModNames() {
		return searchShortModNames;
	}
}
