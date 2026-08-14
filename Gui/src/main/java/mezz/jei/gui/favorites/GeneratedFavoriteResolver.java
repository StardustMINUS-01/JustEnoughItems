package mezz.jei.gui.favorites;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.config.IJeiConfigValueSerializer.IDeserializeResult;
import mezz.jei.common.config.file.serializers.TypedIngredientSerializer;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Auto-resolves a "generated favorite" for a {@link BookmarkIngredientKey}.
 *
 * <p>In 1.21.1 this is handled by {@code RecipePreferenceCandidateResolver}, which
 * also consults user-configured recipe-preferences rules. 1.20.1 has no rule system,
 * so this simplified resolver searches the recipe manager for the first recipe that
 * has the ingredient as an OUTPUT and uses that recipe to continue expanding the
 * favorite recipe tree. The resolved recipe is cached by the
 * {@link FavoriteRecipeStore} via {@code setGeneratedFavorite}.</p>
 */
public class GeneratedFavoriteResolver {
	private final IRecipeManager recipeManager;
	private final IFocusFactory focusFactory;
	private final TypedIngredientSerializer ingredientSerializer;

	public GeneratedFavoriteResolver(
		IRecipeManager recipeManager,
		IFocusFactory focusFactory,
		IIngredientManager ingredientManager
	) {
		this.recipeManager = recipeManager;
		this.focusFactory = focusFactory;
		this.ingredientSerializer = new TypedIngredientSerializer(ingredientManager);
	}

	public Optional<FocusedRecipe> resolve(BookmarkIngredientKey target, RecipeLayoutBuildCache layoutCache) {
		String serializedIngredient = target.serializedIngredient();
		if (serializedIngredient == null) {
			return Optional.empty();
		}
		IDeserializeResult<ITypedIngredient<?>> deserialized = ingredientSerializer.deserialize(serializedIngredient);
		Optional<ITypedIngredient<?>> typedIngredient = deserialized.getResult();
		if (typedIngredient.isEmpty()) {
			return Optional.empty();
		}
		IFocus<?> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, typedIngredient.get());
		IRecipeCategoriesLookup categoriesLookup = recipeManager.createRecipeCategoryLookup();
		return categoriesLookup.get()
			.map(category -> resolveInCategory(category, focus))
			.filter(Optional::isPresent)
			.findFirst()
			.orElse(Optional.empty());
	}

	private Optional<FocusedRecipe> resolveInCategory(IRecipeCategory<?> category, IFocus<?> focus) {
		@SuppressWarnings({"unchecked", "rawtypes"})
		RecipeType<Object> recipeType = (RecipeType<Object>) category.getRecipeType();
		@SuppressWarnings("unchecked")
		IRecipeCategory<Object> castCategory = (IRecipeCategory<Object>) category;
		return recipeManager.createRecipeLookup(recipeType)
			.limitFocus(List.of(focus))
			.get()
			.map(castCategory::getRegistryName)
			.filter(java.util.Objects::nonNull)
			.findFirst()
			.map(recipeUid -> new FocusedRecipe(recipeType.getUid(), recipeUid));
	}
}
