package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.gui.CandidateTooltipComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

public class BookmarkCandidateTooltipCacheTest {
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
	public void reusesTooltipUntilTheCandidateSourceChanges() {
		BookmarkPermutationTooltipState state = new BookmarkPermutationTooltipState();
		List<BookmarkIngredientKey> keys = List.of(
			key("first"),
			key("second")
		);

		CandidateTooltipComponent<?> first = getTooltip(state, "source", keys, keys.getFirst());
		CandidateTooltipComponent<?> reused = getTooltip(state, new String("source"), List.copyOf(keys), keys.getFirst());
		CandidateTooltipComponent<?> changedSelection = getTooltip(state, "source", keys, keys.getLast());
		Assertions.assertSame(first, reused);
		Assertions.assertNotSame(first, changedSelection);

		CandidateTooltipComponent<?> changedSource = getTooltip(state, "other", keys, keys.getFirst());
		Assertions.assertNotSame(changedSelection, changedSource);
	}

	private static CandidateTooltipComponent<?> getTooltip(
		BookmarkPermutationTooltipState state,
		Object sourceKey,
		List<BookmarkIngredientKey> keys,
		BookmarkIngredientKey selectedKey
	) {
		return state.getOrCreateTooltip(sourceKey, keys, selectedKey, INGREDIENT_MANAGER).orElseThrow();
	}

	private static BookmarkIngredientKey key(String ingredient) {
		return new BookmarkIngredientKey(TYPE.getUid(), ingredient, typed(ingredient));
	}

	private static ITypedIngredient<String> typed(String ingredient) {
		return new TestTypedIngredient(TYPE, ingredient);
	}

	private static IIngredientManager ingredientManager() {
		IIngredientRenderer<String> renderer = new IIngredientRenderer<>() {
			@Override
			public void render(GuiGraphics guiGraphics, String ingredient) {
			}

			@Override
			public List<Component> getTooltip(String ingredient, TooltipFlag tooltipFlag) {
				return List.of();
			}
		};
		return (IIngredientManager) Proxy.newProxyInstance(
			IIngredientManager.class.getClassLoader(),
			new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> renderer
		);
	}

	private record TestTypedIngredient(IIngredientType<String> type, String ingredient) implements ITypedIngredient<String> {
		@Override
		public IIngredientType<String> getType() {
			return type;
		}

		@Override
		public String getIngredient() {
			return ingredient;
		}
	}
}
