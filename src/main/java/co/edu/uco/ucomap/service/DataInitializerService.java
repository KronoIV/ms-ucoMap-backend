package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.*;
import co.edu.uco.ucomap.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Siembra la base de datos con todos los datos del campus UCO
 * si las colecciones están vacías. Se ejecuta una única vez al arranque.
 *
 * Para forzar un re-seed: borrar los documentos en MongoDB Atlas
 * o cambiar INIT_DATA=false en .env y hacer seed manual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitializerService {

    private final GraphNodeRepository graphNodeRepo;
    private final GraphEdgeRepository graphEdgeRepo;
    private final BuildingRepository  buildingRepo;
    private final RoomRepository      roomRepo;
    private final PoiClipRepository   poiClipRepo;

    @Value("${app.data.init-on-startup:true}")
    private boolean initOnStartup;

    @PostConstruct
    public void init() {
        if (!initOnStartup) {
            log.info("DataInitializer desactivado (INIT_DATA=false)");
            return;
        }
        seedGraphNodes();
        seedGraphEdges();
        seedBuildings();
        seedRooms();
        seedPoiClips();
        log.info("✅ DataInitializer completado — base de datos lista");
    }

    // =========================================================
    // GRAPH NODES
    // =========================================================

    private void seedGraphNodes() {
        if (graphNodeRepo.count() > 0) return;

        List<GraphNode> nodes = List.of(
            // ── Edificios destino ────────────────────────────────
            node("EDC",     NodeType.BUILDING,  "EDC",
                 6.151164296413953,  -75.3665822995127),
            node("COLEGIO", NodeType.BUILDING,  "COLEGIO",
                 6.149879545997976,  -75.3660539173641),

            // ── Entradas del campus ──────────────────────────────
            node("E1",  NodeType.ENTRANCE, "Entrada 1",
                 6.149554545318648,  -75.36742240149525),
            node("E2",  NodeType.ENTRANCE, "Entrada 2",
                 6.148940504718373,  -75.36624372399544),
            node("E3",  NodeType.ENTRANCE, "Entrada 3",
                 6.150593632727961,  -75.36703093407297),

            // ── Intersecciones internas (waypoints) ──────────────
            node("P1",  NodeType.WAYPOINT, null, 6.149463, -75.366370),
            node("P2",  NodeType.WAYPOINT, null, 6.149370, -75.367230),
            node("P3",  NodeType.WAYPOINT, null, 6.149595, -75.366564),
            node("P4",  NodeType.WAYPOINT, null, 6.149600, -75.367500),
            node("P5",  NodeType.WAYPOINT, null, 6.149679, -75.366713),
            node("P6",  NodeType.WAYPOINT, null, 6.149791, -75.367063),
            node("P7",  NodeType.WAYPOINT, null, 6.149926, -75.366562),
            node("P8",  NodeType.WAYPOINT, null, 6.150700, -75.367800),
            node("P9",  NodeType.WAYPOINT, null, 6.150826, -75.366551),
            node("P10", NodeType.WAYPOINT, null, 6.150669, -75.366463),
            node("P11", NodeType.WAYPOINT, null, 6.150640, -75.366286),
            node("P12", NodeType.WAYPOINT, null, 6.150103, -75.366097),
            node("P13", NodeType.WAYPOINT, null, 6.151259, -75.366795),
            node("P14", NodeType.WAYPOINT, null, 6.150695, -75.366897),
            node("P15", NodeType.WAYPOINT, null, 6.150522, -75.366957)
        );

        graphNodeRepo.saveAll(nodes);
        log.info("  ↳ {} GraphNodes sembrados", nodes.size());
    }

    private GraphNode node(String id, NodeType type, String label, double lat, double lng) {
        return GraphNode.builder()
                .nodeId(id)
                .nodeType(type)
                .label(label)
                .gps(new GpsPoint(lat, lng))
                .build();
    }

    // =========================================================
    // GRAPH EDGES
    // =========================================================

    private void seedGraphEdges() {
        if (graphEdgeRepo.count() > 0) return;

        List<String[]> pairs = List.of(
            new String[]{"E2",  "P1"},
            new String[]{"P1",  "P2"},
            new String[]{"P1",  "P3"},
            new String[]{"P2",  "P4"},
            new String[]{"P4",  "COLEGIO"},
            new String[]{"P4",  "P12"},
            new String[]{"P12", "P11"},
            new String[]{"P11", "P10"},
            new String[]{"P10", "P9"},
            new String[]{"P9",  "P8"},
            new String[]{"P8",  "EDC"},
            new String[]{"P2",  "P7"},
            new String[]{"P7",  "P5"},
            new String[]{"P3",  "P5"},
            new String[]{"P5",  "P6"},
            new String[]{"P6",  "E1"},
            new String[]{"E3",  "P15"},
            new String[]{"P15", "P14"},
            new String[]{"P14", "P13"},
            new String[]{"P13", "EDC"}
        );

        List<GraphEdge> edges = pairs.stream()
                .map(p -> GraphEdge.builder()
                        .id(UUID.randomUUID().toString())
                        .nodeA(p[0])
                        .nodeB(p[1])
                        .build())
                .toList();

        graphEdgeRepo.saveAll(edges);
        log.info("  ↳ {} GraphEdges sembrados", edges.size());
    }

    // =========================================================
    // BUILDINGS
    // =========================================================

    private void seedBuildings() {
        if (buildingRepo.count() > 0) return;

        List<Building> buildings = List.of(
            Building.builder()
                .buildingId("EDC")
                .label("EDC")
                .color("#D84315")
                .category("EDC")
                .gps(new GpsPoint(6.151164296413953, -75.3665822995127))
                .build(),
            Building.builder()
                .buildingId("COLEGIO")
                .label("COLEGIO")
                .color("#3be0d3")
                .category("CO")
                .gps(new GpsPoint(6.149879545997976, -75.3660539173641))
                .build(),
                    Building.builder()
                        .buildingId("M")
                        .label("MADRE DE LA SABIDURIA")
                        .color("#D84315")
                        .category("M")
                        .gps(new GpsPoint(6.1497146345952505, -75.36588140237029))
                        .build(),
            Building.builder()
                .buildingId("OTROS")
                .label("Otros")
                .color("#9C27B0")
                .category("Otros")
                .gps(new GpsPoint(6.149879545997976, -75.3660539173641))
                .build()
        );

        buildingRepo.saveAll(buildings);
        log.info("  ↳ {} Buildings sembrados", buildings.size());
    }

    // =========================================================
    // ROOMS
    // =========================================================

    private void seedRooms() {
        if (roomRepo.count() > 0) return;

        List<Room> rooms = List.of(
            // ── Bloque CO ─────────────────────────────────────────
            room("205", "CO 205",    "CO",   "co205"),
            room("306", "CO 306",    "CO",   "co306"),
            room("303", "CO 303",    "CO",   "co303"),
            room("204", "CO 204",    "CO",   "co204"),
            room("201", "CO 201",    "CO",   "co201"),
            room("207", "CO 207",    "CO",   "co207"),
            room("202", "CO 202",    "CO",   "co202"),
            room("206", "CO 206",    "CO",   "co206"),
            room("304", "CO 304",    "CO",   "co304"),
            room("305", "CO 305",    "CO",   "co305"),
            room("307", "CO 307",    "CO",   "co307"),
            room("203", "CO 203",    "CO",   "co203"),
            room("301", "CO 301",    "CO",   "co301"),
            room("302", "CO 302",    "CO",   "co302"),

            // ── Bloque EDC ────────────────────────────────────────
            room("EDC",                   "EDC",                            "EDC", "EDC"),
            room("L2",                    "Laboratorio de Cómputo L2",      "EDC", "L2"),
            room("Telecomunicaciones",    "Telecomunicaciones",             "EDC", "Telecomunicaciones"),
            room("Laboratorio Molecular", "Laboratorio Biología Molecular", "EDC", "Laboratorio_Molecular"),
            room("Laboratorio Física",    "Laboratorio de Física",          "EDC", "Laboratorio_Fisica"),

            // ── Otros ─────────────────────────────────────────────
            room("Capilla",                    "Capilla",                   "Otros", "Capilla"),
            room("Dirección de Investigación", "Dirección de Investigación","Otros", "Direccion_deinvestigacion")
        );

        roomRepo.saveAll(rooms);
        log.info("  ↳ {} Rooms sembrados", rooms.size());
    }

    private Room room(String roomId, String name, String category, String stateId) {
        return Room.builder()
                .roomId(roomId)
                .name(name)
                .category(category)
                .stateId(stateId)
                .build();
    }

    // =========================================================
    // POI CLIPS
    // =========================================================

    private void seedPoiClips() {
        if (poiClipRepo.count() > 0) return;

        List<PoiClip> clips = List.of(
            clip("co205",                    "CO 205"),
            clip("co306",                    "CO 306"),
            clip("EDC",                      "EDC"),
            clip("L2",                       "Laboratorio de Cómputo L2"),
            clip("co303",                    "CO 303"),
            clip("co204",                    "CO 204"),
            clip("co201",                    "CO 201"),
            clip("Telecomunicaciones",       "Telecomunicaciones"),
            clip("Laboratorio_Molecular",    "Laboratorio Biología Molecular"),
            clip("co207",                    "CO 207"),
            clip("co202",                    "CO 202"),
            clip("Capilla",                  "Capilla"),
            clip("Direccion_deinvestigacion","Dirección de Investigación"),
            clip("co206",                    "CO 206"),
            clip("co304",                    "CO 304"),
            clip("Laboratorio_Fisica",       "Laboratorio de Física"),
            clip("co302",                    "CO 302"),
            clip("co305",                    "CO 305"),
            clip("co307",                    "CO 307"),
            clip("co203",                    "CO 203"),
            clip("co301",                    "CO 301")
        );

        poiClipRepo.saveAll(clips);
        log.info("  ↳ {} PoiClips sembrados", clips.size());
    }

    private PoiClip clip(String id, String name) {
        return PoiClip.builder().clipId(id).displayName(name).build();
    }
}

