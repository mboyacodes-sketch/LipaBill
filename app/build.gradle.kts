import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase Analytics + Crashlytics — apply when Console config is present
// (see docs/firebase-analytics.md).
val hasGoogleServices = file("google-services.json").exists()
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val releaseStorePath = keystoreProps.getProperty("storeFile")
val hasReleaseKeystore = !releaseStorePath.isNullOrBlank() &&
    rootProject.file(releaseStorePath).exists()
if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.lipabill.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lipabill.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 34
        versionName = "2.2.0"
        // Real devices are arm64; drop x86/x86_64 emulator ABIs from shipped APKs.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        buildConfigField("boolean", "FIREBASE_ANALYTICS", if (hasGoogleServices) "true" else "false")
        buildConfigField("boolean", "FIREBASE_CRASHLYTICS", if (hasGoogleServices) "true" else "false")
    }

    /**
     * play — Google Play production (no Settings UI auto-click helper).
     * internal — Firebase App Distribution / APK sideload (full unlock helpers).
     * SMS + Accessibility USSD fill remain in both; do not hide them from review.
     */
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "SIDELOAD_DISTRIBUTION", "false")
            // Play in-app updates only — no self-hosted APK channel.
            buildConfigField("String", "OTA_MANIFEST_URL", "\"\"")
            buildConfigField("String", "OTA_MANIFEST_PUBLIC_KEY", "\"\"")
        }
        create("internal") {
            dimension = "distribution"
            versionNameSuffix = "-internal"
            buildConfigField("boolean", "SIDELOAD_DISTRIBUTION", "true")
            // Hosted on lipabill.app (Vercel). See ota/ in the repo root.
            buildConfigField(
                "String",
                "OTA_MANIFEST_URL",
                "\"https://lipabill.app/ota/latest.json\""
            )
            // ECDSA P-256 public key (base64 SPKI) for latest.json.sig. Private half:
            // keystore/ota-manifest-signing.pem — scripts/publish-ota.sh signs with it.
            buildConfigField(
                "String",
                "OTA_MANIFEST_PUBLIC_KEY",
                "\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECijJOVuyKNlExZxzLt6kqVPcSJpPvrcSZ3SVroVndP5Ozg+mQHYPq9pCSYjAnjaWWgsei5IrygClqRv3Z9EOOg==\""
            )
        }
    }

    signingConfigs {
        getByName("debug")
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isCrunchPngs = true
            // Package native .so symbols into the AAB for Play Console crash/ANR symbolication.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/kotlin-tooling-metadata.json"
            excludes += "DebugProbesKt.bin"
            // PDFBox brings duplicate META-INF notices
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.txt"
            // PDFBox pulls BouncyCastle PQC tables that text extraction never uses.
            excludes += "org/bouncycastle/pqc/**"
            // CJK cmaps also exist as AAR assets; those are stripped after merge*Assets.
            excludes += "**/fontbox/resources/cmap/**"
        }
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
    }

    androidResources {
        // English UI. Library translations (Play Services, AppCompat) stay out of the AAB.
        localeFilters += listOf("en")
    }
}

// Strip PDFBox extras from merged assets (AAR assets bypass packaging.resources).
// Flavor tasks are mergePlayReleaseAssets / mergeInternalDebugAssets, not mergeReleaseAssets.
tasks.configureEach {
    if (!name.startsWith("merge") || !name.endsWith("Assets")) return@configureEach
    doLast {
        outputs.files.files.forEach { out ->
            file("${out.path}/com/tom_roush/fontbox/resources/cmap").deleteRecursively()
            file("${out.path}/com/tom_roush/pdfbox/resources/ttf").deleteRecursively()
        }
    }
}

if (!hasReleaseKeystore) {
    tasks.configureEach {
        val packagesRelease = (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package")) &&
            name.endsWith("Release")
        if (packagesRelease) {
            doFirst {
                throw GradleException(
                    "Release builds require keystore.properties with a release signing key."
                )
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.8.8")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // SQLCipher for Room encryption at rest
    implementation("net.zetetic:sqlcipher-android:4.20.0")

    // Offline QR encode (display) + CameraX/ML Kit scan + e-ticket OCR.
    // Unbundled ML Kit: Play Services delivers barcode + Latin OCR models after install
    // instead of shipping ~17MB of native libs in the AAB.
    implementation("com.google.zxing:core:3.5.3")
    val camera = "1.4.2"
    implementation("androidx.camera:camera-camera2:$camera")
    implementation("androidx.camera:camera-lifecycle:$camera")
    implementation("androidx.camera:camera-view:$camera")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Embedded text from airline e-ticket PDFs (more reliable than OCR alone)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Firebase Analytics + Crashlytics (no Messaging / Remote Config).
    // Active only when app/google-services.json is present.
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    // Play → Play in-app updates. Internal → self-hosted OTA (HttpURLConnection + FileProvider).
    add("playImplementation", "com.google.android.play:app-update:2.1.0")

    testImplementation("junit:junit:4.13.2")
}
