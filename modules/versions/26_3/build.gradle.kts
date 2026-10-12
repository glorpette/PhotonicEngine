val mainLibs = libs263

photonics {
    minecraft = mainLibs.versions.minecraft.get()
    javaVersion = JavaVersion.toVersion(25)

    mixins {
        packageName = "at.redi2go.photonics"
        compatabilityLevel = JavaVersion.VERSION_16
        minVersion = "0.8"
    }

    commonDependencies {
        // Minecraft 26.x is unobfuscated, no mappings needed

        //Use by fabric (for obvious reasons) & common for mixin dependencies
        fabricLoader(mainLibs.fabric.loader)

        shadow(sharedLibs.semver)
        shadow(sharedLibs.fastutil.concurrent.wrapper) {
            isTransitive = false
        }

        runtimeOnly(mainLibs.antlr4.runtime)
        implementation(mainLibs.glsl.transformer)
        implementation(mainLibs.jcpp)

        implementation(mainLibs.sodium)
        implementation(mainLibs.iris)
    }
}
