package com.centralpedidos.service;

import com.centralpedidos.ai.OrderParser;
import com.centralpedidos.ai.PedidoEstruturado;
import com.centralpedidos.model.Pedido;
import com.centralpedidos.repository.PedidoRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

public final class PedidoService {
    private final PedidoRepository repository;
    private final OrderParser parser;
    private final Clock clock;

    public PedidoService(PedidoRepository repository, OrderParser parser, Clock clock) {
        this.repository = repository;
        this.parser = parser;
        this.clock = clock;
    }

    public Pedido criarPedido(String textoLivre) {
        if (textoLivre == null || textoLivre.isBlank()) {
            throw new IllegalArgumentException("Informe o pedido em texto livre");
        }

        PedidoEstruturado estruturado = parser.parse(textoLivre.trim(), LocalDate.now(clock));
        if (estruturado.itens().isEmpty()) {
            throw new IllegalArgumentException("Nao foi possivel identificar itens com quantidade no pedido");
        }

        Pedido pedido = new Pedido(
                null,
                estruturado.cliente(),
                estruturado.itens(),
                estruturado.dataEntrega(),
                textoLivre,
                estruturado.origemParser(),
                Instant.now(clock)
        );
        return repository.save(pedido);
    }

    public List<Pedido> listarPedidos() {
        return repository.findAll();
    }

    public Pedido buscarPedido(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido nao encontrado"));
    }
}
