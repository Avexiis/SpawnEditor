plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.spawneditor.App")
}

dependencies {
    implementation("com.formdev:flatlaf:2.6")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation(fileTree("lib") { include("discord-game-sdk4j-1.0.0.jar") })
}

tasks.processResources {
    from("lib/discord_game_sdk.dll") {
        into("")
    }
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.spawneditor.App"
    }
}

tasks.shadowJar {
    archiveBaseName.set("SpawnEditor")
    archiveVersion.set("")
    archiveClassifier.set("")
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

defaultTasks("shadowJar")
