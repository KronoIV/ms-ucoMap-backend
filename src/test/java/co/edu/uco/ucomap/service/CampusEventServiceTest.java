package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.CampusEvent;
import co.edu.uco.ucomap.model.EventPlaceType;
import co.edu.uco.ucomap.model.GraphNode;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.model.Room;
import co.edu.uco.ucomap.repository.CampusEventRepository;
import co.edu.uco.ucomap.repository.GraphNodeRepository;
import co.edu.uco.ucomap.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampusEventServiceTest {

    private static final Instant START = Instant.parse("2026-10-09T19:00:00Z");

    @Mock CampusEventRepository eventRepository;
    @Mock RoomRepository roomRepository;
    @Mock GraphNodeRepository nodeRepository;
    @InjectMocks CampusEventService service;

    private static CampusEvent event(EventPlaceType type, String placeId, Instant start, Instant end) {
        return CampusEvent.builder().title("  Feria de proyectos ").description(" ")
                .placeType(type).placeId(placeId).placeNote("Segundo piso")
                .startsAt(start).endsAt(end).build();
    }

    private void givenRoom(String roomId, boolean active) {
        when(roomRepository.findByRoomId(roomId))
                .thenReturn(Optional.of(Room.builder().roomId(roomId).name("CO 101").active(active).build()));
    }

    private void assertBadRequest(CampusEvent e) {
        assertThatThrownBy(() -> service.create(e))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createdEventIsNormalizedAndStamped() {
        givenRoom("101", true);
        when(eventRepository.save(any(CampusEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CampusEvent saved = service.create(event(EventPlaceType.ROOM, "101", START, START.plusSeconds(7200)));

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getTitle()).isEqualTo("Feria de proyectos");
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void endMustBeAfterStart() {
        assertBadRequest(event(EventPlaceType.ROOM, "101", START, START));
        assertBadRequest(event(EventPlaceType.ROOM, "101", START, START.minusSeconds(60)));
    }

    @Test
    void eventCannotLastLongerThanTheLimit() {
        assertBadRequest(event(EventPlaceType.ROOM, "101", START,
                START.plus(CampusEventService.MAX_DURATION).plusSeconds(1)));
    }

    @Test
    void placeMustExistAndBeActive() {
        givenRoom("101", false);
        assertBadRequest(event(EventPlaceType.ROOM, "101", START, START.plusSeconds(60)));

        // Un nodo que no es POI no es un lugar de evento
        when(nodeRepository.findById("P1")).thenReturn(Optional.of(
                GraphNode.builder().nodeId("P1").nodeType(NodeType.WAYPOINT).active(true).build()));
        assertBadRequest(event(EventPlaceType.POI, "P1", START, START.plusSeconds(60)));
    }

    @Test
    void visibleEventsSkipPlacesThatNoLongerExist() {
        Instant now = START.plus(Duration.ofMinutes(30));
        CampusEvent atRoom = event(EventPlaceType.ROOM, "101", START, START.plusSeconds(7200));
        CampusEvent atPoi = event(EventPlaceType.POI, "AUD1", START, START.plusSeconds(7200));
        CampusEvent gone = event(EventPlaceType.ROOM, "999", START, START.plusSeconds(7200));
        when(eventRepository.findByActiveTrueAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtAsc(now, now))
                .thenReturn(List.of(atRoom, atPoi, gone));
        givenRoom("101", true);
        when(roomRepository.findByRoomId("999")).thenReturn(Optional.empty());
        when(nodeRepository.findById("AUD1")).thenReturn(Optional.of(
                GraphNode.builder().nodeId("AUD1").nodeType(NodeType.POI).active(true).build()));

        assertThat(service.visibleAt(now)).containsExactly(atRoom, atPoi);
    }
}
