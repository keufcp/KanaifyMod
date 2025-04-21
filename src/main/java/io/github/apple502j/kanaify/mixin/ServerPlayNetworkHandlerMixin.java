package io.github.apple502j.kanaify.mixin;

import com.github.ucchyocean.lc3.japanize.Japanizer;
import io.github.apple502j.kanaify.Kanaifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;

@Mixin(value = ServerPlayNetworkHandler.class, priority = 1001)
public class ServerPlayNetworkHandlerMixin { // ! 変更
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("kanaify");
    
    @ModifyArg(method = "handleDecoratedMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V"), index = 0)
    private SignedMessage convertMessage(SignedMessage message){
        String original = message.getSignedContent();
        //? 5文字以内なら処理しない
        if (original.isBlank() || original.length() < 5 || !Japanizer.needsJapanize(original)){
            return message;
        }
        try {
            // 非同期処理が完了するまで待機して、結果を返す
            SignedMessage result = Kanaifier.INSTANCE.convert(original).thenApply(converted -> {
                return SignedMessage.ofUnsigned("%s (§6%s§f)".formatted(original, converted));
            }).exceptionally(e -> {
                LOGGER.error("Failed to kanaify: {}", original);
                LOGGER.error("Caused by:", e.getCause());
                return message; // エラー時には元のメッセージを返す
            }).get(); // 非同期処理の結果を取得する

            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to process message synchronously: {}", e.getMessage());
            return message; // 例外が発生した場合は元のメッセージを返す
        }
    }
}
