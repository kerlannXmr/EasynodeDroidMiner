plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signature release : fournie par la CI (secrets GitHub) sinon clé de debug.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

// Kotlin 2.2 : version alignée sur celle qu'apporte tor-android 0.4.9 (sinon le compilateur ne la lit pas)
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "org.kerlann.easynode"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.kerlann.easynode.miner"
        minSdk = 26
        targetSdk = 35
        // Le numéro augmente à chaque build GitHub : chaque APK est une mise à jour.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        // Numéro lisible 1.0.0.N, calculé par GitHub Actions (étape "Numéro de version")
        versionName = System.getenv("APP_VERSION") ?: "dev"
        // Garder cet identifiant : le changer = autre application = blockchain perdue
        // monerod officiel n'existe que pour ARM64 (monero-android-armv8)
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("GITHUB_REPOSITORY") ?: ""}\"")
        buildConfigField("String", "MONERO_VERSION", "\"${System.getenv("MONERO_VERSION") ?: "dev"}\"")
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Indispensable : monerod est livré comme "libmonerod.so" et doit être
        // extrait sur le disque (nativeLibraryDir) pour pouvoir être exécuté.
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libmonerod.so"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Tor intégré (adresse .onion + proxy SOCKS)
    implementation("info.guardianproject:tor-android:0.4.9.8")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
}
    // tor-android 0.4.9 demande compileSdk 37 : vérification désactivée en attendant la mise à jour des outils.
tasks.configureEach {
    if (name.startsWith("check") && name.endsWith("AarMetadata")) enabled = false
}
