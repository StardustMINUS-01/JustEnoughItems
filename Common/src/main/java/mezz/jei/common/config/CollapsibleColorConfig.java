package mezz.jei.common.config;

import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;
import mezz.jei.common.config.file.ConfigCategoryBuilder;
import mezz.jei.common.config.file.ConfigValue;
import mezz.jei.common.config.file.serializers.DeserializeResult;

import java.util.Collection;
import java.util.Optional;

public final class CollapsibleColorConfig {
	public static final int DEFAULT_COLOR = 0x339999FF;/*我爱你尘宝 */
	private static final ColorSerializer SERIALIZER = new ColorSerializer();
	private static final ConfigValue<Integer> collapsedColor = createValue("collapsedColor");
	private static final ConfigValue<Integer> expandedColor = createValue("expandedColor");

	private CollapsibleColorConfig() {}

	private static ConfigValue<Integer> createValue(String name) {
		return new ConfigValue<>("jei.config.client.collapsible", name, DEFAULT_COLOR, SERIALIZER);
	}

	static void register(ConfigCategoryBuilder category) {
		category.addValue(collapsedColor);
		category.addValue(expandedColor);
	}

	public static ConfigValue<Integer> getCollapsedColor() {
		return collapsedColor;
	}

	public static ConfigValue<Integer> getExpandedColor() {
		return expandedColor;
	}

	private static final class ColorSerializer implements IJeiConfigValueSerializer<Integer> {
		@Override
		public String serialize(Integer value) {
			return "0x%08X".formatted(value);
		}

		@Override
		public DeserializeResult<Integer> deserialize(String text) {
			String value = text.trim();
			if (value.startsWith("0x") || value.startsWith("0X")) {
				value = value.substring(2);
			}
			if (value.length() == 8 && value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
				return new DeserializeResult<>(Integer.parseUnsignedInt(value, 16));
			}
			return new DeserializeResult<>(null, getValidValuesDescription());
		}

		@Override
		public String getValidValuesDescription() {
			return "0xAARRGGBB";
		}

		@Override
		public boolean isValid(Integer value) {
			return true;
		}

		@Override
		public Optional<Collection<Integer>> getAllValidValues() {
			return Optional.empty();
		}
	}
}
