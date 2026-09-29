package co.edu.uco.ucomap.dto;

/** Malla 3D de un mapa MultiSet con URL firmada temporal y su pose dentro del map set. */
public record MapMeshDTO(
        String name,
        String url,
        Position position,
        Rotation rotation
) {
    public record Position(double x, double y, double z) {}
    public record Rotation(double qx, double qy, double qz, double qw) {}
}
