plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma: se le variabili d'ambiente sono presenti (GitHub Actions + Secrets)
// l'APK release viene firmato con la tua chiave, altrimenti con la chiave di debug.
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
val hasReleaseKey = keystorePath != null && file(keystorePath).exists()

android {
    namespace = "com.marco90.mlinfotainment"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marco90.mlinfotainment"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    signingConfigs {
        create("platform") {
            storeFile = file("../signing/platform.p12")
            storePassword = "android"
            keyAlias = "platform"
            keyPassword = "android"
            storeType = "pkcs12"
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // "normal" = versione per tutti i telefoni; "system" = versione sperimentale firmata con la chiave di test
    // di Android (pubblica) per tablet e autoradio che la accettano: permette di incorporare il navigatore.
    flavorDimensions += "dist"
    productFlavors {
        create("normal") {
            dimension = "dist"
            buildConfigField("boolean", "SYSTEM_BUILD", "false")
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("system") {
            dimension = "dist"
            applicationIdSuffix = ".system"
            versionNameSuffix = "-sistema"
            buildConfigField("boolean", "SYSTEM_BUILD", "true")
            signingConfig = signingConfigs.getByName("platform")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("com.github.marco90-cyber:MLTheme:1.0.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media:media:1.7.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Serve solo per il tema XML Material3 della finestra
    implementation("com.google.android.material:material:1.12.0")

    // Solo versione "Sistema": accesso alle API nascoste di Android per incorporare il navigatore
    "systemImplementation"("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
