package co.edu.uco.ucomap.model;

/** Tipo de nodo en el grafo del campus. */
public enum NodeType {
    /** Edificio destino (EDC, COLEGIO…) */
    BUILDING,
    /** Entrada principal al campus (E1, E2, E3) */
    ENTRANCE,
    /** Puerta de un edificio, sobre la fachada y conectada al nodo BUILDING: ahí termina la ruta exterior */
    DOOR,
    /** Intersección o punto de paso interno (P1–P15…) */
    WAYPOINT,
    /** Punto de interés (cafetería, baños…): destino de la app; su clase va en GraphNode.poiType */
    POI
}

