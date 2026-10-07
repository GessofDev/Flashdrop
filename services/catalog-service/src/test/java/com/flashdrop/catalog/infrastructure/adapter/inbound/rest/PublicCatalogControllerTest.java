package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.flashdrop.catalog.application.usecase.CreateProductUseCase;
import com.flashdrop.catalog.application.usecase.GetProductsByIdsUseCase;
import com.flashdrop.catalog.application.usecase.ListProductsUseCase;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;

/**
 * Plan de pruebas §3.3: {@code GET /catalog/products} usa las consultas que filtran
 * {@code is_available = true}, para no exponer productos desactivados al cliente.
 *
 * <p>Slice web con {@code MockMvc} standalone y el caso de uso <b>simulado</b>
 * ({@link ListProductsUseCase}, Mockito): no levanta Spring Boot. Se comprueba que el
 * controller llama siempre a las variantes {@code executeAvailable(...)} y nunca a las que
 * no filtran ({@code execute(...)}). La consulta que filtra en la base de datos pertenece a
 * la capa de repositorio (integración, fuera de esta prueba). El contrato público completo
 * (categorías, restaurantes, alta interna) se prueba en {@code PublicCatalogControllerIT}.</p>
 */
class PublicCatalogControllerTest {

    private ListProductsUseCase listProductsUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        listProductsUseCase = mock(ListProductsUseCase.class);
        ProductController controller = new ProductController(
                listProductsUseCase,
                mock(GetProductsByIdsUseCase.class),
                mock(CreateProductUseCase.class),
                new ProductImageUrlResolver("/catalog/images"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    private static Product availableProduct(Long id, Long categoryId, Long restaurantId) {
        return new Product(id, categoryId, restaurantId, "Producto " + id, "Descripcion",
                new Money(BigDecimal.valueOf(2500)), "products/2026/10/imagen-" + id + ".webp", true);
    }

    private void assertNeverUsesTheUnfilteredQueries() {
        verify(listProductsUseCase, never()).execute();
        verify(listProductsUseCase, never()).execute(any(), any());
    }

    @Test
    void listProducts_withoutFilters_usesTheAvailableOnlyQuery_andReturnsWhatItGives() throws Exception {
        when(listProductsUseCase.executeAvailable(null, null))
                .thenReturn(List.of(availableProduct(1L, 1L, 10L), availableProduct(2L, 2L, 11L)));

        mockMvc.perform(get("/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].image").value("/catalog/images/products/2026/10/imagen-1.webp"))
                .andExpect(jsonPath("$[1].id").value(2));

        verify(listProductsUseCase).executeAvailable(null, null);
        assertNeverUsesTheUnfilteredQueries();
    }

    @Test
    void listProducts_byCategory_usesTheAvailableOnlyQuery() throws Exception {
        when(listProductsUseCase.executeAvailable(3L, null)).thenReturn(List.of(availableProduct(5L, 3L, 10L)));

        mockMvc.perform(get("/catalog/products").param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(listProductsUseCase).executeAvailable(3L, null);
        assertNeverUsesTheUnfilteredQueries();
    }

    @Test
    void listProducts_byRestaurant_usesTheAvailableOnlyQuery() throws Exception {
        when(listProductsUseCase.executeAvailable(null, 7L)).thenReturn(List.of(availableProduct(6L, 1L, 7L)));

        mockMvc.perform(get("/catalog/products").param("restaurantId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(listProductsUseCase).executeAvailable(null, 7L);
        assertNeverUsesTheUnfilteredQueries();
    }

    @Test
    void listProducts_byCategoryAndRestaurant_usesTheAvailableOnlyQuery() throws Exception {
        when(listProductsUseCase.executeAvailable(3L, 7L)).thenReturn(List.of(availableProduct(8L, 3L, 7L)));

        mockMvc.perform(get("/catalog/products").param("categoryId", "3").param("restaurantId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(listProductsUseCase).executeAvailable(3L, 7L);
        assertNeverUsesTheUnfilteredQueries();
    }

    @Test
    void listProducts_whenNothingIsAvailable_returnsAnEmptyList() throws Exception {
        when(listProductsUseCase.executeAvailable(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
