package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.AppSettingDTO;
import co.edu.uco.ucomap.model.AppSetting;
import co.edu.uco.ucomap.model.SettingType;
import co.edu.uco.ucomap.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSettingServiceTest {

    private static final String AR_FLAG = "enableIndoorARNavigation";

    @Mock AppSettingRepository repository;
    @InjectMocks AppSettingService service;

    private static AppSetting setting(String value, boolean active) {
        return AppSetting.builder().key(AR_FLAG).value(value).type(SettingType.BOOLEAN).active(active).build();
    }

    @Test
    void inactiveSettingIsNotExposed() {
        when(repository.findById(AR_FLAG)).thenReturn(Optional.of(setting("true", false)));

        assertThatThrownBy(() -> service.findByKey(AR_FLAG))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void typedValueIsParsed() {
        when(repository.findById(AR_FLAG)).thenReturn(Optional.of(setting("true", true)));

        assertThat(service.getValueAs(AR_FLAG, Boolean.class, false)).isTrue();
    }

    @Test
    void invalidOrMissingValueFallsBackToDefault() {
        when(repository.findById("maxRetries")).thenReturn(Optional.of(
                AppSetting.builder().key("maxRetries").value("muchos").active(true).build()));
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.getValueAs("maxRetries", Integer.class, 3)).isEqualTo(3);
        assertThat(service.getValueAs("missing", Integer.class, 7)).isEqualTo(7);
    }

    @Test
    void duplicateKeyIsRejected() {
        when(repository.existsById(AR_FLAG)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new AppSettingDTO.Request(AR_FLAG, "true", SettingType.BOOLEAN, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
    }

    @Test
    void newSettingDefaultsToStringType() {
        when(repository.existsById("welcome")).thenReturn(false);
        when(repository.save(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        AppSettingDTO.Response created = service.create(new AppSettingDTO.Request("welcome", "Hola", null, null, "ui"));

        assertThat(created.getType()).isEqualTo(SettingType.STRING);
        assertThat(created.isActive()).isTrue();
    }
}
