package com.centralpedidos.ai;

import com.centralpedidos.model.ItemPedido;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleBasedOrderParser implements OrderParser {
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "(\\d+)\\s+((?:caixas?|fardos?|unidades?|pacotes?|sacos?|kg|quilos?|litros?|garrafas?|latas?)\\s+(?:de\\s+)?)?(.+?)(?=(?:\\s+(?:e|,|;)\\s*\\d+)|(?:\\s*,\\s*\\d+)|(?:\\s+para\\s+entrega)|(?:\\s+com\\s+entrega)|(?:\\s+entrega)|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern BR_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");
    private static final Pattern CLIENTE_PATTERN = Pattern.compile(
            "(?:cliente\\s*:|cliente\\s+|para\\s+o\\s+cliente\\s+)([\\p{L}\\p{M}\\s.'-]+?)(?=,|;|\\.|\\s+quer|\\s+pedi|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Override
    public PedidoEstruturado parse(String textoLivre, LocalDate dataReferencia) {
        String texto = textoLivre == null ? "" : textoLivre.trim();
        List<ItemPedido> itens = extractItens(texto);
        return new PedidoEstruturado(
                extractCliente(texto),
                itens,
                extractDataEntrega(texto, dataReferencia),
                "fallback-heuristico"
        );
    }

    private static List<ItemPedido> extractItens(String texto) {
        List<ItemPedido> itens = new ArrayList<>();
        Matcher matcher = ITEM_PATTERN.matcher(texto);
        while (matcher.find()) {
            int quantidade = Integer.parseInt(matcher.group(1));
            String unidade = normalizeUnidade(matcher.group(2));
            String produto = cleanProduto(matcher.group(3));
            if (!produto.isBlank()) {
                itens.add(new ItemPedido(produto, quantidade, unidade));
            }
        }
        return itens;
    }

    private static String extractCliente(String texto) {
        Matcher matcher = CLIENTE_PATTERN.matcher(texto);
        if (matcher.find()) {
            String cliente = matcher.group(1).trim();
            if (!cliente.isBlank()) {
                return cliente;
            }
        }
        return "desconhecido";
    }

    private static LocalDate extractDataEntrega(String texto, LocalDate dataReferencia) {
        String semAcentos = stripAccents(texto).toLowerCase(Locale.ROOT);
        if (semAcentos.contains("depois de amanha")) {
            return dataReferencia.plusDays(2);
        }
        if (semAcentos.contains("amanha")) {
            return dataReferencia.plusDays(1);
        }
        if (semAcentos.contains("hoje")) {
            return dataReferencia;
        }

        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(texto);
        if (isoMatcher.find()) {
            return LocalDate.parse(isoMatcher.group(1));
        }

        Matcher brMatcher = BR_DATE_PATTERN.matcher(texto);
        if (brMatcher.find()) {
            int day = Integer.parseInt(brMatcher.group(1));
            int month = Integer.parseInt(brMatcher.group(2));
            int year = brMatcher.group(3) == null ? dataReferencia.getYear() : parseYear(brMatcher.group(3));
            try {
                LocalDate candidate = LocalDate.of(year, month, day);
                if (brMatcher.group(3) == null && candidate.isBefore(dataReferencia)) {
                    return candidate.plusYears(1);
                }
                return candidate;
            } catch (DateTimeException ignored) {
                return null;
            }
        }

        return null;
    }

    private static int parseYear(String value) {
        int year = Integer.parseInt(value);
        return year < 100 ? 2000 + year : year;
    }

    private static String normalizeUnidade(String unidade) {
        if (unidade == null || unidade.isBlank()) {
            return null;
        }
        String normalized = unidade
                .toLowerCase(Locale.ROOT)
                .replace(" de", "")
                .trim();
        if (normalized.endsWith("s") && normalized.length() > 2 && !"kg".equals(normalized)) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String cleanProduto(String produto) {
        return produto
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?i)\\bpara\\s+entrega\\b.*$", "")
                .replaceAll("(?i)\\bcom\\s+entrega\\b.*$", "")
                .replaceAll("(?i)\\bentrega\\b.*$", "")
                .replaceAll("[,.;]+$", "")
                .trim();
    }

    private static String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
