package mezz.jei.gui.recipes.filtering;

public final class RecipeFilterSettings {
	private RecipeFilterMode mode = RecipeFilterMode.ALL;
	private String draftQuery = "";
	private String appliedQuery = "";

	public RecipeFilterMode getMode() {
		return mode;
	}

	public RecipeFilterMode cycleMode() {
		mode = mode.next();
		return mode;
	}

	public String getDraftQuery() {
		return draftQuery;
	}

	public void setDraftQuery(String draftQuery) {
		this.draftQuery = draftQuery;
	}

	public String getAppliedQuery() {
		return appliedQuery;
	}

	public boolean commitQuery() {
		if (appliedQuery.equals(draftQuery)) {
			return false;
		}
		appliedQuery = draftQuery;
		return true;
	}

	public void cancelQuery() {
		draftQuery = appliedQuery;
	}

	public boolean clearQuery() {
		boolean changed = !draftQuery.isEmpty() || !appliedQuery.isEmpty();
		draftQuery = "";
		appliedQuery = "";
		return changed;
	}
}
