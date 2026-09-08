package dev.transerver.core;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class TranserverRouterMain {
    private TranserverRouterMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Usage: transerver [configuration.properties]");
            System.exit(2);
        }
        Path configurationFile = Path.of(args.length == 1 ? args[0] : "transerver-router.properties");
        RouterConfiguration configuration = RouterConfiguration.load(configurationFile);
        var store = new FileMessageStore(configuration.dataDirectory());
        var router = new FileRouterTransport(store, configuration.nodes());
        var authenticator = new HmacAuthenticator(configuration.networkSecret());
        var stopped = new CountDownLatch(1);

        try (var server = new HttpRouterServer(configuration.listenAddress(), router, authenticator)) {
            Runtime.getRuntime().addShutdownHook(new Thread(stopped::countDown, "transerver-shutdown"));
            server.start();
            System.out.println("Transerver Router listening on " + configuration.listenAddress());
            System.out.println("Configured nodes: " + String.join(", ", configuration.nodes()));
            stopped.await();
        }
    }
}
