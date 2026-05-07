package com.centralpedidos.ai;

import com.centralpedidos.model.ItemPedido;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record PedidoEstruturado(
        String cliente,
        List<ItemPedido> itens,
        LocalDate dataEntrega,
        String origemParser
) {
    public PedidoEstruturado {
        cliente = cliente == null || cliente.isBlank() ? "desconhecido" : cliente.trim();
        itens = List.copyOf(Objects.requireNonNullElse(itens, List.of()));
        origemParser = origemParser == null || origemParser.isBlank() ? "desconhecido" : origemParser.trim();
    }
}
