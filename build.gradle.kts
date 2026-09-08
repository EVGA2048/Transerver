plugins {
    `java-library`
    `maven-publish`
    application
    id("net.neoforged.moddev") version "2.0.107"
}

application {
    mainClass = "dev.transerver.core.TranserverRouterMain"
}

group = "dev.transerver"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

repositories {
    mavenCentral()
}

neoForge {
    version = providers.gradleProperty("neo_version").get()

    runs {
        create("server") {
            server()
        }
    }

    mods {
        create("transerver") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val properties = mapOf(
        "mod_version" to project.version,
        "minecraft_version_range" to providers.gradleProperty("minecraft_version_range").get(),
        "loader_version_range" to providers.gradleProperty("loader_version_range").get()
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties)
    }
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "dev.transerver"
        attributes["Main-Class"] = "dev.transerver.core.TranserverRouterMain"
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
