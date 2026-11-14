import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing key, kept outside the repo. STEADY_SIGNING names a properties file with storeFile,
// storePassword, keyAlias, keyPassword. Default: the key the user's other apps use. Without one the
// release APK is built unsigned.
val signingProps = Properties().apply {
    val f = file(System.getenv("STEADY_SIGNING") ?: "${System.getProperty("user.home")}/.android-keys/mpe-instruments.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.patrickreynolds.steady"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.patrickreynolds.steady"
        minSdk = 30          // Android 11: the Hisense A9 e-ink phone. The POCO runs 12.
        targetSdk = 35
        // -Psteady.versionCode / -Psteady.versionName: for testing in-app updates with an older-numbered build.
        versionCode = (findProperty("steady.versionCode") as String?)?.toInt() ?: 4
        versionName = (findProperty("steady.versionName") as String?) ?: "0.3.1"
    }
    signingConfigs {
        if (signingProps.isNotEmpty()) create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    // GUIDE.md (the plan, at the top of the repo) is shown in the app, so there is one copy.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/guide"))
}

val guideAsset by tasks.registering(Sync::class) {
    from(rootProject.file("GUIDE.md"))
    into(layout.buildDirectory.dir("generated/guide"))
}
tasks.named("preBuild") { dependsOn(guideAsset) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
