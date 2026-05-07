package com.centralpedidos.ai;

import java.time.LocalDate;

public final class ResilientOrderParser implements OrderParser {
    private final AiConfig config;
    private final RuleBasedOrderParser fallbackParser;
    private final OpenAiOrderParser aiParser;

    public ResilientOrderParser(AiConfig config, RuleBasedOrderParser fallbackParser) {
        this.config = config;
        this.fallbackParser = fallbackParser;
        this.aiParser = config.enabled() ? new OpenAiOrderParser(config) : null;
    }

    @Override
    public PedidoEstruturado parse(String textoLivre, LocalDate dataReferencia) {
        if (aiParser == null) {
            if (config.required()) {
                throw new IllegalStateException("AI_REQUIRED=true, mas OPENAI_API_KEY/AI_API_KEY nao foi configurada");
            }
            return fallbackParser.parse(textoLivre, dataReferencia);
        }

        try {
            return aiParser.parse(textoLivre, dataReferencia);
        } catch (RuntimeException error) {
            if (config.required()) {
                throw error;
            }
            System.err.println("Falha no parser de IA; usando fallback heuristico: " + error.getMessage());
            return fallbackParser.parse(textoLivre, dataReferencia);
        }
    }
}
