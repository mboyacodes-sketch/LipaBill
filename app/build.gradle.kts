import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    jacoco
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
    // Compose BOM 2026.11+ (foundation 1.13) requires compileSdk ≥ 37.1.
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.lipabill.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 46
        versionName = "3.2.4"
        // Real devices are arm64; drop x86/x86_64 emulator ABIs from shipped APKs.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        buildConfigField("boolean", "FIREBASE_ANALYTICS", if (hasGoogleServices) "true" else "false")
        buildConfigField("boolean", "FIREBASE_CRASHLYTICS", if (hasGoogleServices) "true" else "false")
        // Pay link v4: new links carry the destination encrypted to the server key.
        // Every release can OPEN v4 (via lipabill.app). Flip EMIT to true only after
        // (1) lipabill.app has PAYLINK_V4_KEYS deployed and (2) most installs can open v4.
        // Key + public hex: `node scripts/paylink/v4-tool.mjs keygen 1` in lipabill_web.
        buildConfigField("boolean", "PAYLINK_V4_EMIT", "false")
        buildConfigField("int", "PAYLINK_V4_KID", "1")
        buildConfigField("String", "PAYLINK_V4_PUBLIC_KEY", "\"\"")
        buildConfigField("String", "PAYLINK_RESOLVE_URL", "\"https://lipabill.app/api/paylink/resolve\"")
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
            enableUnitTestCoverage = true
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
            // AndroidX ships per-module LICENSE copies; none are needed at runtime.
            excludes += "META-INF/androidx/**/LICENSE.txt"
            excludes += "META-INF/*.version"
            // PDFBox pulls BouncyCastle PQC tables that text extraction never uses.
            excludes += "org/bouncycastle/pqc/**"
            excludes += "org/bouncycastle/x509/*.properties"
            // CJK cmaps also exist as AAR assets; those are stripped after merge*Assets.
            excludes += "**/fontbox/resources/cmap/**"
            excludes += "**/fontbox/resources/unicode/**"
            // Keep pdfbox glyphlist/* and text/*: the text engine loads them on first use;
            // without them every PDF import fails and drops to OCR.
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

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric PDF tests read the real PDFBox assets (glyph lists, AFMs).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Opt-in: SKYLINE_GALLERY=1 ./gradlew testPlayDebugUnitTest --tests '*SkylineGalleryRender*'
            it.environment("SKYLINE_GALLERY", System.getenv("SKYLINE_GALLERY") ?: "")
            // Opt-in private corpus of real tickets (never committed): TICKET_CORPUS=/path/to/dir
            it.environment("TICKET_CORPUS", System.getenv("TICKET_CORPUS") ?: "")
            // Opt-in local inbox export for import diagnostics (never committed): INBOX_AUDIT=/path/inbox.json
            it.environment("INBOX_AUDIT", System.getenv("INBOX_AUDIT") ?: "")
            it.environment("INBOX_REGION", System.getenv("INBOX_REGION") ?: "KENYA")
            it.environment("WALLET_CARD_RENDER", System.getenv("WALLET_CARD_RENDER") ?: "")
            it.environment("SAMPLE_TICKET_OUT", System.getenv("SAMPLE_TICKET_OUT") ?: "")
        }
    }
}

jacoco {
    toolVersion = "0.8.13"
}

/**
 * Unit-test coverage for JVM-testable logic. Compose screens, Android services,
 * Room DAOs/entities, and WorkManager are excluded — those need instrumented tests.
 * Target: 90% line coverage of the included set.
 */
val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/databinding/**",
    "**/ui/**",
    "**/LipaBillApp*",
    "**/MainActivity*",
    "**/data/local/**",
    "**/data/repository/**",
    "**/data/prefs/**",
    "**/data/export/**",
    "**/data/sms/*Receiver*",
    "**/data/sms/*Watcher*",
    "**/data/sms/SmsInboxReader*",
    "**/data/sms/SmsPermission*",
    "**/data/sms/LedgerNotifier*",
    "**/data/tickets/ETicketAnalyzer*",
    "**/data/tickets/PkPassParser*",
    "**/data/tickets/PkPassIntents*",
    "**/data/tickets/TicketDocumentImporter*",
    "**/data/tickets/PassDocumentReader*",
    "**/ussd/*Service*",
    "**/ussd/*Helper*",
    "**/ussd/*Coordinator*",
    "**/ussd/*Driver*",
    "**/ussd/*Mask*",
    "**/ussd/*Session*",
    "**/ussd/dialer/**",
    "**/ussd/PendingPaymentReceipt*",
    "**/auth/**",
    "**/notify/**",
    "**/metrics/**",
    "**/tickets/**",
    "**/engage/EngagementScheduler*",
    "**/engage/CheckInNotifier*",
    "**/update/AppUpdates*",
    "**/update/DistributionUpdate*",
    "**/viewmodel/*ViewModel*",
    "**/viewmodel/*UiState*",
    "**/viewmodel/ViewModelFactory*",
    "**/viewmodel/PaymentSession*",
    "**/DemoLedger*",
    // Android Intent / Context paths — unit tests stay JVM-only.
    "**/social/SocialLink*",
    "**/data/contacts/ContactPicker*",
    "**/security/AppAtRestCrypto*",
    "**/data/model/Ticket*"
)

