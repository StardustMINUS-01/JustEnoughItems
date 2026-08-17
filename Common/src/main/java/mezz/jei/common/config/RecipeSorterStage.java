package mezz.jei.common.config;

import java.util.List;

public enum RecipeSorterStage {
	BOOKMARKED, CRAFTABLE;

	public static final List<RecipeSorterStage> defaultStages = List.of(
		RecipeSorterStage.BOOKMARKED,
		RecipeSorterStage.CRAFTABLE
	);

	public boolean isEnabled(IClientConfig clientConfig) {
		return clientConfig.getRecipeSorterStages().contains(this);
	}

	public void setEnabled(IClientConfig clientConfig, boolean enabled) {
		boolean currentlyEnabled = clientConfig.getRecipeSorterStages().contains(this);
		if (enabled == currentlyEnabled) {
			return;
		}
		if (enabled) {
			clientConfig.enableRecipeSorterStage(this);
		} else {
			clientConfig.disableRecipeSorterStage(this);
		}
	}
}
