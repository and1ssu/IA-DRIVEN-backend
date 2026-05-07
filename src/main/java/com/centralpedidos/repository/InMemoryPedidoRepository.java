package com.centralpedidos.repository;

import com.centralpedidos.model.Pedido;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryPedidoRepository implements PedidoRepository {
    private final AtomicInteger sequence = new AtomicInteger();
    private final ConcurrentMap<Integer, Pedido> storage = new ConcurrentHashMap<>();

    @Override
    public Pedido save(Pedido pedido) {
        int id = sequence.incrementAndGet();
        Pedido saved = pedido.withId(id);
        storage.put(id, saved);
        return saved;
    }

    @Override
    public List<Pedido> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparing(Pedido::id).reversed())
                .toList();
    }

    @Override
    public Optional<Pedido> findById(int id) {
        return Optional.ofNullable(storage.get(id));
    }
}
