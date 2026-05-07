package com.centralpedidos.repository;

import com.centralpedidos.model.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository {
    Pedido save(Pedido pedido);

    List<Pedido> findAll();

    Optional<Pedido> findById(int id);
}
