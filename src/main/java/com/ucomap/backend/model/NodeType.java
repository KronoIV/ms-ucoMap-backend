package com.ucomap.backend.model;

/** Tipo de nodo en el grafo del campus. */
public enum NodeType {
    /** Edificio destino (EDC, COLEGIO…) */
    BUILDING,
    /** Entrada principal al campus (E1, E2, E3) */
    ENTRANCE,
    /** Intersección o punto de paso interno (P1–P15…) */
    WAYPOINT
}

