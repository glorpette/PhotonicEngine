plugins {
    id("net.fabricmc.fabric-loom-remap")
    `photonics-common`
}

loom {
    noIntermediateMappings()
}

dependencies {
    add("mappings", loom.layered {
        mappings(file("../empty.tiny"))
    })
}