import org.infernus.idea.checkstyle.build.CheckstyleVersions
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.InstrumentCodeTask
import java.io.File

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

plugins {
    id("java")
    id("jacoco")
    id("idea")
    alias(libs.plugins.intellij.platform)

    id("org.infernus.idea.checkstyle.build")
}

version = "26.12.0"

intellijPlatform {
    pluginConfiguration {
        id = "CheckStyle-IDEA"
        name = "CheckStyle-IDEA"
        version = project.version.toString()

        ideaVersion {
            untilBuild = provider { null }
        }
    }

    publishing {
        token.set(System.getenv("JETBRAINS_PLUGIN_REPO_TOKEN"))
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val mockitoAgent: Configuration = configurations.create("mockitoAgent") { isCanBeConsumed = false }

abstract class MockitoAgentProvider : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val agentFiles: ConfigurableFileCollection

    override fun asArguments() = listOf("-javaagent:${agentFiles.asPath}")
}

tasks {
    withType<Test> {
        jvmArgs("-Xshare:off")
        val agentProvider = objects.newInstance(MockitoAgentProvider::class)
        agentProvider.agentFiles.from(mockitoAgent)
        jvmArgumentProviders.add(agentProvider)
        useJUnitPlatform()
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
        options.release.set(21)

        if (name == "compileCsaccessJava" || name == "compileCsaccessTestJava") {
            options.compilerArgs.addAll(listOf("-Xlint:unchecked"))
        }
    }

    // Workaround for legacy Apache Ant Path.addJavaRuntime() behavior: when java.vendor contains
    // "microsoft" it adds <java.home>/Packages as a FileSet (support for the old 1990s MS JVM).
    // Modern Microsoft JDK builds no longer ship this directory, causing a build failure.
    // Creating the directory (even empty) lets Ant scan it harmlessly.
    withType<InstrumentCodeTask>().configureEach {
        doFirst {
            val packagesDir = File(System.getProperty("java.home"), "Packages")
            if (!packagesDir.exists()) {
                packagesDir.mkdirs()
            }
        }
    }
}

// workaround for Checkstyle#14123
configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.collections:google-collections") {
        select("com.google.guava:guava:0")
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(libs.versions.intellij.idea.community.get())

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.idea.maven")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Maven)
    }

    implementation(libs.commons.io)
    implementation(libs.commons.codec)

    val checkStyleBaseVersion = (project.extra["supportedCsVersions"] as CheckstyleVersions).baseVersion
    csaccessCompileOnly("com.puppycrawl.tools:checkstyle:${checkStyleBaseVersion}") {
        exclude("commons-logging:commons-logging")
    }

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.hamcrest)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

idea.module {
    isDownloadJavadoc = true
    isDownloadSources = true

    excludeDirs.addAll(listOf(file(".idea"), file("_support")))

    // TODO We should also tell IntelliJ automatically that csaccessTest contains test code.
    // The following lines should really do it, but currently don't, which seems like a Gradle bug to me:
    //val SourceSet catSourceSet = sourceSets.getByName(CustomSourceSetCreator.CSACCESSTEST_SOURCESET_NAME)
    //testSourceDirs.addAll(catSourceSet.getJava().getSrcDirs())
    //testSourceDirs.addAll(catSourceSet.getResources().getSrcDirs())
    //scopes.TEST.plus.addAll(listOf(configurations.getByName(catSourceSet.getRuntimeConfigurationName())))
}
