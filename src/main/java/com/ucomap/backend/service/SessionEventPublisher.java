package com.ucomap.backend.service;

import com.ucomap.backend.dto.StatsDTO;
import com.ucomap.backend.model.DeviceSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestiona las conexiones SSE activas y emite eventos en tiempo real
 * cada vez que se registra o actualiza una sesion de dispositivo.
 */
@Slf4j
@Service
public class SessionEventPublisher {

    /** Lista thread-safe de clientes SSE conectados */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registra un nuevo cliente SSE.
     * Se llama cuando el frontend hace GET /api/sessions/stream.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // sin timeout

        emitters.add(emitter);
        log.info("Cliente SSE conectado — total activos: {}", emitters.size());

        // Limpiar el emitter de la lista cuando se cierre/falle
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("Cliente SSE desconectado — total activos: {}", emitters.size());
        });
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    /**
     * Emite un evento a todos los clientes conectados cuando hay un ping nuevo.
     * Envia dos eventos:
     *  - "session"  : el DeviceSession actualizado/creado
     *  - "stats"    : las estadisticas globales actualizadas
     */
    public void publishPing(DeviceSession session, StatsDTO stats) {
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data(session));

                emitter.send(SseEmitter.event()
                        .name("stats")
                        .data(stats));

            } catch (IOException e) {
                dead.add(emitter);
            }
        }

        emitters.removeAll(dead);
    }

    /** Numero de clientes SSE conectados en este momento. */
    public int connectedClients() {
        return emitters.size();
    }
}

