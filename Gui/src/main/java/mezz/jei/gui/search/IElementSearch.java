package mezz.jei.gui.search;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public interface IElementSearch {
	<T> void add(IListElementInfo<T> info, IIngredientManager ingredientManager);

	Collection<IListElement<?>> getAllIngredients();

	void update(
		Collection<IListElementInfo<?>> updatedInfos,
		Set<ElementSearchIndex> rebuildingIndexes,
		Collection<IListElementInfo<?>> allInfos
	);

	void rebuildSearchIndexes(
		Set<ElementSearchIndex> searchIndexes,
		Collection<IListElementInfo<?>> elementInfos
	);

	Set<IListElement<?>> getSearchResults(ElementPrefixParser.TokenInfo tokenInfo);

	/** Streams hits without deduplication for collectors such as recipe-search bitsets. */
	default void collectSearchResults(ElementPrefixParser.TokenInfo tokenInfo, Consumer<IListElement<?>> consumer) {
		getSearchResults(tokenInfo).forEach(consumer);
	}

	@Nullable
	<T> IListElement<T> findElement(ITypedIngredient<T> ingredient, IIngredientHelper<T> ingredientHelper);

	void logStatistics();
}
