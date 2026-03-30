rootProject.name = "build-logic"

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "4.4.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        gradlePluginPortal()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

val isCiBuild = providers.environmentVariable("CI").isPresent

// Ensure buildCache config is kept in sync with all builds (root, build-logic & detekt-gradle-plugin)
buildCache {
    local {
        isEnabled = !isCiBuild
    }
    remote(develocity.buildCache) {
        server = "https://ge.detekt.dev"
        isEnabled = true
        isPush = isCiBuild
    }
}
