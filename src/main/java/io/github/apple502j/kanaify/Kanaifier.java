package io.github.apple502j.kanaify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.github.ucchyocean.lc3.japanize.Japanizer;
import com.github.ucchyocean.lc3.japanize.provider.Provider;
import com.github.ucchyocean.lc3.japanize.provider.Providers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.util.Util;

public final class Kanaifier {
    public static final Logger LOGGER = LoggerFactory.getLogger("kanaifier");
    // リクエストのタイムアウト (20 秒) より後に置く．通常はそちらが先に働く．
    private static final long FALLBACK_TIMEOUT_SECONDS = 25L;
    private final Provider kanaProvider;
    private final HttpClient client;
    public static Kanaifier INSTANCE = new Kanaifier();

    private Kanaifier() {
        this.kanaProvider = Providers.get();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10L))
                .executor(Util.getMainWorkerExecutor())
                .build();
        LOGGER.info("Using {} kana provider", this.kanaProvider.getName());
    }

    public CompletableFuture<String> performGet(String url) {
        return this.request("GET", URI.create(url), HttpRequest.BodyPublishers.noBody(), Map.of());
    }

    public CompletableFuture<String> request(String method, URI uri, HttpRequest.BodyPublisher publisher, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest
                .newBuilder(uri)
                .method(method, publisher)
                .timeout(Duration.ofSeconds(20L));

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.header(entry.getKey(), entry.getValue());
        }

        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApplyAsync((resp) -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        throw new UncheckedIOException(new IOException(String.format(Locale.ROOT, "Page returned code %d", resp.statusCode())));
                    }

                    return resp.body();
                });
    }

    // 呼び出し元は元のメッセージを差し止めてから結果を待つため，例外を投げず必ず完了する
    // future を返す．そうしないとメッセージが配信されないまま失われる．
    public CompletableFuture<String> convert(String romaji) {
        final String japanized;
        try {
            japanized = Japanizer.japanize(romaji);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to japanize: {}", romaji, e);
            return CompletableFuture.completedFuture(romaji);
        }

        try {
            return this.kanaProvider.fetch(this, japanized)
                    .thenApply(this.kanaProvider::parse)
                    .exceptionally((e) -> {
                        LOGGER.error("Failed to kanaify: {}", japanized, e);
                        return japanized;
                    })
                    // 変換結果が空でも原文だけを配信しないよう，かなへ落とす．
                    .thenApply((converted) -> converted == null || converted.isBlank() ? japanized : converted)
                    // 応答が返らなくてもメッセージが消えないよう，必ず完了させる．
                    .completeOnTimeout(japanized, FALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            // 設定が不正で URI を組み立てられない場合など，future を返す前に失敗する経路．
            LOGGER.error("Failed to kanaify: {}", japanized, e);
            return CompletableFuture.completedFuture(japanized);
        }
    }
}
