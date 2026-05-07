package com.centralpedidos;

import com.centralpedidos.ai.AiConfig;
import com.centralpedidos.ai.ResilientOrderParser;
import com.centralpedidos.ai.RuleBasedOrderParser;
import com.centralpedidos.http.ApiServer;
import com.centralpedidos.repository.InMemoryPedidoRepository;
import com.centralpedidos.service.PedidoService;

import java.io.IOException;
import java.time.Clock;

public final class Application {
    private Application() {
    }

    public static void main(String[] args) throws IOException {
        int port = readIntEnv("PORT", 8080);
        AiConfig aiConfig = AiConfig.fromEnvironment();
        ResilientOrderParser parser = new ResilientOrderParser(aiConfig, new RuleBasedOrderParser());
        PedidoService pedidoService = new PedidoService(
                new InMemoryPedidoRepository(),
                parser,
                Clock.systemDefaultZone()
        );

        ApiServer server = new ApiServer(port, pedidoService);
        server.start();

        System.out.printf("Central de Pedidos rodando em http://localhost:%d%n", port);
        System.out.printf("Parser IA: %s | AI_REQUIRED=%s%n",
                aiConfig.enabled() ? aiConfig.model() : "nao configurado",
                aiConfig.required());
    }

    private static int readIntEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
