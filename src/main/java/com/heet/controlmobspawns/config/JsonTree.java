package com.heet.controlmobspawns.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Merge and diff of JSON trees. In an override, a JSON null removes the key from the base.
 */
public final class JsonTree {
	private JsonTree() {
	}

	public static JsonObject merge(JsonObject base, JsonObject override) {
		JsonObject result = base.deepCopy();
		for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value.isJsonNull()) {
				result.remove(key);
			} else if (value.isJsonObject() && result.get(key) instanceof JsonObject baseChild) {
				result.add(key, merge(baseChild, value.getAsJsonObject()));
			} else {
				result.add(key, value.deepCopy());
			}
		}
		return result;
	}

	/** Returns the smallest override that turns {@code base} into {@code target} when merged. */
	public static JsonObject diff(JsonObject base, JsonObject target) {
		JsonObject result = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : target.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			JsonElement baseValue = base.get(key);
			if (baseValue instanceof JsonObject baseChild && value instanceof JsonObject targetChild) {
				JsonObject child = diff(baseChild, targetChild);
				if (!child.isEmpty()) {
					result.add(key, child);
				}
			} else if (!value.equals(baseValue)) {
				result.add(key, value.deepCopy());
			}
		}
		for (String key : base.keySet()) {
			if (!target.has(key)) {
				result.add(key, JsonNull.INSTANCE);
			}
		}
		return result;
	}
}
