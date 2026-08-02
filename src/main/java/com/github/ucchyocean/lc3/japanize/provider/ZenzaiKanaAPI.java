package com.github.ucchyocean.lc3.japanize.provider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.apple502j.kanaify.Kanaifier;

public class ZenzaiKanaAPI implements Provider {
    public static Provider INSTANCE = new ZenzaiKanaAPI();
    private static final String BASE_URL = "KANAIFY_ZENZAI_URL";
    private static final String PATH = "/v1/convert?text=";
    private static final Gson GSON = new Gson();

    // 末尾のスラッシュを落とす．"/" だけを与えられた場合は空になり，使用不可と判定される．
    private static String baseUrl() {
        String url = System.getenv(BASE_URL);
        if (url == null) {
            return "";
        }
        url = url.strip();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public boolean isUsable() {
        return !baseUrl().isEmpty();
    }

    public CompletableFuture<String> fetch(Kanaifier kanaifier, String message) {
        return kanaifier.performGet(baseUrl() + PATH + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    public String parse(String value) {
        JsonObject gson = GSON.fromJson(value, JsonObject.class);
        return gson.get("result").getAsString();
    }
}
