package mezz.jei.gui.config.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Screen-owned draft supplied by the platform, without changing live key mappings. */
public interface IConfigKeyBinding {
	String getName();
	String getCategory();
	Component getBindingName();
	boolean isChanged();
	boolean isDefault();
	boolean setKey(InputConstants.Key key, int modifiers);
	void reset();
	void apply();
	List<Component> getConflicts();
}
