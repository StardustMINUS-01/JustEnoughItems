package mezz.jei.gui.overlay;

import mezz.jei.common.util.ImmutableRect2i;

/**
 * A search text field with externally controlled value, focus state, and bounds.
 */
public interface ISearchField {
	/**
	 * Updates the displayed search text.
	 * <p>
	 * NOTE: The method is intentionally named differently from {@link net.minecraft.client.gui.components.EditBox#setValue}
	 * so that Forge's reobfuscation (which renames EditBox methods to SRG names) does not break the
	 * interface implementation. On NeoForge (no reobf) this is not an issue, but on Forge 1.20.1 the
	 * same-named method in the implementing class gets renamed to the SRG name while this interface
	 * method keeps its name, causing an AbstractMethodError.
	 */
	void setSearchText(String filterText);

	/**
	 * Sets whether the search field has keyboard focus.
	 * <p>
	 * See the note on {@link #setSearchText(String)} about why this is not named {@code setFocused}.
	 */
	void setSearchFieldFocused(boolean focused);

	/**
	 * Updates the search field bounds for the current overlay layout.
	 */
	void updateBounds(ImmutableRect2i area);
}
