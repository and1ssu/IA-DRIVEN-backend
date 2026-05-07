package com.centralpedidos.ai;

import com.centralpedidos.model.ItemPedido;
import com.centralpedidos.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiOrderParser implements OrderParser {
    private final AiConfig config;
    private final HttpClient httpClient;

    public OpenAiOrderParser(AiConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
    }

    @Override
    public PedidoEstruturado parse(String textoLivre, LocalDate dataReferencia) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model());
        payload.put("temperature", 0);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", systemPrompt()
                ),
                Map.of(
                        "role", "user",
                        "content", "Data de referencia: " + dataReferencia + "\nPedido em texto livre: " + textoLivre
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint())
                .timeout(config.timeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(payload)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("IA retornou HTTP " + response.statusCode() + ": " + response.body());
            }
            return fromAiResponse(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede ao chamar IA", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada de IA interrompida", e);
        }
    }

    private URI endpoint() {
        String base = config.baseUrl().toString();
        if (base.endsWith("/")) {
            return URI.create(base + "chat/completions");
        }
        return URI.create(base + "/chat/completions");
    }

    private PedidoEstruturado fromAiResponse(String body) {
        Map<String, Object> response = Json.parseObject(body);
        List<?> choices = getList(response, "choices");
        if (choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> firstChoice)) {
            throw new IllegalStateException("Resposta da IA sem choices");
        }

        Object message = firstChoice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("Resposta da IA sem message");
        }

        Object content = messageMap.get("content");
        if (!(content instanceof String jsonContent) || jsonContent.isBlank()) {
            throw new IllegalStateException("Resposta da IA sem content JSON");
        }

        Map<String, Object> parsed = Json.parseObject(stripCodeFence(jsonContent));
        return toPedidoEstruturado(parsed);
    }

    private PedidoEstruturado toPedidoEstruturado(Map<String, Object> parsed) {
        String cliente = stringOrDefault(parsed.get("cliente"), "desconhecido");
        LocalDate dataEntrega = parseDate(parsed.get("data_entrega"));

        List<ItemPedido> itens = new ArrayList<>();
        for (Object item : getList(parsed, "itens")) {
            if (!(item instanceof Map<?, ?> rawItem)) {
                continue;
            }
            String produto = stringOrDefault(rawItem.get("produto"), "");
            int quantidade = intValue(rawItem.get("quantidade"));
            String unidade = stringOrDefault(rawItem.get("unidade"), null);
            if (!produto.isBlank() && quantidade > 0) {
                itens.add(new ItemPedido(produto, quantidade, unidade));
            }
        }

        return new PedidoEstruturado(cliente, itens, dataEntrega, "ia:" + config.model());
    }

    private static String systemPrompt() {
        return """
                Voce estrutura pedidos comerciais escritos em portugues do Brasil.
                Responda apenas com JSON valido, sem markdown, neste formato:
                {
                  "cliente": "nome do cliente ou desconhecido",
                  "itens": [
                    { "produto": "produto em minusculas, sem unidade de embalagem", "quantidade": 10, "unidade": "caixa" }
                  ],
                  "data_entrega": "YYYY-MM-DD ou null"
                }

                Regras:
                - Use a data de referencia enviada pelo usuario para resolver datas relativas como hoje, amanha e depois de amanha.
                - Nao invente cliente, itens ou data.
                - Separe unidade/logistica do produto: "10 caixas de leite integral" vira produto "leite integral", quantidade 10, unidade "caixa".
                - Remova termos de entrega do nome do produto.
                - Se a quantidade estiver ausente ou ambigua, nao inclua o item.
                """;
    }

    private static List<?> getList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        return trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }

    private static String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return LocalDate.parse(text);
    }
}
