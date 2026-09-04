package mezz.jei.test.gui.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;
import mezz.jei.gui.config.FavoriteRecipeJsonSerializer;
import mezz.jei.gui.favorites.FavoriteRecipeStore;
import mezz.jei.gui.input.FocusedRecipe;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

public class FavoriteRecipeJsonSerializerTest {
	private static final IIngredientType<String> TYPE = new IIngredientType<>() {
		@Override
		public Class<? extends String> getIngredientClass() {
			return String.class;
		}

		@Override
		public String getUid() {
			return "test:string";
		}
	};
	private static final IIngredientManager INGREDIENT_MANAGER = ingredientManager();

	@Test
	public void codecRoundTripsTypedTargetAndOrderedInputs() {
		BookmarkIngredientKey target = key("target");
		BookmarkIngredientKey first = key("first");
		BookmarkIngredientKey second = key("second");
		FavoriteRecipeStore.Entry entry = new FavoriteRecipeStore.Entry(
			target,
			new FocusedRecipe(ResourceLocation.parse("test:crafting"), ResourceLocation.parse("test:recipe")),
			Map.of(4, new FavoriteRecipeStore.FavoriteSlotInput(second, List.of(first, second)))
		);
		Codec<FavoriteRecipeStore.Entry> codec = FavoriteRecipeJsonSerializer.create(codecHelper(), INGREDIENT_MANAGER);

		JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, entry).getOrThrow();
		FavoriteRecipeStore.Entry decoded = codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();

		Assertions.assertEquals(entry, decoded);
		Assertions.assertEquals("test:crafting", encoded.getAsJsonObject().get("recipeType").getAsString());
		Assertions.assertEquals(List.of(first, second), decoded.inputs().get(4).permutations());
		Assertions.assertNotNull(decoded.target().typedIngredient());
		Assertions.assertTrue(decoded.inputs().get(4).permutations().stream().allMatch(key -> key.typedIngredient() != null));
	}

	private static BookmarkIngredientKey key(String value) {
		ITypedIngredient<String> ingredient = new TypedIngredient(value);
		return new BookmarkIngredientKey(TYPE.getUid(), value, ingredient);
	}

	private static ICodecHelper codecHelper() {
		MapCodec<ITypedIngredient<?>> typedIngredientCodec = Codec.STRING
			.fieldOf("ingredient")
			.xmap(TypedIngredient::new, ingredient -> (String) ingredient.getIngredient());
		return (ICodecHelper) Proxy.newProxyInstance(
			ICodecHelper.class.getClassLoader(),
			new Class<?>[]{ICodecHelper.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getTypedIngredientCodec" -> typedIngredientCodec;
				default -> throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private static IIngredientManager ingredientManager() {
		IIngredientHelper<String> helper = new IIngredientHelper<>() {
			@Override
			public IIngredientType<String> getIngredientType() {
				return TYPE;
			}

			@Override
			public String getDisplayName(String ingredient) {
				return ingredient;
			}

			@Override
			public String getUniqueId(String ingredient, UidContext context) {
				return ingredient;
			}

			@Override
			public ResourceLocation getResourceLocation(String ingredient) {
				return ResourceLocation.parse("test:" + ingredient);
			}

			@Override
			public String copyIngredient(String ingredient) {
				return ingredient;
			}

			@Override
			public String getErrorInfo(String ingredient) {
				return ingredient;
			}
		};
		return (IIngredientManager) Proxy.newProxyInstance(
			IIngredientManager.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> {
				if ("getIngredientHelper".equals(method.getName())) {
					return helper;
				}
				throw new UnsupportedOperationException(method.getName());
			}
		);
	}

	private record TypedIngredient(String ingredient) implements ITypedIngredient<String> {
		@Override
		public IIngredientType<String> getType() {
			return TYPE;
		}

		@Override
		public String getIngredient() {
			return ingredient;
		}
	}
}
