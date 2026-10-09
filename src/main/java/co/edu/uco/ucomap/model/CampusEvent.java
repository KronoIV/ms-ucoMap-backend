package co.edu.uco.ucomap.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Evento del campus creado en el panel. La app lo anuncia en el inicio solo entre {@code startsAt} y {@code endsAt}
 * y guía hasta su lugar con la navegación normal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "campus_events")
public class CampusEvent {

    @Id
    private String id;

    @NotBlank
    @Size(max = 80)
    private String title;

    @Size(max = 280)
    private String description;

    @NotNull
    private EventPlaceType placeType;

    /** roomId del lugar, nodeId del punto de interés o buildingId del edificio. */
    @NotBlank
    @Size(max = 120)
    private String placeId;

    /** Indicación extra para encontrarlo («Segundo piso, al fondo»). */
    @Size(max = 120)
    private String placeNote;

    @NotNull
    private Instant startsAt;

    @NotNull
    private Instant endsAt;

    /** Pausado desde el panel: no se muestra aunque esté en su horario. */
    @Builder.Default
    private boolean active = true;

    private Instant createdAt;

    private Instant updatedAt;

    /** Copia lo que se edita en el panel (texto recortado; opcional vacío = sin dato). */
    public CampusEvent withFieldsOf(CampusEvent src) {
        title = src.title.trim();
        description = blankToNull(src.description);
        placeType = src.placeType;
        placeId = src.placeId.trim();
        placeNote = blankToNull(src.placeNote);
        startsAt = src.startsAt;
        endsAt = src.endsAt;
        active = src.active;
        return this;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
