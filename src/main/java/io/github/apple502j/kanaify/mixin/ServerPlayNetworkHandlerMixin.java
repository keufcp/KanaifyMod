package io.github.apple502j.kanaify.mixin;

import java.util.concurrent.CompletableFuture;

import com.github.ucchyocean.lc3.japanize.Japanizer;
import io.github.apple502j.kanaify.Kanaifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;

@Mixin(value = ServerPlayNetworkHandler.class, priority = 1001)
public abstract class ServerPlayNetworkHandlerMixin extends ServerCommonNetworkHandler {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("kanaify");

    // 変換を待つ間にメインスレッドを止めないため，配信を後回しにする．その結果，
    // 同一プレイヤーの発言が変換時間の差で入れ替わりうるので 1 本の鎖に繋いで直列化する．
    @Unique
    private CompletableFuture<Void> kanaify$chain = CompletableFuture.completedFuture(null);

    // 変換後の配信は同じメソッドを呼び直すため，自身の注入が再発火する．
    @Unique
    private boolean kanaify$rebroadcasting;

    public ServerPlayNetworkHandlerMixin(MinecraftServer server, ClientConnection connection, ConnectedClientData clientData) {
        super(server, connection, clientData);
    }

    @Shadow
    private void handleDecoratedMessage(SignedMessage message) {
        throw new AssertionError();
    }

    @Inject(method = "handleDecoratedMessage", at = @At("HEAD"), cancellable = true)
    private void kanaify$convert(SignedMessage message, CallbackInfo ci) {
        if (this.kanaify$rebroadcasting) {
            return;
        }
        String original = message.getSignedContent();
        //? 5文字以内なら処理しない
        if (original.isBlank() || original.length() < 5 || !Japanizer.needsJapanize(original)) {
            return;
        }

        ci.cancel();

        this.kanaify$chain = this.kanaify$chain
                .thenCompose((ignored) -> Kanaifier.INSTANCE.convert(original))
                .thenAcceptAsync((converted) -> {
                    this.kanaify$rebroadcasting = true;
                    try {
                        this.handleDecoratedMessage(
                                SignedMessage.ofUnsigned("%s (§6%s§f)".formatted(original, converted)));
                    } finally {
                        this.kanaify$rebroadcasting = false;
                    }
                }, this.server)
                .exceptionally((e) -> {
                    LOGGER.error("Failed to broadcast the converted message: {}", original, e);
                    return null;
                });
    }
}
