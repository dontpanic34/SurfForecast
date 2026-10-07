plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.surfcast.surfforecast.shared"
        compileSdk = 37
        minSdk = 26

        withHostTestBuilder {}

        // Ressources Compose Multiplatform (police Inter, logo) empaquetées dans l'AAR.
        androidResources {
            enable = true
        }
    }

    // Version web (navigateur) : installable depuis Safari
    // ("Ajouter à l'écran d'accueil"). Même interface Compose que sur Android.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("surflog")
        browser {
            commonWebpackConfig {
                outputFileName = "surflog.js"
            }
        }
        binaries.executable()
    }

    // Code propre à l'appli mobile : base Room du journal de bord, qui n'existe pas dans le
    // navigateur (le web stocke le journal en JSON).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("mobile") {
                withAndroidTarget()
            }
        }
    }

    sourceSets {
        val mobileMain by getting {
            dependencies {
                implementation(libs.room.runtime)
                implementation(libs.sqlite.bundled)
            }
        }
        // Lien explicite : la cible Android du plugin AGP « kotlin.multiplatform.library » n'est
        // pas toujours reconnue par withAndroidTarget().
        androidMain.get().dependsOn(mobileMain)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.material3)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation("androidx.activity:activity-compose:1.8.2")
            implementation("io.coil-kt:coil-compose:2.7.0")
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            // Fuseaux horaires pour kotlinx-datetime dans le navigateur.
            implementation(npm("@js-joda/timezone", "2.22.0"))
        }
    }
}

// Compilateur Room (KSP) pour chaque cible : génère DAO et SessionLogDatabaseConstructor.
dependencies {
    add("kspAndroid", libs.room.compiler)
}

compose.resources {
    packageOfResClass = "com.surfcast.surfforecast.resources"
    publicResClass = false
}
