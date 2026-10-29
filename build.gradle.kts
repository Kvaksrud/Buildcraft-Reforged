plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "2.0.148"
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
}

val minecraftVersion = property("minecraft_version") as String
val minecraftVersionRange = property("minecraft_version_range") as String
val neoVersion = property("neo_version") as String
val modId = property("mod_id") as String
val modName = property("mod_name") as String
val modLicense = property("mod_license") as String
val modVersion = property("mod_version") as String

version = modVersion
group = property("mod_group_id") as String

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
}

base {
    archivesName = modId
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

sourceSets.main {
    resources {
        srcDir("src/generated/resources")
        exclude("**/*.bbmodel")
        exclude("src/generated/**/.cache")
    }
}

neoForge {
    version = neoVersion

    runs {
        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("data") {
            clientData()
            programArguments.addAll(
                "--mod", modId, "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath,
            )
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

configurations {
    runtimeClasspath.get().extendsFrom(create("localRuntime"))
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoVersion,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.jar {
    // buildcraftreforged-<mod_version>+mc<minecraft_version>.jar (PLAN.md section 16)
    archiveVersion = "$modVersion+mc$minecraftVersion"
    from("LICENSE", "NOTICE.md")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

publishMods {
    file = tasks.jar.flatMap { it.archiveFile }
    changelog = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText
        .map { it.substringAfter("## [").substringBefore("\n## [").let { section -> "## [$section".trim() } }
    type = if (modVersion.startsWith("0.")) BETA else STABLE   // 0.x is the development series
    modLoaders.add("neoforge")
    displayName = "$modName $modVersion for Minecraft $minecraftVersion"
    // Without a CurseForge token (any local run), publishMods only writes what it would upload to build/.
    dryRun = providers.environmentVariable("CURSEFORGE_TOKEN").orNull == null

    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = property("curseforge_project_id") as String
        minecraftVersions.add(minecraftVersion)
        javaVersions.add(JavaVersion.VERSION_25)
        client = true
        server = true
        optional("jei")
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = property("github_repository") as String
        commitish = "main"
        tagName = "v$modVersion"
    }
}
