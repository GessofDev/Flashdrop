package com.flashdrop.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arranca el contexto completo de Spring y verifica la cadena de filtros real.
 *
 * <p>Es el único test que ejercita el wiring de verdad: {@code UseCaseConfiguration},
 * {@code SecurityConfig} y el registro de {@code InternalApiKeyFilter}. Los demás
 * tests montan los controladores con {@code standaloneSetup}, que se saltea
 * Spring Security por completo — con ellos solos, un error de configuración de
 * rutas pasa desapercibido hasta que alguien levanta el servicio.
 *
 * <p>No abre conexiones: las propiedades de Supabase apuntan a un puerto muerto
 * y ningún caso de prueba llega a la capa de persistencia. Solo se verifican
 * rutas que resuelven dentro de los filtros.
 */
@SpringBootTest(properties = {
        "supabase.url=http://localhost:0",
        "supabase.service-role-key=clave-de-prueba",
        "services.internal-api-key=clave-interna-de-prueba",
        "jwt.allow-ephemeral-key=true"
})
@AutoConfigureMockMvc
// Perfil supabase: este test verifica la cadena de filtros, no la
// persistencia, y asi no necesita levantar un DataSource.
@ActiveProfiles("supabase")
class ApplicationStartupTest {

    @Autowired
    MockMvc mvc;

    /** Si algún bean no resuelve, este test falla antes que ningún otro. */
    @Test
    void elContextoArranca() {
        // El fallo se manifiesta en la inicializacion del contexto.
    }

    /**
     * El gateway consulta /health en cada ciclo de agregacion de estado.
     * Estaba cayendo en el denyAll de SecurityConfig y devolvia 403.
     */
    @Test
    void healthEsPublicoYResponde200() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    /**
     * Verifica que InternalApiKeyFilter esté efectivamente montado en la cadena
     * real, no solo en el test de MockMvc standalone. Sin la cabecera no se
     * llega al controlador, asi que no hay llamada a Supabase.
     */
    @Test
    void endpointsInternosRechazanSinApiKey() throws Exception {
        mvc.perform(get("/api/internal/users/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    /** Mismo control sobre la variante batch. */
    @Test
    void batchInternoRechazaSinApiKey() throws Exception {
        mvc.perform(get("/api/internal/users").param("ids", "1,2"))
                .andExpect(status().isForbidden());
    }

    /** Ruta no declarada en SecurityConfig: debe caer en el denyAll. */
    @Test
    void rutaDesconocidaQuedaDenegadaPorDefecto() throws Exception {
        mvc.perform(get("/una-ruta-que-no-existe"))
                .andExpect(status().is4xxClientError());
    }
}
