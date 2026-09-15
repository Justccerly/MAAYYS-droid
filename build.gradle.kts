plugins {
    id("com.android.library") version "8.7.3"
    id("com.android.application") version "8.7.3" apply false
    kotlin("android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

android {
    namespace = "com.maayys"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation(project(":meow-platform"))
    testImplementation("junit:junit:4.13.2")
}
