package co.edu.uco.ucomap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado de los permisos que necesita la app, tal como lo ve el teléfono en ese momento.
 * camera / location: granted | prompt | denied | blocked | unavailable | error.
 * motion (solo iPhone pide permiso): granted | denied | not-required | unknown.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSnapshot {
    private String camera;
    private String location;
    private String motion;
}
