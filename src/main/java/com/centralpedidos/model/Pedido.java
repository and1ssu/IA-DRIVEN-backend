package com.centralpedidos.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Pedido(
        Integer id,
        String cliente,
        List<ItemPedido> itens,
        LocalDate dataEntrega,
        String textoOriginal,
        String origemParser,
        Instant criadoEm
) {
    public Pedido {
        cliente = cliente == null || cliente.isBlank() ? "desconhecido" : cliente.trim();
        itens = List.copyOf(itens == null ? List.of() : itens);
        textoOriginal = textoOriginal == null ? "" : textoOriginal.trim();
        origemParser = origemParser == null || origemParser.isBlank() ? "desconhecido" : origemParser.trim();
        criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    }

    public Pedido withId(int novoId) {
        return new Pedido(novoId, cliente, itens, dataEntrega, textoOriginal, origemParser, criadoEm);
    }
}
