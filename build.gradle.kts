plugins {
    `java-library`
    idea
    id("net.neoforged.moddev") version "2.0.148"
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
}

val minecraftVersion = property("minecraft_version") as String
val minecraftVersionRange = property("minecraft_version_range") as String
val neoVersion = property("neo_version") as String
val jeiVersion = property("jei_version") as String
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

// GameTests (PLAN.md section 17) live in their own source set: part of the mod in dev runs, never in the jar.
val gametest: SourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

// The no-sockets GameTest (#33) reads this recording. It traces every socket connect and bind, every datagram send,
// and every HTTP and FTP URL connection, with stack traces, from JVM start.
val socketTraceFilter = listOf(
    "sun.nio.ch.Net::connect",
    "sun.nio.ch.Net::bind",
    "sun.nio.ch.DatagramChannelImpl::send",
    "sun.net.www.protocol.http.HttpURLConnection::connect",
    "sun.net.www.protocol.ftp.FtpURLConnection::connect",
).joinToString(";")
val socketRecording = listOf(
    "name=buildcraftreforged-sockets",
    "settings=none",
    "+jdk.MethodTrace#enabled=true",
    "+jdk.MethodTrace#stackTrace=true",
    "+jdk.MethodTrace#filter=$socketTraceFilter",
    "+jdk.SocketRead#enabled=true",
    "+jdk.SocketRead#threshold=0ms",
    "+jdk.SocketRead#stackTrace=true",
    "+jdk.SocketWrite#enabled=true",
    "+jdk.SocketWrite#threshold=0ms",
    "+jdk.SocketWrite#stackTrace=true",
).joinToString(",")

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
            sourceSet = gametest
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            // The GameTest-only blocks and tests register in this run alone (BuildcraftGameTests.isGameTestRun).
            systemProperty("buildcraftreforged.gametest", "true")
            jvmArgument("-XX:StartFlightRecording:$socketRecording")
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
            sourceSet(gametest)
        }
    }

    // JUnit tests run with the game loaded (registries bootstrapped), through junit-fml.
    unitTest {
        enable()
        testedMod = mods.getByName(modId)
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // EnergyTableTest (#46) compares docs/ENERGY-TABLE.md with the table it generates; -PupdateEnergyTable writes it.
    systemProperty("buildcraftreforged.projectDir", layout.projectDirectory.asFile.absolutePath)
    inputs.files("docs/ENERGY-TABLE.md")
    if (providers.gradleProperty("updateEnergyTable").isPresent) {
        systemProperty("buildcraftreforged.updateEnergyTable", "true")
        outputs.upToDateWhen { false }
    }
}

// Dev client defaults: music off and GUI scale 4. Only keys options.txt does not have yet are written, so what the
// player changes in game sticks.
val devClientOptions = tasks.register("devClientOptions") {
    val options = layout.projectDirectory.file("run/options.txt").asFile
    doLast {
        options.parentFile.mkdirs()
        val lines = if (options.exists()) options.readLines().toMutableList() else mutableListOf()
        mapOf("soundCategory_music" to "0.0", "guiScale" to "4").forEach { (key, value) ->
            if (lines.none { it.startsWith("$key:") })
                lines += "$key:$value"
        }
        options.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
tasks.matching { it.name == "runClient" }.configureEach { dependsOn(devClientOptions) }

neoForge.addModdingDependenciesTo(gametest)

configurations {
    runtimeClasspath.get().extendsFrom(create("localRuntime"))
    named(gametest.runtimeClasspathConfigurationName) { extendsFrom(getByName("localRuntime")) }
}

repositories {
    maven("https://maven.blamejared.com") {
        name = "BlameJared (JEI)"
        content {
            includeGroup("mezz.jei")
            includeGroup("net.mezzdev.config")   // JEI's own runtime dependency
        }
    }
}

dependencies {
    // JEI is optional (PLAN.md section 6): compile against its API only; the plugin class is loaded by JEI alone.
    compileOnly("mezz.jei:jei-$minecraftVersion-common-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$minecraftVersion-neoforge-api:$jeiVersion")
    // ./gradlew runClient -Pwith_jei puts JEI in the dev runs; without the flag they run without it.
    if (providers.gradleProperty("with_jei").isPresent) {
        "localRuntime"("mezz.jei:jei-$minecraftVersion-neoforge:$jeiVersion")
    }
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoVersion,
        "jei_version" to jeiVersion,
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
