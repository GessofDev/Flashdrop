package com.flashdrop.delivery.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.*;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeliveryArchitectureTest — NFR-1: Hexagonal Purity")
class DeliveryArchitectureTest {

    @Nested
    @DisplayName("domain package — no framework imports")
    class DomainNoFrameworkImports {

        private com.tngtech.archunit.core.domain.JavaClasses domainClasses() {
            return new ClassFileImporter().importPackages("com.flashdrop.delivery.domain..");
        }

        @Test
        @DisplayName("TC1: domain has no jakarta.persistence field types")
        void domainHasNoJpaFields() {
            var classes = domainClasses();
            NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("jakarta.persistence")));
        }

        @Test
        @DisplayName("TC2: domain has no org.springframework.data field types")
        void domainHasNoSpringDataFields() {
            var classes = domainClasses();
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("org.springframework.data")));
        }

        @Test
        @DisplayName("TC3: domain has no org.springframework.boot field types")
        void domainHasNoSpringBootFields() {
            var classes = domainClasses();
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("org.springframework.boot")));
        }

        @Test
        @DisplayName("TC4: domain has no com.supabase field types")
        void domainHasNoSupabaseFields() {
            var classes = domainClasses();
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("com.supabase")));
        }
    }

    @Nested
    @DisplayName("application package — no framework imports")
    class ApplicationNoFrameworkImports {

        private com.tngtech.archunit.core.domain.JavaClasses appClasses() {
            return new ClassFileImporter().importPackages("com.flashdrop.delivery.application..");
        }

        @Test
        @DisplayName("TC5: application has no jakarta.persistence field types")
        void applicationHasNoJpaFields() {
            var classes = appClasses();
            NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("jakarta.persistence")));
        }

        @Test
        @DisplayName("TC6: application has no org.springframework.data field types")
        void applicationHasNoSpringDataFields() {
            var classes = appClasses();
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("org.springframework.data")));
        }

        @Test
        @DisplayName("TC7: application has no org.springframework.boot field types")
        void applicationHasNoSpringBootFields() {
            var classes = appClasses();
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("org.springframework.boot")));
        }

        @Test
        @DisplayName("TC8: application has no com.supabase field types")
        void applicationHasNoSupabaseFields() {
            var classes = appClasses();
            assertThat(classes).noneMatch(c -> c.getAllFields().stream()
                    .anyMatch(f -> f.getRawType().getPackage().getName().startsWith("com.supabase")));
        }
    }

    @Nested
    @DisplayName("jakarta.persistence confined to JPA adapter")
    class JpaConfinedToAdapter {

        @Test
        @DisplayName("TC9: jakarta.persistence types only appear in JPA adapter package")
        void jakartaPersistenceOnlyInJpaAdapter() {
            var jpaClasses = new ClassFileImporter()
                    .importPackages("com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa..");
            assertThat(jpaClasses).isNotEmpty();
        }
    }
}
