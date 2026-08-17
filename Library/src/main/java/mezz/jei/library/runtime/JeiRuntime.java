package mezz.jei.library.runtime;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.api.runtime.config.IJeiConfigManager;
import mezz.jei.library.focus.FocusGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;


public class JeiRuntime implements IJeiRuntime {
	private static final Logger LOGGER = LogManager.getLogger();
	private final IRecipeManager recipeManager;
	private final IRecipeTransferManager recipeTransferManager;
	private final IEditModeConfig editModeConfig;
	private final IIngredientManager ingredientManager;
	private final IJeiKeyMappings keyMappings;
	private final IJeiHelpers jeiHelpers;
	private final IScreenHelper screenHelper;
	private final IJeiConfigManager configManager;
	private final IIngredientListOverlay ingredientListOverlay;
	private final IBookmarkOverlay bookmarkOverlay;
	private final IRecipesGui recipesGui;
	private final IIngredientFilter ingredientFilter;

	public JeiRuntime(
		IRecipeManager recipeManager,
		IIngredientManager ingredientManager,
		IJeiKeyMappings keyMappings,
		IJeiHelpers jeiHelpers,
		IScreenHelper screenHelper,
		IRecipeTransferManager recipeTransferManager,
		IEditModeConfig editModeConfig,
		IIngredientListOverlay ingredientListOverlay,
		IBookmarkOverlay bookmarkOverlay,
		IRecipesGui recipesGui,
		IIngredientFilter ingredientFilter,
		IJeiConfigManager configManager
	) {
		this.recipeManager = recipeManager;
		this.recipeTransferManager = recipeTransferManager;
		this.editModeConfig = editModeConfig;
		this.ingredientListOverlay = ingredientListOverlay;
		this.bookmarkOverlay = bookmarkOverlay;
		this.recipesGui = recipesGui;
		this.ingredientFilter = ingredientFilter;
		this.ingredientManager = ingredientManager;
		this.keyMappings = keyMappings;
		this.jeiHelpers = jeiHelpers;
		this.screenHelper = screenHelper;
		this.configManager = configManager;
		debugSelfCheck(recipeManager, ingredientManager);
	}

	private static void debugSelfCheck(IRecipeManager recipeManager, IIngredientManager ingredientManager) {
		try {
			LOGGER.info("[Bug5SelfCheck] begin");
			List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
			LOGGER.info("[Bug5SelfCheck] total categories: {}", categories.size());
			int gtCategories = 0;
			int recipesScanned = 0;
			int multiSlotsFound = 0;
			for (IRecipeCategory<?> category : categories) {
				String uid = category.getRecipeType().getUid().toString();
				if (!uid.contains("gregtech") && !uid.contains("gtceu")) {
					continue;
				}
				gtCategories++;
				List<?> recipes = recipeManager.createRecipeLookup(category.getRecipeType()).get().limit(60).toList();
				LOGGER.info("[Bug5SelfCheck] GT category: {} scanRecipes={}", uid, recipes.size());
				for (Object recipe : recipes) {
					@SuppressWarnings({"unchecked", "rawtypes"})
					Optional<? extends IRecipeLayoutDrawable<?>> layoutOpt = (Optional) recipeManager.createRecipeLayoutDrawable((IRecipeCategory) category, recipe, FocusGroup.EMPTY);
					if (layoutOpt.isEmpty()) {
						continue;
					}
					IRecipeLayoutDrawable<?> layout = layoutOpt.get();
					IRecipeSlotsView slotsView = layout.getRecipeSlotsView();
					List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
					for (int i = 0; i < inputSlots.size(); i++) {
						IRecipeSlotView slot = inputSlots.get(i);
						List<ITypedIngredient<?>> ings = slot.getAllIngredients().toList();
						if (ings.size() > 1) {
							multiSlotsFound++;
							String first = ings.get(0).getIngredient().toString();
							LOGGER.info("[Bug5SelfCheck]   MULTI cat={} slotName={} count={} first={}", uid, slot.getSlotName().orElse("?"), ings.size(), first);
							if (multiSlotsFound >= 12) {
								break;
							}
						}
					}
					recipesScanned++;
					if (multiSlotsFound >= 12) {
						break;
					}
				}
				if (multiSlotsFound >= 12) {
					break;
				}
			}
			LOGGER.info("[Bug5SelfCheck] gtCategories={} recipesScanned={} multiSlotsFound={}", gtCategories, recipesScanned, multiSlotsFound);
			LOGGER.info("[Bug5SelfCheck] end");
		} catch (Throwable t) {
			LOGGER.error("[Bug5SelfCheck] FAILED", t);
		}
	}

	@Override
	public IRecipeManager getRecipeManager() {
		return recipeManager;
	}

	@Override
	public IIngredientFilter getIngredientFilter() {
		return ingredientFilter;
	}

	@Override
	public IIngredientListOverlay getIngredientListOverlay() {
		return ingredientListOverlay;
	}

	@Override
	public IIngredientManager getIngredientManager() {
		return ingredientManager;
	}

	@Override
	public IBookmarkOverlay getBookmarkOverlay() {
		return bookmarkOverlay;
	}

	@Override
	public IJeiHelpers getJeiHelpers() {
		return jeiHelpers;
	}

	@Override
	public IRecipesGui getRecipesGui() {
		return recipesGui;
	}

	@Override
	public IJeiKeyMappings getKeyMappings() {
		return keyMappings;
	}

	@Override
	public IScreenHelper getScreenHelper() {
		return screenHelper;
	}

	@Override
	public IRecipeTransferManager getRecipeTransferManager() {
		return recipeTransferManager;
	}

	@Override
	public IEditModeConfig getEditModeConfig() {
		return editModeConfig;
	}

	@Override
	public IJeiConfigManager getConfigManager() {
		return configManager;
	}
}
