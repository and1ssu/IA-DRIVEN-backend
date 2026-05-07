package com.centralpedidos.model;

import java.util.Locale;

public record ItemPedido(
        String produto,
        int quantidade,
        String unidade
) {
    public ItemPedido {
        if (produto == null || produto.isBlank()) {
            throw new IllegalArgumentException("Produto do item e obrigatorio");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        }
        produto = produto.trim().toLowerCase(Locale.ROOT);
        unidade = unidade == null || unidade.isBlank() ? null : unidade.trim().toLowerCase(Locale.ROOT);
    }
}