tasks.register<JacocoReport>("jacocoPlayDebugUnitTestReport") {
    group = "verification"
    description = "Jacoco report for playDebug unit tests (logic sources only)."
    dependsOn("testPlayDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    // AGP 9 built-in Kotlin writes playDebug classes here; tmp/kotlin-classes can drift.
    val kotlinClasses = fileTree(
        layout.buildDirectory.dir("intermediates/built_in_kotlinc/playDebug/compilePlayDebugKotlin/classes")
    ) {
        exclude(jacocoExcludes)
    }
    val javaClasses = fileTree(
        layout.buildDirectory.dir("intermediates/javac/playDebug/compilePlayDebugJavaWithJavac/classes")
    ) {
        exclude(jacocoExcludes)
    }
    classDirectories.setFrom(files(kotlinClasses, javaClasses).filter { it.exists() })
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        layout.buildDirectory.file("outputs/unit_test_code_coverage/playDebugUnitTest/testPlayDebugUnitTest.exec")
    )
}

tasks.register<JacocoCoverageVerification>("jacocoPlayDebugCoverageVerification") {
    group = "verification"
    description = "Fails if included logic sources are under 90% line coverage."
    dependsOn("jacocoPlayDebugUnitTestReport")
    classDirectories.setFrom(tasks.named<JacocoReport>("jacocoPlayDebugUnitTestReport").map { it.classDirectories })
    sourceDirectories.setFrom(tasks.named<JacocoReport>("jacocoPlayDebugUnitTestReport").map { it.sourceDirectories })
    executionData.setFrom(tasks.named<JacocoReport>("jacocoPlayDebugUnitTestReport").map { it.executionData })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

// Strip PDFBox extras from merged assets (AAR assets bypass packaging.resources).
// Flavor tasks are mergePlayReleaseAssets / mergeInternalDebugAssets, not mergeReleaseAssets.
tasks.configureEach {
    if (!name.startsWith("merge") || !name.endsWith("Assets")) return@configureEach
    doLast {
        outputs.files.files.forEach { out ->
            val root = out.path
            // Keep Identity-H / Identity-V (~10 KB): most PDFs, airline e-tickets included,
            // use them, and without them PDF text extraction fails and falls back to OCR.
            file("$root/com/tom_roush/fontbox/resources/cmap")
                .listFiles()
                ?.filterNot { it.name == "Identity-H" || it.name == "Identity-V" }
                ?.forEach { it.deleteRecursively() }
            // Keep pdfbox/resources/ttf/LiberationSans-Regular.ttf (~400 KB): PDFBox's font mapper
            // loads it whenever a PDF uses a non-embedded font (common on generated tickets).
            // Latin ticket PDFs: Unicode Scripts tables unused (Bidi in pdfbox/text stays).
            file("$root/com/tom_roush/fontbox/resources/unicode").deleteRecursively()
            // Keep all 14 Standard-14 AFMs: PDFBox loads every one when it first meets a
            // standard font, so dropping Courier/Symbol/Zapf breaks Helvetica PDFs too.
            // Guard: PDF text extraction needs these at class init. If a trim rule ever
            // removes one, every PDF import silently falls back to OCR — fail the build instead.
            if (file("$root/com/tom_roush/pdfbox").exists()) {
                val required = listOf(
                    "com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt",
                    "com/tom_roush/pdfbox/resources/glyphlist/additional.txt",
                    "com/tom_roush/pdfbox/resources/glyphlist/zapfdingbats.txt",
                    "com/tom_roush/pdfbox/resources/text/BidiMirroring.txt",
                    "com/tom_roush/fontbox/resources/cmap/Identity-H",
                    "com/tom_roush/pdfbox/resources/afm/Helvetica.afm",
                    "com/tom_roush/pdfbox/resources/afm/Courier.afm",
                    "com/tom_roush/pdfbox/resources/afm/Symbol.afm",
                    "com/tom_roush/pdfbox/resources/afm/ZapfDingbats.afm",
                    "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
                )
                val missing = required.filterNot { file("$root/$it").exists() }
                if (missing.isNotEmpty()) {
                    throw GradleException(
                        "PDFBox assets stripped from $name: $missing. PDF ticket import would break."
                    )
                }
            }
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
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0")

    val room = "2.8.5"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // SQLCipher for Room encryption at rest
    implementation("net.zetetic:sqlcipher-android:4.20.0")

    // Offline QR encode (display) + CameraX/ML Kit scan + e-ticket OCR.
    // Unbundled ML Kit: Play Services delivers barcode + Latin OCR models after install
    // instead of shipping ~17MB of native libs in the AAB.
    implementation("com.google.zxing:core:3.5.4")
    val camera = "1.7.0"
    implementation("androidx.camera:camera-camera2:$camera")
    implementation("androidx.camera:camera-lifecycle:$camera")
    implementation("androidx.camera:camera-view:$camera")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Embedded text from airline e-ticket PDFs (more reliable than OCR alone)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Firebase Analytics + Crashlytics (no Messaging / Remote Config).
    // Active only when app/google-services.json is present.
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    // Play → Play in-app updates. Internal → self-hosted OTA (HttpURLConnection + FileProvider).
    add("playImplementation", "com.google.android.play:app-update:2.1.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (Android stubs return defaults).
    testImplementation("org.json:json:20260814")
    // Real Android Canvas on the JVM for the opt-in skyline gallery render (design review).
    testImplementation("org.robolectric:robolectric:4.18.1")
}
