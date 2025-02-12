val mainClass = "no.nav.helse.spleis.jobs.AppKt"
val rapidsAndRiversCliVersion = "1.6191629"

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers-cli:$rapidsAndRiversCliVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation(project(":sykepenger-model"))
    implementation(project(":sykepenger-serde"))
    implementation(libs.bundles.database)
    implementation(libs.cloudsql)
}

tasks {
    val copyJars = create("copy-jars") {
        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }
    get("build").finalizedBy(copyJars)

    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = mainClass
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }
    }
}
