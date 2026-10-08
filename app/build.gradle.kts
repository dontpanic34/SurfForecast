plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.surfcast.surfforecast"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.surfcast.surfforecast"
        minSdk = 26
        targetSdk = 37
        // Numero de build = numero du run GitHub Actions (surf-log-debug-<n>) : la version
        // affichee dans l'app correspond directement a l'artifact telecharge. 0 = build local.
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = buildNumber.coerceAtLeast(1)
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Clé fixe (secrets GitHub) : l'APK publié se met à jour par-dessus l'ancien. Sans secrets, clé debug.
    val keystorePath = System.getenv("SURFLOG_KEYSTORE_PATH")
    val hasFixedKey = !keystorePath.isNullOrBlank() && file(keystorePath).exists()
    if (hasFixedKey) {
        fun com.android.build.api.dsl.ApkSigningConfig.useFixedKey() {
            storeFile = file(keystorePath!!)
            storePassword = System.getenv("SURFLOG_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SURFLOG_KEY_ALIAS")
            keyPassword = System.getenv("SURFLOG_KEY_PASSWORD")
        }
        signingConfigs.getByName("debug").useFixedKey()
        signingConfigs.create("release").useFixedKey()
    }

    buildTypes {
        release {
            // APK publié : version « release » (non déboguable). Même clé que le debug quand les
            // secrets sont là (mises à jour par-dessus), sinon clé debug pour rester installable.
            signingConfig = signingConfigs.getByName(if (hasFixedKey) "release" else "debug")
            optimization {
                enable = false
            }
        }
    }
    lint {
        // Le contrôle lint tourne déjà en local ; il ne doit pas bloquer la publication.
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ViewModel & Navigation
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Toute l'interface et la logique : module partagé (même app que le site).
    implementation(project(":shared"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore (Sauvegarde des favoris)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Room : le journal de bord vit dans le module partagé (session_log.db), rien à déclarer ici.

    // Coil (affichage des photos du journal de session)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // WorkManager (rafraîchissement du widget en arrière-plan, appli fermée)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}