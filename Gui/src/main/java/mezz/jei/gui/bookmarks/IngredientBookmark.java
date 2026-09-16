package mezz.jei.gui.bookmarks;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientBookmarkElement;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class IngredientBookmark<T> implements IBookmark {
	private final IElement<T> element;
	private final Object uid;
	private final ITypedIngredient<T> typedIngredient;
	@Nullable
	private final Object equalityScope;
	private boolean visible = true;

	public static <T> IngredientBookmark<T> create(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		typedIngredient = ingredientManager.normalizeTypedIngredient(typedIngredient);
		Object uniqueId = ingredientHelper.getUid(typedIngredient, UidContext.Ingredient);
		return new IngredientBookmark<>(typedIngredient, uniqueId);
	}

	public static <T> IngredientBookmark<T> createPreservingAmount(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		Object uniqueId = ingredientHelper.getUid(typedIngredient, UidContext.Ingredient);
		return new IngredientBookmark<>(typedIngredient, uniqueId);
	}

	private IngredientBookmark(ITypedIngredient<T> typedIngredient, Object uid) {
		this(typedIngredient, uid, null);
	}

	public static <T> IngredientBookmark<T> createWithAmount(ITypedIngredient<T> ingredient, long amount, IIngredientManager ingredientManager) {
		IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(ingredient.getType());
		T copy = helper.copyWithAmount(ingredient.getIngredient(), amount);
		return createPreservingAmount(ingredientManager.createTypedIngredient(ingredient.getType(), copy, false).orElseThrow(), ingredientManager);
	}

	private IngredientBookmark(ITypedIngredient<T> typedIngredient, Object uid, @Nullable Object equalityScope) {
		this.typedIngredient = typedIngredient;
		this.uid = uid;
		this.equalityScope = equalityScope;
		this.element = new IngredientBookmarkElement<>(this);
	}

	public IngredientBookmark<T> withEqualityScope(@Nullable Object equalityScope) {
		return new IngredientBookmark<>(typedIngredient, uid, equalityScope);
	}

	@Nullable
	Object getEqualityScope() {
		return equalityScope;
	}

	public ITypedIngredient<T> getIngredient() {
		return typedIngredient;
	}

	@Override
	public IElement<?> getElement() {
		return element;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public int hashCode() {
		return 31 * uid.hashCode() + Objects.hashCode(equalityScope);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof IngredientBookmark<?> ingredientBookmark) {
			if (!Objects.equals(equalityScope, ingredientBookmark.equalityScope)) {
				return false;
			}
			if (typedIngredient.getIngredient() instanceof ItemStack stackA && ingredientBookmark.typedIngredient.getIngredient() instanceof ItemStack stackB) {
				return ItemStack.matches(stackA, stackB);
			}
			return ingredientBookmark.uid.equals(uid) &&
				ingredientBookmark.typedIngredient.getType().equals(typedIngredient.getType());
		}
		return false;
	}
}
