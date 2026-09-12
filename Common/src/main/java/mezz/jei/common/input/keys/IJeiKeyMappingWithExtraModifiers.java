package mezz.jei.common.input.keys;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;

public interface IJeiKeyMappingWithExtraModifiers extends IJeiKeyMapping {
	boolean isActiveAndMatchesAllowingExtraModifiers(InputConstants.Key key);

	default boolean isActiveAndMatchesShortcut(InputConstants.Key key) {
		return isActiveAndMatches(key);
	}

	static boolean matchesShortcut(IJeiKeyMapping mapping, InputConstants.Key key) {
		if (mapping instanceof IJeiKeyMappingWithExtraModifiers internal) {
			return internal.isActiveAndMatchesShortcut(key);
		}
		return mapping.isActiveAndMatches(key);
	}
}
