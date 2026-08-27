package co.edu.uco.ucomap.repository;

import co.edu.uco.ucomap.model.AppSetting;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para parámetros de configuración de la aplicación.
 * La clave ({@code key}) es el ID del documento — las búsquedas por ID
 * son O(1) y no requieren índices adicionales.
 */
@Repository
public interface AppSettingRepository extends MongoRepository<AppSetting, String> {

    /** Retorna todos los parámetros activos. */
    List<AppSetting> findAllByActiveTrue();

    /** Retorna todos los parámetros activos de una categoría específica. */
    List<AppSetting> findByCategoryAndActiveTrue(String category);
}
