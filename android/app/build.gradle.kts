import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Apply google-services only when google-services.json is present (developer step; file is gitignored).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Load developer-supplied LAN URL from local.properties (not committed).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val lanBaseUrl: String = localProps.getProperty("LAN_BASE_URL", "http://192.168.1.1:8080")

android {
    namespace = "com.demo.taskmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.demo.taskmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // AppAuth merges a RedirectUriReceiverActivity that requires this placeholder.
        // We override RedirectUriReceiverActivity in the manifest, so this placeholder
        // satisfies the AAR merge but the actual scheme is declared in the intent-filter.
        manifestPlaceholders["appAuthRedirectScheme"] = "taskmanager"
    }

    buildTypes {
        debug {
            // No extra manifest placeholders needed; network_security_config.xml referenced directly.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "env"
    productFlavors {
        // Android emulator — backend reachable on host loopback alias 10.0.2.2.
        create("emulator") {
            dimension = "env"
            buildConfigField("String", "BASE_URL",        "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "KEYCLOAK_ISSUER", "\"http://10.0.2.2:8180/realms/demo\"")
        }
        // Physical device on same LAN — URL comes from local.properties.
        create("device") {
            dimension = "env"
            buildConfigField("String", "BASE_URL",        "\"$lanBaseUrl\"")
            buildConfigField("String", "KEYCLOAK_ISSUER", "\"${lanBaseUrl.replace(":8080", ":8180")}/realms/demo\"")
        }
        // Tunnel / remote — developer fills in local.properties with a tunnel URL.
        create("tunnel") {
            dimension = "env"
            buildConfigField("String", "BASE_URL",        "\"${localProps.getProperty("TUNNEL_BASE_URL", "https://example.ngrok.io")}\"")
            buildConfigField("String", "KEYCLOAK_ISSUER", "\"${localProps.getProperty("TUNNEL_KEYCLOAK_ISSUER", "https://example.ngrok.io/realms/demo")}\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        managedDevices {
            localDevices {
                create("pixel6api33") {
                    device = "Pixel 6"
                    apiLevel = 33
                    systemImageSource = "aosp"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM — pin all Compose artifact versions together.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Core modules
    implementation(project(":core-network"))
    implementation(project(":core-ui"))
    implementation(project(":domain"))
    implementation(project(":data"))

    // Feature modules
    implementation(project(":feature-tasks"))
    implementation(project(":feature-work"))
    implementation(project(":feature-attachments"))
    implementation(project(":feature-projects"))
    implementation(project(":feature-users"))
    implementation(project(":feature-search"))
    implementation(project(":feature-reports"))
    implementation(project(":feature-config"))

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.converter)

    // Auth
    implementation(libs.appauth)

    // Firebase BOM — google-services plugin applied conditionally when google-services.json is present.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Image loading
    implementation(libs.coil.compose)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Encrypted SharedPreferences for token storage.
    implementation(libs.security.crypto)

    // Unit tests
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Hilt instrumented test support
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    // UI tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
