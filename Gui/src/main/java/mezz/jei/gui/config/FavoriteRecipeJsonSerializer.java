package mezz.jei.gui.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public final class FavoriteRecipeJsonSerializer {
	private static final String TYPE_FAVORITE = "favorite";
	private static final Codec<String> FAVORITE_TYPE_CODEC = Codec.STRING.flatXmap(
		type -> TYPE_FAVORITE.equals(type) ?
			DataResult.success(type) :
			DataResult.error(() -> "Unknown favorite recipe type: " + type),
		DataResult::success
	);
	private static final Codec<Integer> SLOT_INDEX_CODEC = Codec.STRING.flatXmap(
		value -> {
			try {
				return DataResult.success(Integer.parseInt(value));
			} catch (NumberFormatException e) {
				return DataResult.error(() -> "Invalid favorite recipe slot index: " + value);
			}
		},
		index -> DataResult.success(Integer.toString(index))
	);

	private FavoriteRecipeJsonSerializer() {
	}

	public static Codec<FavoriteRecipeStore.Entry> create(
		ICodecHelper codecHelper,
		IIngredientManager ingredientManager
	) {
		Codec<ITypedIngredient<?>> typedIngredientCodec = codecHelper.getTypedIngredientCodec().codec();
		Codec<BookmarkIngredientKey> keyCodec = BookmarkConfigEntryCodec.createIngredientKeyCodec(
			typedIngredientCodec,
			ingredientManager
		);
		Codec<FavoriteRecipeStore.FavoriteSlotInput> slotInputCodec = RecordCodecBuilder.create(instance -> instance.group(
			keyCodec.fieldOf("selected").forGetter(FavoriteRecipeStore.FavoriteSlotInput::selected),
			keyCodec.listOf().optionalFieldOf("permutations", List.of())
				.forGetter(input -> input.permutations().equals(List.of(input.selected())) ? List.of() : input.permutations())
		).apply(instance, FavoriteRecipeStore.FavoriteSlotInput::new));
		Codec<Map<Integer, FavoriteRecipeStore.FavoriteSlotInput>> inputsCodec = Codec.unboundedMap(
			SLOT_INDEX_CODEC,
			slotInputCodec
		);
		return RecordCodecBuilder.create(instance -> instance.group(
			FAVORITE_TYPE_CODEC.fieldOf("type").forGetter(entry -> TYPE_FAVORITE),
			ResourceLocation.CODEC.fieldOf("recipeType").forGetter(entry -> entry.recipe().recipeTypeUid()),
			ResourceLocation.CODEC.fieldOf("recipe").forGetter(entry -> entry.recipe().recipeUid()),
			keyCodec.fieldOf("target").forGetter(FavoriteRecipeStore.Entry::target),
			inputsCodec.optionalFieldOf("inputs", Map.of()).forGetter(FavoriteRecipeStore.Entry::inputs)
		).apply(instance, (type, recipeType, recipe, target, inputs) ->
			new FavoriteRecipeStore.Entry(target, new FocusedRecipe(recipeType, recipe), inputs)
		));
	}
}
