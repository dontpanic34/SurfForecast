pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    // PREFER_SETTINGS : le plugin Kotlin Wasm ajoute un dépôt Node.js au projet ; il est ignoré
    // (ceux déclarés ci-dessous font foi) au lieu de faire échouer le build.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Node.js et Yarn, téléchargés par Gradle pour construire la version web (Kotlin/Wasm).
        // Le plugin Kotlin essayait de les déclarer lui-même, ce que FAIL_ON_PROJECT_REPOS refuse.
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Node Distributions"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.nodejs", "node") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn Distributions"
                    patternLayout { artifact("v[revision]/[artifact]-v[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "SurfForecast"
include(":app")
include(":shared")
 