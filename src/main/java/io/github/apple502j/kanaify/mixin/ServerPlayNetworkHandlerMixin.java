package io.github.apple502j.kanaify.mixin;

import com.github.ucchyocean.lc3.japanize.Japanizer;
import io.github.apple502j.kanaify.Kanaifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1001)
public class ServerPlayNetworkHandlerMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("kanaify");

    @ModifyArg(method = "broadcastChatMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"), index = 0)
    private PlayerChatMessage convertMessage(PlayerChatMessage message) {
        String original = message.signedContent();
        //? 5文字以内なら処理しない
        if (original.isBlank() || original.length() < 5 || !Japanizer.needsJapanize(original)) {
            return message;
        }
        try {
            // 非同期処理が完了するまで待機して、結果を返す
            PlayerChatMessage result = Kanaifier.INSTANCE.convert(original).thenApply(converted -> {
                return PlayerChatMessage.system("%s (§6%s§f)".formatted(original, converted));
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
