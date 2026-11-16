package mezz.jei.gui.overlay.ingredients;

import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import net.mezzdev.config.api.value.IConfigValue;
import mezz.jei.gui.overlay.TestJeiConfigValue;
import mezz.jei.common.config.IngredientGridBackgroundStyle;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.config.IngredientGridLayoutMode;
import mezz.jei.common.config.IngredientGridNavigationMode;
import mezz.jei.common.config.NavigationVisibility;

public final class IngredientGridConfigTestFixtures {
	private IngredientGridConfigTestFixtures() {
	}

	public static class TestGridConfig implements IIngredientGridConfig {
		private final TestJeiConfigValue<Integer> maxColumns = value("maxColumns", 9);
		private final TestJeiConfigValue<Integer> maxRows = value("maxRows", 6);
		private final TestJeiConfigValue<IngredientGridBackgroundStyle> backgroundStyle = value("drawBackground", IngredientGridBackgroundStyle.BACKGROUND);
		private final TestJeiConfigValue<Boolean> shrinkToFit = value("shrinkToFit", false);
		private final TestJeiConfigValue<IngredientGridLayoutMode> layoutMode = value("layoutMode", IngredientGridLayoutMode.MAXIMIZE_AVAILABLE_SPACE);
		private final TestJeiConfigValue<HorizontalAlignment> horizontalAlignment = value("horizontalAlignment", HorizontalAlignment.LEFT);
		private final TestJeiConfigValue<VerticalAlignment> verticalAlignment = value("verticalAlignment", VerticalAlignment.TOP);
		private final TestJeiConfigValue<NavigationVisibility> navigationVisibility = value("navigationVisibility", NavigationVisibility.AUTO_HIDE);
		private final TestJeiConfigValue<IngredientGridNavigationMode> navigationMode = value("navigationMode", IngredientGridNavigationMode.PAGED);
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
			this.backgroundStyle.set(drawBackground ? IngredientGridBackgroundStyle.BACKGROUND : IngredientGridBackgroundStyle.NONE);
			return this;
		}

		public TestGridConfig layoutMode(IngredientGridLayoutMode layoutMode) {
			this.layoutMode.set(layoutMode);
			return this;
		}

		public TestGridConfig backgroundStyle(IngredientGridBackgroundStyle backgroundStyle) {
			this.backgroundStyle.set(backgroundStyle);
			return this;
		}

		public TestGridConfig shrinkToFit(boolean shrinkToFit) {
			this.shrinkToFit.set(shrinkToFit);
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
		public IConfigValue<Integer> maxColumns() {
			return maxColumns;
		}

		@Override
		public int getMinColumns() {
			return minColumns;
		}

		@Override
		public IConfigValue<Integer> maxRows() {
			return maxRows;
		}

		@Override
		public int getMinRows() {
			return minRows;
		}

		@Override
		public IConfigValue<IngredientGridBackgroundStyle> backgroundStyle() {
			return backgroundStyle;
		}

		@Override
		public IConfigValue<IngredientGridLayoutMode> layoutMode() {
			return layoutMode;
		}

		@Override
		public IConfigValue<IngredientGridNavigationMode> navigationMode() {
			return navigationMode;
		}

		@Override
		public IConfigValue<HorizontalAlignment> horizontalAlignment() {
			return horizontalAlignment;
		}

		@Override
		public IConfigValue<VerticalAlignment> verticalAlignment() {
			return verticalAlignment;
		}

		@Override
		public IConfigValue<NavigationVisibility> navigationVisibility() {
			return navigationVisibility;
		}

		@Override
		public IConfigValue<Boolean> shrinkToFit() {
			return shrinkToFit;
		}

		private static <T> TestJeiConfigValue<T> value(String name, T defaultValue) {
			return new TestJeiConfigValue<>(name, defaultValue);
		}
	}

}
