plugins {
    `java-library`
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    // Tipos que los servicios consumidores necesitan (api = expuesto transitivamente)
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework:spring-web")
    api("org.slf4j:slf4j-api")
    api("net.logstash.logback:logstash-logback-encoder:7.4")

    // Servlet API solo para compilar el filtro; en runtime lo aporta el starter-web del servicio
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework:spring-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic")   // provider real de SLF4J para que el MDC funcione en tests
}

tasks.withType<Test> {
    useJUnitPlatform()
}
