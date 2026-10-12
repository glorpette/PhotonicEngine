plugins {
    id("net.fabricmc.fabric-loom")
    `photonics-fabric`
}

val mainLibs = libs263

dependencies {
    compileOnly(mainLibs.fabric.api)
    // Required by sodium
    runtimeOnly(mainLibs.fabric.api)
}

tasks {
    processResources {
        inputs.property("version", project.version)

        val phVersion = constants.versions.photonics.get()
        val mcVersion = mainLibs.versions.minecraft.get()
        val fabricLoaderVersion = mainLibs.versions.fabric.loader.get()
        val irisVersion = ">=${mainLibs.versions.iris.get().substringBefore('+')}"
        val sodiumVersion = ">=${mainLibs.versions.sodium.get().split('-')[1]}"

        filesMatching("fabric.mod.json") {
            expand(
                "photonics_version" to phVersion,
                "minecraft_version" to mcVersion,
                "fabric_loader_version" to fabricLoaderVersion,
                "iris_version_range" to irisVersion,
                "sodium_version_range" to sodiumVersion
            )
        }
    }
}
