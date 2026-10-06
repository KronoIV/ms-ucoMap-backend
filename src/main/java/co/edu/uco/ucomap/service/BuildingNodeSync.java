package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.Building;
import co.edu.uco.ucomap.model.GraphNode;
import co.edu.uco.ucomap.model.NodeType;
import co.edu.uco.ucomap.model.PixelPoint;
import co.edu.uco.ucomap.repository.BuildingRepository;
import co.edu.uco.ucomap.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Correspondencia uno a uno entre los edificios de /campus y los nodos BUILDING del mapa.
 * El mapa manda en la ubicación; /campus en el nombre, el color y la categoría de los salones.
 * Usa los repositorios directamente para no llamarse en círculo con GraphService/BuildingService.
 */
@Slf4j
@Service
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class BuildingNodeSync implements ApplicationRunner {

    private static final String[] PALETTE = {
            "#D84315", "#3BE0D3", "#D84563", "#7C3AED", "#2563EB", "#16A34A", "#CA8A04", "#DB2777",
    };

    private final BuildingRepository  buildingRepository;
    private final GraphNodeRepository nodeRepository;

    @Value("${app.data.init-on-startup:true}")
    private boolean syncOnStartup;

    public static String nodeIdOf(Building building) {
        return hasText(building.getNodeId()) ? building.getNodeId() : building.getBuildingId();
    }

    /** Edificio que ubica este nodo (enlazado explícitamente o con el mismo ID). */
    public Optional<Building> buildingOf(String nodeId) {
        return buildingRepository.findFirstByNodeId(nodeId)
                .or(() -> buildingRepository.findById(nodeId).filter(b -> nodeId.equals(nodeIdOf(b))));
    }

    /** Tras guardar un nodo en el mapa: todo BUILDING activo tiene su edificio, y al dejar de serlo se oculta. */
    public void nodeSaved(GraphNode node, boolean labelChanged) {
        Optional<Building> linked = buildingOf(node.getNodeId());
        if (node.getNodeType() != NodeType.BUILDING || !node.isActive() || node.getGps() == null) {
            linked.filter(Building::isActive).ifPresent(b -> {
                b.setActive(false);
                buildingRepository.save(b);
                log.info("Edificio oculto: su nodo ya no es un edificio activo — buildingId={}", b.getBuildingId());
            });
            return;
        }
        Building building = linked.orElseGet(() -> newBuildingFor(node));
        building.setNodeId(node.getNodeId());
        building.setGps(node.getGps());
        building.setActive(true);
        if (labelChanged && hasText(node.getLabel())) building.setLabel(node.getLabel().trim());
        buildingRepository.save(building);
    }

    /**
     * Tras guardar un edificio en /campus: actualiza su nodo BUILDING en el mapa.
     * Solo se crea al crear el edificio: uno antiguo sin punto (p. ej. «Otros») no aparece solo en el mapa.
     */
    public void buildingSaved(Building building, boolean createNode) {
        String nodeId = nodeIdOf(building);
        GraphNode node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null && (!createNode || building.getGps() == null)) return;
        if (node == null) node = GraphNode.builder().nodeId(nodeId).pixel(new PixelPoint(0, 0)).build();
        if (building.getGps() != null) node.setGps(building.getGps());
        node.setLabel(building.getLabel());
        node.setNodeType(NodeType.BUILDING);
        node.setActive(building.isActive());
        nodeRepository.save(node);
        if (!nodeId.equals(building.getNodeId())) {
            building.setNodeId(nodeId);
            buildingRepository.save(building);
        }
    }

    /** El edificio se borró en /campus: su punto sale del mapa (sus caminos dejan de usarse). */
    public void buildingDeleted(Building building) {
        nodeRepository.findById(nodeIdOf(building))
                .filter(n -> n.getNodeType() == NodeType.BUILDING && n.isActive())
                .ifPresent(n -> {
                    n.setActive(false);
                    nodeRepository.save(n);
                    log.info("Nodo del edificio desactivado — nodeId={}", n.getNodeId());
                });
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!syncOnStartup) return;
        try {
            reconcile();
        } catch (RuntimeException e) {
            log.warn("No se pudo sincronizar edificios y nodos al iniciar: {}", e.getMessage());
        }
    }

    /** Enlaza los datos existentes: cada edificio con su nodo y cada nodo BUILDING sin edificio recibe uno. */
    public void reconcile() {
        List<GraphNode> nodes = nodeRepository.findByActiveTrue().stream()
                .filter(n -> n.getNodeType() == NodeType.BUILDING && n.getGps() != null)
                .toList();
        Set<String> linked = new HashSet<>();
        int updated = 0;
        for (Building building : buildingRepository.findAll()) {
            GraphNode node = match(building, nodes, linked);
            if (node == null) continue;
            linked.add(node.getNodeId());
            if (node.getNodeId().equals(building.getNodeId()) && Objects.equals(building.getGps(), node.getGps())
                    && building.isActive()) continue;
            building.setNodeId(node.getNodeId());
            building.setGps(node.getGps());
            building.setActive(true);
            buildingRepository.save(building);
            updated++;
        }
        int created = 0;
        for (GraphNode node : nodes) {
            if (linked.contains(node.getNodeId())) continue;
            buildingRepository.save(newBuildingFor(node));
            created++;
        }
        if (updated + created > 0) log.info("Edificios sincronizados con el mapa — actualizados={} creados={}", updated, created);
    }

    /** Mismo ID, la categoría igual al ID del nodo o el mismo nombre. */
    private static GraphNode match(Building building, List<GraphNode> nodes, Set<String> taken) {
        List<GraphNode> free = nodes.stream().filter(n -> !taken.contains(n.getNodeId())).toList();
        String id = nodeIdOf(building);
        String label = normalize(building.getLabel());
        return free.stream().filter(n -> n.getNodeId().equals(id)).findFirst()
                .or(() -> free.stream().filter(n -> n.getNodeId().equalsIgnoreCase(building.getCategory())).findFirst())
                .or(() -> free.stream().filter(n -> !label.isEmpty()
                        && (label.equals(normalize(n.getLabel())) || label.equals(normalize(n.getNodeId())))).findFirst())
                .orElse(null);
    }

    private Building newBuildingFor(GraphNode node) {
        String label = hasText(node.getLabel()) ? node.getLabel().trim() : node.getNodeId();
        Building building = Building.builder()
                .buildingId(uniqueBuildingId(node.getNodeId()))
                .nodeId(node.getNodeId())
                .label(label)
                .category(uniqueCategory(node.getNodeId()))
                .color(PALETTE[(int) (buildingRepository.count() % PALETTE.length)])
                .gps(node.getGps())
                .active(true)
                .build();
        log.info("Edificio creado desde el mapa — buildingId={} nodeId={}", building.getBuildingId(), node.getNodeId());
        return building;
    }

    private String uniqueBuildingId(String base) {
        String id = base;
        for (int i = 2; buildingRepository.existsById(id); i++) id = base + "-" + i;
        return id;
    }

    private String uniqueCategory(String base) {
        String category = base;
        for (int i = 2; buildingRepository.existsByCategoryIgnoreCase(category); i++) category = base + "-" + i;
        return category;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
