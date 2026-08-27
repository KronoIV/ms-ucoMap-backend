package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.AppSetting;
import co.edu.uco.ucomap.model.SettingType;
import co.edu.uco.ucomap.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Sembrador de parámetros de configuración predeterminados.
 *
 * Solo inserta los parámetros que NO existen en la base de datos,
 * garantizando idempotencia (seguro ejecutar múltiples veces / reinicios).
 *
 * Para agregar un nuevo parámetro predeterminado, añadir una entrada a
 * {@code DEFAULT_SETTINGS} — sin modificar ninguna otra clase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppSettingInitializer implements CommandLineRunner {

    private final AppSettingRepository repository;

    @Value("${app.data.init-on-startup:true}")
    private boolean initOnStartup;

    /**
     * Parámetros predeterminados que deben existir en la base de datos.
     * Agregar aquí cada nueva feature flag o parámetro de configuración.
     */
    private static final List<AppSetting> DEFAULT_SETTINGS = List.of(

            AppSetting.builder()
                    .key("enableIndoorARNavigation")
                    .value("true")
                    .type(SettingType.BOOLEAN)
                    .description("Habilita la navegación AR indoor. " +
                            "Si es false, al llegar al edificio se muestra una pantalla de llegada " +
                            "en lugar de lanzar la cámara de realidad aumentada.")
                    .category("navigation")
                    .active(true)
                    .updatedAt(Instant.now())
                    .build()

            // Futuros parámetros de ejemplo (descomentar y ajustar):
            // AppSetting.builder()
            //     .key("outdoorArrivalThresholdMeters")
            //     .value("5")
            //     .type(SettingType.NUMBER)
            //     .description("Radio en metros para considerar que el usuario llegó al edificio.")
            //     .category("navigation")
            //     .active(true)
            //     .updatedAt(Instant.now())
            //     .build(),
            //
            // AppSetting.builder()
            //     .key("enableRoomsTab")
            //     .value("true")
            //     .type(SettingType.BOOLEAN)
            //     .description("Muestra u oculta la pestaña de salones en la app.")
            //     .category("ui")
            //     .active(true)
            //     .updatedAt(Instant.now())
            //     .build()
    );

    @Override
    public void run(String... args) {
        if (!initOnStartup) {
            log.info("[AppSettingInitializer] Semilla deshabilitada (app.data.init-on-startup=false)");
            return;
        }

        int seeded = 0;
        for (AppSetting setting : DEFAULT_SETTINGS) {
            if (!repository.existsById(setting.getKey())) {
                repository.save(setting);
                log.info("[AppSettingInitializer] Parámetro sembrado: {} = {}",
                        setting.getKey(), setting.getValue());
                seeded++;
            }
        }

        if (seeded == 0) {
            log.info("[AppSettingInitializer] Todos los parámetros predeterminados ya existen.");
        } else {
            log.info("[AppSettingInitializer] {} parámetro(s) sembrado(s).", seeded);
        }
    }
}
