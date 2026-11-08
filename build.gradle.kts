plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jooq.jooq-codegen-gradle") version "3.19.35"
}

group = "dev.iliamuravev"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    jooqCodegen("org.jooq:jooq-meta-extensions:3.19.35")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val migrationScripts = layout.projectDirectory.dir("src/main/resources/db/migration")
val jooqOutput = layout.buildDirectory.dir("generated-sources/jooq")

jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    property {
                        key = "scripts"
                        value = migrationScripts.asFile.path + "/*.sql"
                    }
                    property {
                        key = "sort"
                        value = "flyway"
                    }
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
                forcedTypes {
                    forcedType {
                        userType = "dev.iliamuravev.ledger.domain.AccountType"
                        isEnumConverter = true
                        includeExpression = "account\\.type"
                    }
                    forcedType {
                        userType = "dev.iliamuravev.ledger.domain.AccountStatus"
                        isEnumConverter = true
                        includeExpression = "account\\.status"
                    }
                    forcedType {
                        userType = "dev.iliamuravev.ledger.domain.TransferType"
                        isEnumConverter = true
                        includeExpression = "transfer\\.type"
                    }
                    forcedType {
                        userType = "dev.iliamuravev.ledger.domain.TransferStatus"
                        isEnumConverter = true
                        includeExpression = "transfer\\.status"
                    }
                    forcedType {
                        name = "JSONB"
                        includeExpression = "outbox_event\\.payload"
                    }
                    forcedType {
                        userType = "dev.iliamuravev.ledger.domain.RejectionReason"
                        isEnumConverter = true
                        includeExpression = "transfer\\.reason"
                    }
                }
            }
            generate {
                isRecords = true
                isPojos = false
                isDaos = false
                isJavaTimeTypes = true
            }
            target {
                packageName = "dev.iliamuravev.ledger.jooq"
                directory = jooqOutput.get().asFile.path
            }
        }
    }
}

tasks.named("jooqCodegen") {
    inputs.dir(migrationScripts).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(jooqOutput)
}

sourceSets {
    main {
        java.srcDir(jooqOutput)
    }
}

tasks.compileJava {
    dependsOn(tasks.named("jooqCodegen"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-parameters"))
}

tasks.test {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val benchmark by tasks.registering(Test::class) {
    description = "Compares the pessimistic and optimistic transfer engines under contention."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("benchmark")
    }
    outputs.upToDateWhen { false }
    systemProperty("benchmark.output", layout.buildDirectory.file("reports/benchmark/locking.md").get().asFile.path)
    listOf("benchmark.threads", "benchmark.transfers", "benchmark.warmup").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
    testLogging {
        showStandardStreams = true
    }
}

tasks.bootJar {
    archiveFileName = "ledger.jar"
}

tasks.jar {
    enabled = false
}
