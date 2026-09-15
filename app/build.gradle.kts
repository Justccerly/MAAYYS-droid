plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.maayys.host"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.maayys.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.4.0-dev"
        resValue("string", "app_name", "MAAYYS-droid")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // The upstream Go Agent is a PIE executable packaged as a native library so
    // Android can execute it from its read-only installation directory.
    // Official OCR binaries contain relocated ELF segments. NDK 27 llvm-strip
    // changes their file alignment and Android can no longer load them. Keep the
    // verified upstream binaries byte-for-byte (also preserves the PIE Agent).
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" } }
    androidResources { noCompress += "zip" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(project(":"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(project(":meow-platform"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

val verifyUpstreamInputs by tasks.registering {
    doLast {
        val required = listOf("src/main/assets/maayys-resources.zip", "src/main/assets/maayys-manifest.json") +
            listOf("arm64-v8a", "x86_64").flatMap { abi ->
                listOf("libMaaFramework.so", "libMaaAgentClient.so", "libMaaAgentServer.so", "libmaayys_agent.so")
                    .map { "src/main/jniLibs/$abi/$it" }
            }
        val missing = required.filter { !file(it).isFile }
        check(missing.isEmpty()) {
            "Missing MaaYYs Android inputs: $missing. Run scripts/prepare-framework.py, scripts/prepare-resources.py and scripts/build-agent.ps1."
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyUpstreamInputs) }
tasks.matching { it.name == "packageDebug" }.configureEach { doLast { val source = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile; if (source.isFile) source.copyTo(File(source.parentFile, "MAAYYS-droid-debug.apk"), overwrite = true) } }
