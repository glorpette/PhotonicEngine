import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask

val phConfig = parent!!.extensions.getByName<PhotonicsExtension>("photonics")

loom {
    runConfigs {
        configureEach {
            generateRunConfig = true
        }
    }
}

dependencies {
    add("minecraft", "com.mojang:minecraft:${phConfig.minecraft.get()}")
    phConfig._dependencyBlock.orNull?.execute(PhotonicsCommonDependenciesScope(this))

    val fabricLoader = _fabricLoader

    // Fabric loader is needed on common for mixin dependency.
    // Why not just include the mixin dependency raw? I have no clue, ask architectury.
    if (fabricLoader != null) {
        val configuration = if (phConfig.minecraft.get().startsWith("26.")) "implementation" else "modImplementation"
        add(configuration, fabricLoader)
    }
}

val jarName = "photonics-${project.version}-${project.name}+MC-${phConfig.minecraft.get()}"

tasks {
    if (tasks.findByName("remapJar") != null) {
        named<RemapJarTask>("remapJar") {
            archiveFileName = "$jarName.jar"

            dependsOn(shadowJar)
            inputFile = shadowJar.get().archiveFile
        }

        named<RemapSourcesJarTask>("remapSourcesJar") {
            archiveFileName = "$jarName-sources.jar"
        }
    } else {
        named<Jar>("jar") {
            archiveFileName = "$jarName.jar"
            dependsOn(shadowJar)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(shadowJar.map { zipTree(it.archiveFile.get().asFile) })
        }
    }
}
