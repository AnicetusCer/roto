import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val useLatestDeps = (project.findProperty("useLatestDeps") as? String)?.toBoolean() == true

fun Project.loadSigningProps(): Properties? {
    val propsFile = File(System.getProperty("user.home"), ".gradle/keystore-com.anicetuscer.roto.properties")
    return propsFile.takeIf(File::exists)?.reader()?.use {
        Properties().apply { load(it) }
    }
}

android {
    namespace = "org.roto"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.roto"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        // Disable dependency metadata signing block so F-Droid scans pass.
        includeInApk = false
        includeInBundle = false
    }
    val signingProps = loadSigningProps()

    signingConfigs {
        signingProps?.let { props ->
            create("release") {
                storeFile = file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (signingProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                // Generate native debug symbols ZIP for Play Console crash decoding.
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

}

// Copy the assembled release APK to a versioned filename for release uploads.
afterEvaluate {
    tasks.register<Copy>("copyVersionedReleaseApk") {
        val versionName = android.defaultConfig.versionName ?: "unspecified"
        dependsOn("assembleRelease")
        from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
        into(layout.buildDirectory.dir("outputs/apk/release/versioned"))
        rename { "roto-v$versionName.apk" }
        mustRunAfter("createReleaseApkListingFileRedirect")
    }
    tasks.named("assembleRelease").configure {
        finalizedBy("copyVersionedReleaseApk")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    val desugarVersion = if (useLatestDeps) "2.1.5" else "2.0.4"
    val coreKtxVersion = if (useLatestDeps) "1.17.0" else "1.13.1"
    val lifecycleVersion = if (useLatestDeps) "2.10.0" else "2.7.0"
    val activityVersion = if (useLatestDeps) "1.12.1" else "1.9.0"
    val materialVersion = if (useLatestDeps) "1.13.0" else "1.11.0"
    val datastoreVersion = if (useLatestDeps) "1.2.0" else "1.1.1"
    val glanceVersion = if (useLatestDeps) "1.1.1" else "1.0.0"
    val workVersion = if (useLatestDeps) "2.9.1" else "2.9.0"

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:$desugarVersion")
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:$activityVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:$materialVersion")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.datastore:datastore-preferences:$datastoreVersion")
    implementation("androidx.glance:glance:$glanceVersion")
    implementation("androidx.glance:glance-appwidget:$glanceVersion")
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
