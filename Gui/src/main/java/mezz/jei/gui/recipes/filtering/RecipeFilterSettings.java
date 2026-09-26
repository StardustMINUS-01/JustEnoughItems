package mezz.jei.gui.recipes.filtering;

public final class RecipeFilterSettings {
	private RecipeFilterMode mode = RecipeFilterMode.DEFAULT;
	private RecipeSearchScope scope = RecipeSearchScope.NONE;

	public RecipeSearchScope getScope() { return scope; }
	public void setScope(RecipeSearchScope scope) { this.scope = scope; }
	public void setMode(RecipeFilterMode mode) { this.mode = mode; }
	private String draftQuery = "";
	private String appliedQuery = "";

	public RecipeFilterMode getMode() {
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

	public void restore(RecipeFilterMode mode, String query) {
		this.mode = mode;
		this.draftQuery = query;
		this.appliedQuery = query;
	}
}
