package com.centralpedidos.http;

import com.centralpedidos.model.ItemPedido;
import com.centralpedidos.model.Pedido;
import com.centralpedidos.service.PedidoService;
import com.centralpedidos.util.Json;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

public final class ApiServer {
    private final HttpServer httpServer;
    private final PedidoService pedidoService;

    public ApiServer(int port, PedidoService pedidoService) throws IOException {
        this.pedidoService = pedidoService;
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.httpServer.createContext("/pedido", this::handlePedido);
        this.httpServer.createContext("/pedidos", this::handlePedidos);
        this.httpServer.createContext("/health", this::handleHealth);
        this.httpServer.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        httpServer.start();
    }

    private void handlePedido(HttpExchange exchange) throws IOException {
        withCors(exchange);
        if (isOptions(exchange)) {
            sendEmpty(exchange, 204);
            return;
        }

        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("POST".equalsIgnoreCase(method) && "/pedido".equals(path)) {
                String texto = extractTexto(exchange);
                Pedido pedido = pedidoService.criarPedido(texto);
                sendJson(exchange, 201, pedidoToMap(pedido));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && path.startsWith("/pedido/")) {
                int id = parseId(path.substring("/pedido/".length()));
                Pedido pedido = pedidoService.buscarPedido(id);
                sendJson(exchange, 200, pedidoToMap(pedido));
                return;
            }

            sendError(exchange, 405, "Metodo ou rota nao suportada");
        } catch (IllegalArgumentException error) {
            sendError(exchange, 400, error.getMessage());
        } catch (NoSuchElementException error) {
            sendError(exchange, 404, error.getMessage());
        } catch (Exception error) {
            error.printStackTrace();
            sendError(exchange, 500, "Erro interno ao processar pedido");
        }
    }

    private void handlePedidos(HttpExchange exchange) throws IOException {
        withCors(exchange);
        if (isOptions(exchange)) {
            sendEmpty(exchange, 204);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Metodo nao suportado");
            return;
        }

        List<Map<String, Object>> pedidos = pedidoService.listarPedidos().stream()
                .map(ApiServer::pedidoToMap)
                .toList();
        sendJson(exchange, 200, pedidos);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        withCors(exchange);
        if (isOptions(exchange)) {
            sendEmpty(exchange, 204);
            return;
        }
        sendJson(exchange, 200, Map.of("status", "ok"));
    }

    private static String extractTexto(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (body.isBlank()) {
            throw new IllegalArgumentException("Informe o pedido em texto livre");
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if ((contentType != null && contentType.toLowerCase().contains("application/json")) || body.startsWith("{")) {
            Map<String, Object> payload = Json.parseObject(body);
            Object texto = firstPresent(payload, "texto", "pedido", "text");
            if (texto == null || String.valueOf(texto).isBlank()) {
                throw new IllegalArgumentException("JSON deve conter o campo texto");
            }
            return String.valueOf(texto).trim();
        }

        return body;
    }

    private static Object firstPresent(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private static int parseId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("ID invalido");
        }
    }

    private static Map<String, Object> pedidoToMap(Pedido pedido) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", pedido.id());
        map.put("cliente", pedido.cliente());
        map.put("itens", pedido.itens().stream().map(ApiServer::itemToMap).toList());
        map.put("data_entrega", pedido.dataEntrega() == null ? null : pedido.dataEntrega().toString());
        map.put("texto_original", pedido.textoOriginal());
        map.put("origem_parser", pedido.origemParser());
        map.put("criado_em", pedido.criadoEm().toString());
        return map;
    }

    private static Map<String, Object> itemToMap(ItemPedido item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("produto", item.produto());
        map.put("quantidade", item.quantidade());
        map.put("unidade", item.unidade());
        return map;
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("erro", message));
    }

    private static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void withCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type,Authorization");
    }

    private static boolean isOptions(HttpExchange exchange) {
        return "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod());
    }
}
