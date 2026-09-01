package com.flashdrop.auth;

import org.junit.jupiter.api.Test;
import com.flashdrop.auth.application.dto.TokenClaims;
import com.flashdrop.auth.application.port.outbound.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    /** Emisor real de JWT, para no validar contra un caso de uso simulado. */
    @Autowired
    TokenService tokens;

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

    /**
     * Gap QA: comprobar sobre la cadena real que las rutas declaradas publicas
     * efectivamente lo son. Un permitAll mal escrito no se nota hasta que el
     * gateway o la app dejan de poder entrar.
     */
    @Test
    void lasRutasPublicasDeclaradasSonAlcanzables() throws Exception {
        // No se comprueba el codigo exacto sino que la peticion atraviese la
        // cadena de seguridad: lo que importa es que no responda 401 ni 403.
        for (String ruta : List.of("/auth/.well-known/jwks.json", "/health",
                                   "/actuator/health")) {
            mvc.perform(get(ruta))
                    .andExpect(result -> {
                        int codigo = result.getResponse().getStatus();
                        if (codigo == 401 || codigo == 403) {
                            throw new AssertionError(
                                    "La ruta publica " + ruta + " respondio " + codigo);
                        }
                    });
        }
    }

    /**
     * Gap QA: /auth/validate ejercitado con un JWT real emitido por el propio
     * servicio, no con un caso de uso simulado. Recorre la firma, el issuer y
     * la extraccion de claims de punta a punta.
     */
    @Test
    void validateAceptaUnJwtRealYDevuelveSusClaims() throws Exception {
        String jwt = tokens.issue(new TokenClaims(42L, "real@flashdrop.cl", List.of("Cliente")));

        mvc.perform(get("/auth/validate").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("real@flashdrop.cl"))
                .andExpect(jsonPath("$.roles[0]").value("Cliente"));
    }

    @Test
    void validateRechazaUnJwtManipulado() throws Exception {
        String jwt = tokens.issue(new TokenClaims(42L, "real@flashdrop.cl", List.of("Cliente")));
        String[] partes = jwt.split("\\.");
        char primero = partes[2].charAt(0);
        String manipulado = partes[0] + "." + partes[1] + "."
                + (primero == 'A' ? 'B' : 'A') + partes[2].substring(1);

        mvc.perform(get("/auth/validate").header("Authorization", "Bearer " + manipulado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void validateSinCabeceraDevuelve401() throws Exception {
        mvc.perform(get("/auth/validate"))
                .andExpect(status().isUnauthorized());
    }
}
