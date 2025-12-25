plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("com.game.protocol.GameProtocolClientKt")
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java {
            srcDir("../app/src/main/java/com/game/protocol")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.register<JavaExec>("runProtocol") {
    group = "application"
    description = "Run the GameProtocolClient main function"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.game.protocol.GameProtocolClientKt")
}
