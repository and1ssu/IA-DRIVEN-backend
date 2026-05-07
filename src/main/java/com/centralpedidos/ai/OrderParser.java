package com.centralpedidos.ai;

import java.time.LocalDate;

public interface OrderParser {
    PedidoEstruturado parse(String textoLivre, LocalDate dataReferencia);
}
