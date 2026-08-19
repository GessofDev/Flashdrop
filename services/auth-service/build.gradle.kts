plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Módulo transversal: trace_id, logs JSON, ApiError y catálogo de errores compartidos
    implementation(project(":shared-observability"))

    // Web + seguridad + persistencia + validación + observabilidad
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // JWT (firma/verificación)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")



    // Tests
    // Sin Testcontainers: la persistencia es PostgREST, no JDBC, así que no
    // hay nada que levantar en un contenedor de Postgres. Los tests son
    // unitarios (dominio y casos de uso) y de capa REST con MockMvc.
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
