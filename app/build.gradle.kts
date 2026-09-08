import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Properties
import groovy.json.JsonSlurper

val keystoreProperties =
    Properties().apply {
        rootProject.file("key.properties").takeIf { it.exists() }?.inputStream()?.use(this::load)
    }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")

    id("com.google.gms.google-services")
    id("com.google.android.gms.oss-licenses-plugin")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
    id("com.google.firebase.appdistribution")
}

apply(from = "../gradle/spotless.gradle")

android {
    namespace = "app.yomu.netbar"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "app.yomu.netbar"
        minSdk = 23
        targetSdk = 35
        versionCode = 66
        versionName = "4.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resourceConfigurations.addAll(
            listOf("zh-rCN", "zh-rHK", "ja", "en", "ko", "ru", "de", "fr", "es", "pt")
        )

        // rename output file name
        // https://stackoverflow.com/a/52508858/10008797
        setProperty("archivesBaseName", "native_tools_${versionName}_$versionCode")
        buildConfigField("long", "BUILD_TIMESTAMP", "${Date().time}")
    }

    signingConfigs {
        if (keystoreProperties.isEmpty) return@signingConfigs
        create("release") {
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        val config = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        getByName("debug") {
            versionNameSuffix = "-debug"
            signingConfig = config
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = config
        }
        create("beta") {
            initWith(getByName("release"))
            versionNameSuffix = "-beta"
            firebaseAppDistribution {
                groups = "beta"
                releaseNotesFile = file("beta-distribution-nodes.txt").absolutePath
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    compileOnly(project(":systemApi"))
    implementation(deps.androidx.appcompat)
    implementation(deps.androidx.core.ktx)
    implementation(deps.androidx.preference.ktx)
    implementation(deps.androidx.recyclerview)
    implementation(deps.bundles.androidx.lifecycle)
    implementation(deps.bundles.androidx.navigation)
    implementation(deps.google.material)
    implementation(deps.androidx.browser)
    implementation(deps.androidx.startup)
    implementation(deps.androidx.work.runtime.ktx)
    implementation(deps.androidx.datastore.preferences)
    implementation(deps.androidx.security.crypto.ktx)

    implementation(deps.free.reflection)
    implementation(deps.viewbinding.property.delegate)
    implementation(deps.bumptech.glide)
    implementation(deps.squareup.okhttp)
    implementation(deps.squareup.retrofit)
    implementation(deps.squareup.retrofit.converter.moshi)
    implementation(deps.squareup.moshi)

    implementation(deps.play.services.oss.licenses)
    implementation(platform(deps.firebase.bom))
    implementation(deps.bundles.firebase.ktx)

    debugImplementation(deps.bundles.squareup.leakcanary)

    testImplementation(deps.junit)
    androidTestImplementation(deps.bundles.androidx.test)
}

tasks.register<Exec>("pgyer") {
    val assemble = tasks.named("assembleBeta").get()
    dependsOn(assemble)

    val tree =
        fileTree("build") { include("outputs/apk/beta/*.apk", "intermediates/apk/beta/*.apk") }
    doFirst {
        val apiKey = checkNotNull(keystoreProperties["pgyer.api_key"]) { "pgyer.api_key not found" }
        val apkPath = tree.first().absolutePath
        println("Upload Apk: $apkPath")
        val nodes = file("beta-distribution-nodes.txt").readText().trim()

        commandLine(
            "curl",
            "-F",
            "file=@$apkPath",
            "-F",
            "_api_key=$apiKey",
            "-F",
            "buildUpdateDescription=$nodes",
            "https://www.pgyer.com/apiv2/app/upload"
        )
    }
    val output = ByteArrayOutputStream().apply { standardOutput = this }
    doLast {
        val result = output.toString()
        @Suppress("UNCHECKED_CAST")
        val obj = JsonSlurper().parseText(result) as Map<String, Any?>
        val code = (obj["code"] as Number).toInt()
        if (code == 0) {
            val data = obj["data"] as Map<*, *>
            val path = data["buildShortcutUrl"].toString()
            println("Upload succeeded: https://www.pgyer.com/$path")
        } else {
            val message = obj["message"].toString()
            println("Upload failed: $message")
        }
    }
}
