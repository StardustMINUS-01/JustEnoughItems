package mezz.jei.common.config;

import net.mezzdev.config.api.schema.builder.IConfigCategoryBuilder;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.editor.ConfigValueEditMode;
import net.mezzdev.config.api.value.serializer.IDeserializeResult;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CollapsibleColorConfig {
	public static final int DEFAULT_COLOR = 0x339999FF;/*我爱你尘宝 */
	private static final ColorSerializer SERIALIZER = new ColorSerializer();
	@Nullable
	private static CollapsibleColorConfig instance;

	private final IConfigValue<Integer> collapsedColor;
	private final IConfigValue<Integer> expandedColor;
	private final IConfigValue<Boolean> showGroupSize;

	private CollapsibleColorConfig(IConfigCategoryBuilder category) {
		collapsedColor = category.addValue("collapsedColor", DEFAULT_COLOR, SERIALIZER)
			.setEditMode(ConfigValueEditMode.IMMEDIATE)
			.build();
		expandedColor = category.addValue("expandedColor", DEFAULT_COLOR, SERIALIZER)
			.setEditMode(ConfigValueEditMode.IMMEDIATE)
			.build();
		showGroupSize = category.addBoolean("showGroupSize", false)
			.setEditMode(ConfigValueEditMode.IMMEDIATE)
			.build();
	}

	static void register(IConfigCategoryBuilder category) {
		instance = new CollapsibleColorConfig(category);
	}

	public static IConfigValue<Boolean> getShowGroupSize() {
		return Objects.requireNonNull(instance).showGroupSize;
	}

	public static IConfigValue<Integer> getCollapsedColor() {
		return Objects.requireNonNull(instance).collapsedColor;
	}

	public static IConfigValue<Integer> getExpandedColor() {
		return Objects.requireNonNull(instance).expandedColor;
	}

	private static final class ColorSerializer implements IConfigValueSerializer<Integer> {
		@Override
		public String serialize(Integer value) {
			return "0x%08X".formatted(value);
		}

		@Override
		public IDeserializeResult<Integer> deserialize(String text) {
			String value = text.trim();
			if (value.startsWith("0x") || value.startsWith("0X")) {
				value = value.substring(2);
			}
			if (value.length() == 8 && value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
				return IDeserializeResult.success(Integer.parseUnsignedInt(value, 16));
			}
			return IDeserializeResult.failure(getValidValuesDescription());
		}

		@Override
		public String getValidValuesDescription() {
			return "0xAARRGGBB";
		}

		@Override
		public boolean isValid(@Nullable Integer value) {
			return value != null;
		}
	}
}
