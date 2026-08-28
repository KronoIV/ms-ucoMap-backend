package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.StatsDTO;
import co.edu.uco.ucomap.model.DeviceSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SessionEventPublisher {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registra un nuevo cliente SSE y le envia el estado actual inmediatamente
     * para que el dashboard no quede en blanco hasta el proximo ping.
     */
    public SseEmitter subscribe(StatsDTO initialStats) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.add(emitter);
        log.info("Cliente SSE conectado — total activos: {}", emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("Cliente SSE desconectado — total activos: {}", emitters.size());
        });
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Estado inicial para que el cliente no espere el primer ping
        try {
            emitter.send(SseEmitter.event()
                    .name("stats")
                    .data(initialStats, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Emite session + stats a todos los clientes tras cada ping.
     * Usa APPLICATION_JSON explícito para evitar ambigüedad en la negociación de conversor.
     */
    public void publishPing(DeviceSession session, StatsDTO stats) {
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data(session, MediaType.APPLICATION_JSON));

                emitter.send(SseEmitter.event()
                        .name("stats")
                        .data(stats, MediaType.APPLICATION_JSON));

            } catch (IOException e) {
                dead.add(emitter);
            }
        }

        emitters.removeAll(dead);
    }

    /**
     * Heartbeat cada 25 s para evitar que proxies (nginx, ELB) cierren la conexion
     * por inactividad (timeout tipico: 60 s).
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    public int connectedClients() {
        return emitters.size();
    }
}

