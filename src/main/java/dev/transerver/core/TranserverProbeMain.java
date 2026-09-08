package dev.transerver.core;

import dev.transerver.api.DeliveryResult;
import dev.transerver.api.SendOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public final class TranserverProbeMain {
    private static final String PROBE_CHANNEL = "transerver:probe";

    private TranserverProbeMain() {
    }

    public static void run(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: transerver probe [configuration.properties]");
        }
        Path file = Path.of(args.length == 1 ? args[0] : "transerver-probe.properties");
        ProbeConfiguration configuration = ProbeConfiguration.load(file);
        RouteResolver routes = destination -> Optional.of(configuration.routerUri());
        var transport = new HttpTransport(configuration.nodeId(), routes,
                new HmacAuthenticator(configuration.networkSecret()));
        var node = new TranserverNode(configuration.nodeId(), transport,
                new FileMessageStore(configuration.dataDirectory()));
        node.registerHandler(PROBE_CHANNEL, message -> {
            String text = new String(message.payload(), StandardCharsets.UTF_8);
            System.out.println("\n[received " + message.source() + " -> " + message.destination() + "] " + text);
            System.out.print("> ");
            return CompletableFuture.completedFuture(DeliveryResult.APPLIED);
        });

        try (var runtime = new TranserverRuntime(node, configuration.pollInterval());
             var scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            runtime.start();
            printHelp(configuration.nodeId());
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.equals("quit") || line.equals("exit")) {
                    break;
                }
                if (line.equals("status")) {
                    System.out.println(node.status());
                    continue;
                }
                if (line.equals("help")) {
                    printHelp(configuration.nodeId());
                    continue;
                }
                if (line.startsWith("send ")) {
                    send(node, runtime, line);
                    continue;
                }
                if (!line.isEmpty()) {
                    System.out.println("Unknown command. Type help.");
                }
            }
        }
    }

    private static void send(TranserverNode node, TranserverRuntime runtime, String command) {
        String[] parts = command.split("\\s+", 3);
        if (parts.length < 3) {
            System.out.println("Usage: send <destination> <text>");
            return;
        }
        var handle = node.send(parts[1], PROBE_CHANNEL,
                parts[2].getBytes(StandardCharsets.UTF_8), SendOptions.defaults());
        System.out.println("Queued " + handle.messageId());
        handle.completion().whenComplete((receipt, error) -> {
            if (error != null) {
                System.out.println("\n[failed " + handle.messageId() + "] " + error.getMessage());
            } else {
                System.out.println("\n[" + receipt.state() + " " + receipt.messageId() + "] " + receipt.detail());
            }
            System.out.print("> ");
        });
        runtime.wake();
    }

    private static void printHelp(String nodeId) {
        System.out.println("Transerver probe node: " + nodeId);
        System.out.println("Commands: send <destination> <text> | status | help | quit");
    }
}
