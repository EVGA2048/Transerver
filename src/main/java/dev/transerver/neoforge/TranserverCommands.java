package dev.transerver.neoforge;

import com.mojang.brigadier.Command;
import dev.transerver.api.NodeStatus;
import dev.transerver.api.TranserverServices;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TranserverMod.MOD_ID)
public final class TranserverCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("transerver")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("identity").executes(context -> identity(context.getSource()))));
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        var api = TranserverServices.api();
        if (api.isEmpty()) {
            source.sendFailure(Component.literal("Transerver 节点未运行；请检查 enabled、Router 地址和密钥。"));
            return 0;
        }
        NodeStatus status = api.orElseThrow().status();
        ChatFormatting linkColor = status.transportUp() ? ChatFormatting.GREEN : ChatFormatting.RED;
        source.sendSuccess(() -> Component.literal("Transerver ")
                .append(Component.literal(status.transportUp() ? "已连接" : "未连接").withStyle(linkColor)), false);
        source.sendSuccess(() -> Component.literal("节点：" + status.nodeId()), false);
        source.sendSuccess(() -> Component.literal("队列：发出 " + status.outboxDepth()
                + "，接收 " + status.inboxDepth()
                + "，回执 " + status.outgoingReceiptDepth()
                + "，待处理结果 " + status.completedSendDepth()
                + "，死信 " + status.deadLetterDepth()), false);
        if (!status.lastFailure().isBlank()) {
            source.sendSuccess(() -> Component.literal("最近错误：" + status.lastFailure())
                    .withStyle(ChatFormatting.RED), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int identity(net.minecraft.commands.CommandSourceStack source) {
        var identity = TranserverServices.identity();
        if (identity.isEmpty()) {
            source.sendFailure(Component.literal("节点身份尚未加载。"));
            return 0;
        }
        var value = identity.orElseThrow();
        source.sendSuccess(() -> Component.literal(value.alias() + " · " + value.fingerprint())
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(value.nodeId().toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private TranserverCommands() {
    }
}
