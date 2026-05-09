group = "moe.oof"
version = "0.5.0"

plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    kotlin("kapt") version libs.versions.kotlin.get()
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://maven.noxcrew.com/public") {
        name = "noxcrewMavenPublic"
    }
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())
    implementation(libs.kotlin.stdlib)
    implementation(libs.cloud.paper)
    implementation(libs.cloud.annotations)
    implementation(libs.configurate.yaml)
    implementation(libs.configurate.extra.kotlin)
    kapt(libs.cloud.annotations)
}

kotlin {
    jvmToolchain(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    shadowJar {
        val shadowPkg = "moe.oof.systemhomes.shade"

        relocate("org.incendo", "${shadowPkg}.org.incendo")
        relocate("org.spongepowered", "${shadowPkg}.org.spongepowered")
        // relocate("fr.mrmicky", "${shadowPkg}.fr.mrmicky")
        // relocate("com.google.gson", "${shadowPkg}.com.google.gson")

        mergeServiceFiles()
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}
