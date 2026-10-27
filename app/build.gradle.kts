plugins {
    id("com.android.application") version "9.4.1"
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

android {
    namespace = "com.upcroc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.upcroc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9.4.1 bundles the Kotlin 2.2.10 compiler. Ktor and the MCP SDK declare a
// newer kotlin-stdlib (2.3.x), whose metadata the 2.2 compiler refuses to read.
// Pin the stdlib to the compiler's own version; their classes are unaffected.
configurations.configureEach {
    resolutionStrategy.force(
        // Ktor and the MCP SDK ask for kotlin-stdlib 2.4.0, whose metadata the
        // 2.2.10 compiler cannot read. 2.2.21 is the newest stdlib that stays
        // inside that ceiling.
        //
        // All three artifacts move together. Pinning stdlib alone left
        // kotlin-reflect free to resolve at 2.3.21, and the packaged app then
        // contained a kotlin.jvm.internal class with no matching definition, so
        // it died at launch in Room's constructor.
        "org.jetbrains.kotlin:kotlin-stdlib:2.2.21",
        "org.jetbrains.kotlin:kotlin-reflect:2.2.21",
        "org.jetbrains.kotlin:kotlin-stdlib-common:2.2.21",
        // androidx.core 1.19.0 sets minCompileSdk=37; 1.18.0 is the newest
        // that stays under compileSdk 36.
        "androidx.core:core:1.18.0",
        "androidx.core:core-ktx:1.18.0",
    )
}

dependencies {
    // BOM 2026.06.01 is the newest that stays under compileSdk 36. Compose
    // 1.12.0 (BOM 2026.08.00+) sets minCompileSdk=37 and needs an SDK platform
    // that is not released.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // 3.5.1 is the Ktor that MCP 0.15.0 declares, and the newest that pairs
    // with okhttp 5.3.2. okhttp 5.5.0 sets minCompileSdk=37.
    implementation("io.ktor:ktor-client-core:3.5.1")
    implementation("io.ktor:ktor-client-okhttp:3.5.1")
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.15.0")
}