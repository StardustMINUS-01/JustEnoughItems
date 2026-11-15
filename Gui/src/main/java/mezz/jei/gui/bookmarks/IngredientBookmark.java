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
		typedIngredient = ingredientManager.normalizeTypedIngredient(typedIngredient);
		return createPreservingAmount(typedIngredient, ingredientManager);
	}

	public static <T> IngredientBookmark<T> createPreservingAmount(ITypedIngredient<T> typedIngredient, IIngredientManager ingredientManager) {
		if (typedIngredient.getIngredient() instanceof ItemStack stack) {
			return new IngredientBookmark<>(typedIngredient, new ItemStackIdentity(stack));
		}
		IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
		// JsonElement is slow for hash/equals, so use the string representation as the uid
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
		int hash = 31 * typedIngredient.getType().hashCode() + uid.hashCode();
		return 31 * hash + Objects.hashCode(equalityScope);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof IngredientBookmark<?> ingredientBookmark) {
			return ingredientBookmark.typedIngredient.getType().equals(typedIngredient.getType()) &&
				Objects.equals(ingredientBookmark.equalityScope, equalityScope) &&
				ingredientBookmark.uid.equals(uid);
		}
		return false;
	}

	// Keep the identity stack private so changes to the displayed ingredient cannot invalidate collection membership.
	private static final class ItemStackIdentity {
		private final ItemStack stack;
		private final int hashCode;

		private ItemStackIdentity(ItemStack stack) {
			this.stack = stack.copy();
			this.hashCode = 31 * ItemStack.hashItemAndComponents(this.stack) + this.stack.getCount();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ItemStackIdentity other && ItemStack.matches(stack, other.stack);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}
	}
}
