package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.model.CampusEvent;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.model.Room;
import co.edu.uco.ucomap.repository.CampusEventRepository;
import co.edu.uco.ucomap.repository.GraphNodeRepository;
import co.edu.uco.ucomap.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampusEventService {

    static final Duration MAX_DURATION = Duration.ofDays(90);
    static final int MAX_VISIBLE = 10;

    private final CampusEventRepository eventRepository;
    private final RoomRepository roomRepository;
    private final GraphNodeRepository nodeRepository;

    public List<CampusEvent> findAll() {
        return eventRepository.findAllByOrderByStartsAtDesc();
    }

    /** Los que la app debe mostrar ahora: activos, dentro de su horario y con un lugar al que se pueda llegar. */
    public List<CampusEvent> visibleNow() {
        return visibleAt(Instant.now());
    }

    List<CampusEvent> visibleAt(Instant now) {
        return eventRepository.findByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc(now, now)
                .stream()
                .filter(this::placeAvailable)
                .limit(MAX_VISIBLE)
                .toList();
    }

    public CampusEvent create(CampusEvent event) {
        validate(event);
        Instant now = Instant.now();
        CampusEvent saved = eventRepository.save(CampusEvent.builder()
                .id(UUID.randomUUID().toString())
                .createdAt(now)
                .updatedAt(now)
                .build()
                .withFieldsOf(event));
        log.info("Evento creado — id={} inicio={} fin={}", saved.getId(), saved.getStartsAt(), saved.getEndsAt());
        return saved;
    }

    public CampusEvent update(String id, CampusEvent updated) {
        CampusEvent existing = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND.getMessage()));
        validate(updated);
        existing.withFieldsOf(updated).setUpdatedAt(Instant.now());
        CampusEvent saved = eventRepository.save(existing);
        log.info("Evento actualizado — id={}", id);
        return saved;
    }

    public void delete(String id) {
        if (!eventRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        }
        eventRepository.deleteById(id);
        log.info("Evento eliminado — id={}", id);
    }

    private void validate(CampusEvent e) {
        if (e.getTitle() == null || e.getTitle().isBlank()) {
            throw badRequest(ErrorCode.VALIDATION_ERROR.getMessage());
        }
        if (e.getStartsAt() == null || e.getEndsAt() == null || !e.getEndsAt().isAfter(e.getStartsAt())) {
            throw badRequest("La hora de fin debe ser posterior a la de inicio.");
        }
        if (Duration.between(e.getStartsAt(), e.getEndsAt()).compareTo(MAX_DURATION) > 0) {
            throw badRequest("Un evento puede durar máximo " + MAX_DURATION.toDays() + " días.");
        }
        if (!placeAvailable(e)) {
            throw badRequest("El lugar del evento no existe o está inactivo.");
        }
    }

    /** Un salón borrado o un punto oculto después de crear el evento: la app no podría guiar hasta allí. */
    private boolean placeAvailable(CampusEvent e) {
        String placeId = e.getPlaceId() == null ? "" : e.getPlaceId().trim();
        if (e.getPlaceType() == null || placeId.isEmpty()) return false;
        return switch (e.getPlaceType()) {
            case ROOM -> roomRepository.findByRoomId(placeId).filter(Room::isActive).isPresent();
            case POI -> nodeRepository.findById(placeId)
                    .filter(n -> n.isActive() && n.getNodeType() == NodeType.POI)
                    .isPresent();
        };
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
