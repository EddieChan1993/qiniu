/**
 *
 */
package org.code4everything.qiniu;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhazhapan.util.Checker;

/**
 * @author pantao
 *
 */
public class GsonTest {

	public String configJson = "{accesskey:123456,secretkey:abcdef,buckets:[{bucket:zhazhapan,zone:华东}]}";

	@Test
	public void testGson() {
		JsonObject json = new JsonParser().parse(configJson).getAsJsonObject();
		JsonElement buckets = json.get("buckets");
		if (Checker.isNotNull(buckets)) {
			JsonArray array = buckets.getAsJsonArray();
			for (JsonElement element : array) {
				System.out.println(((JsonObject) element).get("bucket").getAsString());
			}
		}
	}
}
