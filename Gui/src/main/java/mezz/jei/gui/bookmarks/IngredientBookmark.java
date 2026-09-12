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
		String uniqueId = ingredientHelper.getUniqueId(typedIngredient.getIngredient(), UidContext.Ingredient);
		return new IngredientBookmark<>(typedIngredient, uniqueId);
	}

	public static <T> IngredientBookmark<T> createPreservingAmount(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		String uniqueId = ingredientHelper.getUniqueId(typedIngredient.getIngredient(), UidContext.Ingredient);
		return new IngredientBookmark<>(typedIngredient, uniqueId);
	}

	public static <T> IngredientBookmark<T> createWithAmount(
		ITypedIngredient<T> typedIngredient,
		long amount,
		IIngredientManager ingredientManager
	) {
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		T ingredient = ingredientHelper.copyWithAmount(typedIngredient.getIngredient(), amount);
		ITypedIngredient<T> ingredientWithAmount = ingredientManager.createTypedIngredient(typedIngredient.getType(), ingredient, false)
			.orElseThrow();
		return createPreservingAmount(ingredientWithAmount, ingredientManager);
	}

	IngredientBookmark(ITypedIngredient<T> typedIngredient, Object uid) {
		this(typedIngredient, uid, null);
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

	@Override
	public BookmarkType getType() {
		return BookmarkType.INGREDIENT;
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
	public String toString() {
		return "IngredientBookmark{" +
			"uid=" + uid +
			", typedIngredient=" + typedIngredient +
			", visible=" + visible +
			'}';
	}

	@Override
	public int hashCode() {
		return equalityScope == null ? uid.hashCode() : Objects.hash(equalityScope, uid);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof IngredientBookmark<?> ingredientBookmark) {
			if (!Objects.equals(ingredientBookmark.equalityScope, equalityScope)) {
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
