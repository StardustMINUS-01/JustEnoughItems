package mezz.jei.common.recipes;

/** Runtime material lifecycle shared by the GUI reload handler and the recipe manager. */
public interface IRecipeMaterialCacheInternal {
	void invalidateRecipeMaterials();
}
