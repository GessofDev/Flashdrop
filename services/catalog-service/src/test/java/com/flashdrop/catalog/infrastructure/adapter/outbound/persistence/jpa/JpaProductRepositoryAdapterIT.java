package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.flashdrop.catalog.application.usecase.CreateProductUseCase;
import com.flashdrop.catalog.application.usecase.GetProductsByIdsUseCase;
import com.flashdrop.catalog.application.usecase.ListProductsUseCase;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.ProductController;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.ProductImageUrlResolver;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.RestExceptionHandler;

@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres")
@Import(JpaProductRepositoryAdapter.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers(disabledWithoutDocker = true)
class JpaProductRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private final JpaProductRepositoryAdapter repository;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private MockMvc mockMvc;
    private Product activeProduct;

    JpaProductRepositoryAdapterIT(JpaProductRepositoryAdapter repository,
                                  JdbcTemplate jdbc, EntityManager entityManager) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        jdbc.update("insert into categories (id, name) values (10, 'Category A'), (20, 'Category B')");
        jdbc.update("insert into restaurant (id, user_id, name) values (100, 1, 'Store A'), (200, 2, 'Store B')");
        activeProduct = saveProduct(10L, 100L, "active-a", true);
        saveProduct(10L, 100L, "inactive-a", false);
        saveProduct(20L, 100L, "active-b", true);
        saveProduct(10L, 200L, "active-c", true);
        saveProduct(20L, 200L, "inactive-b", false);
        entityManager.flush();
        entityManager.clear();

        // Only unused write/validation use cases are mocked. Public GET uses real JPA and PostgreSQL.
        ProductController controller = new ProductController(new ListProductsUseCase(repository),
                mock(GetProductsByIdsUseCase.class), mock(CreateProductUseCase.class),
                new ProductImageUrlResolver("/catalog/images"));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void findAllAvailable_mixedProducts_returnsOnlyActiveProducts() {
        assertThat(repository.findAllAvailable()).extracting(Product::getName)
                .containsExactlyInAnyOrder("active-a", "active-b", "active-c");
    }

    @Test
    void findByCategoryIdAndAvailableTrue_mixedProducts_filtersCategoryAndAvailability() {
        assertThat(repository.findByCategoryIdAndAvailableTrue(10L)).extracting(Product::getName)
                .containsExactlyInAnyOrder("active-a", "active-c");
    }

    @Test
    void findByRestaurantIdAndAvailableTrue_mixedProducts_filtersRestaurantAndAvailability() {
        assertThat(repository.findByRestaurantIdAndAvailableTrue(100L)).extracting(Product::getName)
                .containsExactlyInAnyOrder("active-a", "active-b");
    }

    @Test
    void listAvailable_combinedFilters_returnsOnlyActiveMatchingProducts() {
        assertThat(new ListProductsUseCase(repository).executeAvailable(10L, 100L))
                .extracting(Product::getName).containsExactly("active-a");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,NULL,active-a;active-b;active-c",
            "10,NULL,active-a;active-c",
            "NULL,100,active-a;active-b",
            "10,100,active-a"
    }, nullValues = "NULL")
    void publicCatalog_mixedPersistedProducts_neverExposesInactiveProducts(
            Long categoryId, Long restaurantId, String expectedNames) throws Exception {
        MockHttpServletRequestBuilder request = get("/catalog/products");
        if (categoryId != null) {
            request.param("categoryId", categoryId.toString());
        }
        if (restaurantId != null) {
            request.param("restaurantId", restaurantId.toString());
        }
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(expectedNames.split(";"))));
    }

    @Test
    void publicCatalog_onlyInactiveMatches_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/catalog/products").param("categoryId", "20").param("restaurantId", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void update_deactivatedProduct_disappearsFromPublicQueriesButRemainsForOwner() {
        repository.update(new Product(activeProduct.getId(), 10L, 100L,
                activeProduct.getName(), activeProduct.getDescription(), activeProduct.getPrice(), null, false));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllAvailable()).extracting(Product::getName)
                .containsExactlyInAnyOrder("active-b", "active-c");
        assertThat(repository.findByCategoryIdAndAvailableTrue(10L)).extracting(Product::getName)
                .containsExactly("active-c");
        assertThat(repository.findByRestaurantIdAndAvailableTrue(100L)).extracting(Product::getName)
                .containsExactly("active-b");
        assertThat(new ListProductsUseCase(repository).executeAvailable(10L, 100L)).isEmpty();
        assertThat(repository.findByRestaurantId(100L)).extracting(Product::getName)
                .containsExactlyInAnyOrder("active-a", "inactive-a", "active-b");
        assertThat(repository.findById(activeProduct.getId())).hasValueSatisfying(
                product -> assertThat(product.isAvailable()).isFalse());
    }

    private Product saveProduct(Long categoryId, Long restaurantId, String name, boolean available) {
        return repository.save(new Product(null, categoryId, restaurantId, name, "QA",
                new Money(BigDecimal.valueOf(2500)), null, available));
    }
}
