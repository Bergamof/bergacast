plugins {
    java
    application
    jacoco
    checkstyle
    id("com.diffplug.spotless") version "8.10.4"
}

group = "fr.bergadev"
// Release version, given by the release workflow (computed from Conventional Commits, see .github/scripts).
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-dev")
description = "Schema migrations of the Bergacast database (Liquibase), run as a job"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val liquibaseVersion = "4.33.0" // last 4.x line (Apache-2.0): Liquibase 5 moved to the non-open-source FSL license
val postgresqlDriverVersion = "42.7.14"
// The same password encoder as the backend (Spring Security delegating encoder), so that it can check what is stored.
val springSecurityCryptoVersion = "7.1.2"
val springCoreVersion = "7.0.10"
val junitVersion = "6.0.3"
val assertjVersion = "3.27.7"
val testcontainersVersion = "2.0.5"
val checkstyleVersion = "14.3.0"
val jacocoVersion = "0.8.15"
val palantirJavaFormatVersion = "2.102.0"

dependencies {
    implementation("org.liquibase:liquibase-core:$liquibaseVersion")
    constraints {
        // liquibase-core 4.33.0 pulls commons-lang3 3.17.0 (CVE-2025-48924, fixed in 3.18.0).
        implementation("org.apache.commons:commons-lang3:3.18.0")
    }
    runtimeOnly("org.postgresql:postgresql:$postgresqlDriverVersion")
    implementation("org.springframework.security:spring-security-crypto:$springSecurityCryptoVersion")
    // Spring Security crypto uses its annotations and, through it, commons-logging.
    implementation("org.springframework:spring-core:$springCoreVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "fr.bergadev.bergacast.migration.MigrationMain"
}

tasks.jar {
    manifest {
        attributes("Implementation-Title" to project.name, "Implementation-Version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-serial,-processing", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = jacocoVersion
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

checkstyle {
    toolVersion = checkstyleVersion
    // Same rules as the backend.
    configFile = file("../backend/config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(palantirJavaFormatVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.check {
    dependsOn(tasks.spotlessCheck)
}
