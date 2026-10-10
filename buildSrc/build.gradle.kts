plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(22)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()

    maven("https://maven.fabricmc.net") {
        name = "Fabric"
    }

    maven("https://maven.minecraftforge.net/") {
        name = "Forge"
    }

    maven("https://maven.architectury.dev/") {
        name = "Architectury"
    }

    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"

        content {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    implementation(plugin(sharedLibs.plugins.idea.gradle))
    implementation(plugin(sharedLibs.plugins.shadow))

    implementation(plugin(mcLibs.plugins.loom.gradle))
    implementation(sharedLibs.fastutil)
}

fun plugin(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map {
        val pluginId = it.pluginId
        val version = it.version

        "$pluginId:$pluginId.gradle.plugin:$version"
    }
