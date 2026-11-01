// Plugin Android 9 : Kotlin est intégré au plugin Android (plus de plugin « kotlin-android »).
// On fixe ici la version de Kotlin, la même que celle du plugin Compose.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
