package co.edu.uco.ucomap.model;

/**
 * A qué apunta el lugar de un evento: un salón/lugar (Room.roomId), un punto de interés del mapa (GraphNode POI)
 * o un edificio entero (Building.buildingId), p. ej. un coliseo o una cancha sin salones.
 */
public enum EventPlaceType {
    ROOM,
    POI,
    BUILDING
}
