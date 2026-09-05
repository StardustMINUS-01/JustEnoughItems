package mezz.jei.gui.overlay.ingredients;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.runtime.config.IJeiConfigValue;
import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.config.IngredientGridLayoutMode;
import mezz.jei.common.config.IngredientGridNavigationMode;
import mezz.jei.common.util.NavigationVisibility;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class IngredientGridConfigTestFixtures {
	private IngredientGridConfigTestFixtures() {
	}

	public static class TestGridConfig implements IIngredientGridConfig {
		private final TestConfigValue<Integer> maxColumns = value("maxColumns", 9);
		private final TestConfigValue<Integer> maxRows = value("maxRows", 6);
		private final TestConfigValue<Boolean> drawBackground = value("drawBackground", true);
		private final TestConfigValue<IngredientGridLayoutMode> layoutMode = value("layoutMode", IngredientGridLayoutMode.MAXIMIZE_AVAILABLE_SPACE);
		private final TestConfigValue<HorizontalAlignment> horizontalAlignment = value("horizontalAlignment", HorizontalAlignment.LEFT);
		private final TestConfigValue<VerticalAlignment> verticalAlignment = value("verticalAlignment", VerticalAlignment.TOP);
		private final TestConfigValue<NavigationVisibility> navigationVisibility = value("navigationVisibility", NavigationVisibility.AUTO_HIDE);
		private final TestConfigValue<IngredientGridNavigationMode> navigationMode = value("navigationMode", IngredientGridNavigationMode.PAGED);
		private int minColumns = 1;
		private int minRows = 1;

		public TestGridConfig maxColumns(int maxColumns) {
			this.maxColumns.set(maxColumns);
			return this;
		}

		public TestGridConfig minColumns(int minColumns) {
			this.minColumns = minColumns;
			return this;
		}

		public TestGridConfig maxRows(int maxRows) {
			this.maxRows.set(maxRows);
			return this;
		}

		public TestGridConfig minRows(int minRows) {
			this.minRows = minRows;
			return this;
		}

		public TestGridConfig drawBackground(boolean drawBackground) {
			this.drawBackground.set(drawBackground);
			return this;
		}

		public TestGridConfig layoutMode(IngredientGridLayoutMode layoutMode) {
			this.layoutMode.set(layoutMode);
			return this;
		}

		public TestGridConfig navigationMode(IngredientGridNavigationMode navigationMode) {
			this.navigationMode.set(navigationMode);
			return this;
		}

		public TestGridConfig horizontalAlignment(HorizontalAlignment horizontalAlignment) {
			this.horizontalAlignment.set(horizontalAlignment);
			return this;
		}

		public TestGridConfig verticalAlignment(VerticalAlignment verticalAlignment) {
			this.verticalAlignment.set(verticalAlignment);
			return this;
		}

		public TestGridConfig navigationVisibility(NavigationVisibility navigationVisibility) {
			this.navigationVisibility.set(navigationVisibility);
			return this;
		}

		@Override
		public IJeiConfigValue<Integer> maxColumns() {
			return maxColumns;
		}

		@Override
		public int getMinColumns() {
			return minColumns;
		}

		@Override
		public IJeiConfigValue<Integer> maxRows() {
			return maxRows;
		}

		@Override
		public int getMinRows() {
			return minRows;
		}

		@Override
		public IJeiConfigValue<Boolean> drawBackground() {
			return drawBackground;
		}

		@Override
		public IJeiConfigValue<IngredientGridLayoutMode> layoutMode() {
			return layoutMode;
		}

		@Override
		public IJeiConfigValue<IngredientGridNavigationMode> navigationMode() {
			return navigationMode;
		}

		@Override
		public IJeiConfigValue<HorizontalAlignment> horizontalAlignment() {
			return horizontalAlignment;
		}

		@Override
		public IJeiConfigValue<VerticalAlignment> verticalAlignment() {
			return verticalAlignment;
		}

		@Override
		public IJeiConfigValue<NavigationVisibility> navigationVisibility() {
			return navigationVisibility;
		}

		private static <T> TestConfigValue<T> value(String name, T defaultValue) {
			return new TestConfigValue<>(name, defaultValue);
		}
	}

	private static class TestConfigValue<T> implements IJeiConfigValue<T> {
		private final String name;
		private final T defaultValue;
		private T value;

		public TestConfigValue(String name, T defaultValue) {
			this.name = name;
			this.defaultValue = defaultValue;
			this.value = defaultValue;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		@SuppressWarnings("removal")
		public String getDescription() {
			return "";
		}

		@Override
		public Component getLocalizedName() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Component getLocalizedDescription() {
			throw new UnsupportedOperationException();
		}

		@Override
		public T getValue() {
			return value;
		}

		@Override
		public T getDefaultValue() {
			return defaultValue;
		}

		@Override
		public boolean set(T value) {
			this.value = value;
			return true;
		}

		@Override
		public void addListener(Consumer<T> listener) {
		}

		@Override
		public IJeiConfigValueSerializer<T> getSerializer() {
			throw new UnsupportedOperationException();
		}
	}
}
