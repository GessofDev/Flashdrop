plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(project(":shared-observability"))
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // Pin Testcontainers 1.20.4 (matches auth-service BOM). 1.19.8 ships
    // docker-java-api 3.3.6 which speaks Docker Engine API 1.32, but Docker
    // Desktop 4.85.0 (Engine 29.6.2) requires API 1.40+. 1.20.4 ships
    // docker-java 3.4.x with API 1.43+, restoring IT compatibility.
    // Explicit versions on the direct deps are not enough: Spring Boot's
    // auto-imported BOM still wins the transitive conflict, so we
    // additionally force() every testcontainers group via resolutionStrategy.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.testcontainers") {
            useVersion("1.20.4")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}