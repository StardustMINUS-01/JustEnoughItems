package mezz.jei.gui.config.file.serializers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mezz.jei.gui.bookmarks.BookmarkIngredientKey;

public final class BookmarkIngredientKeySerializer {
	private BookmarkIngredientKeySerializer() {
	}

	public static JsonObject serialize(BookmarkIngredientKey key) {
		JsonObject json = new JsonObject();
		json.addProperty("type", key.ingredientTypeUid());
		json.addProperty("uid", key.ingredientUid());
		if (key.serializedIngredient() != null) {
			json.addProperty("ingredient", key.serializedIngredient());
		}
		return json;
	}

	public static BookmarkIngredientKey deserialize(JsonElement element) {
		if (element.isJsonPrimitive()) {
			return BookmarkIngredientKey.legacy(element.getAsString());
		}
		JsonObject json = element.getAsJsonObject();
		String type = json.has("type") ? json.get("type").getAsString() : BookmarkIngredientKey.UNKNOWN_TYPE_UID;
		String uid = json.has("uid") ? json.get("uid").getAsString() : "fallback:unknown";
		String ingredient = json.has("ingredient") ? json.get("ingredient").getAsString() : null;
		return new BookmarkIngredientKey(type, uid, ingredient);
	}
}
