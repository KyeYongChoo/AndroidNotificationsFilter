import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing secrets live in keystore.properties (gitignored). Without it, release builds stay unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "io.github.notifilter"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.notifilter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Don't embed the Google-encrypted dependency metadata blob, so the APK contains
    // nothing that can't be inspected (also required by F-Droid / reproducible builds).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
}

// The privacy guarantee of this app is that it has no network access at all. Android only lets
// an app open sockets if it holds android.permission.INTERNET, so we fail the build if the final
// merged manifest asks for any permission other than the harmless signature-only one AndroidX
// adds for its own internal broadcasts.
androidComponents {
    onVariants { variant ->
        val taskSuffix = variant.name.replaceFirstChar { it.uppercase() }
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val allowed = setOf("${variant.applicationId.get()}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")

        val check = tasks.register("verify${taskSuffix}NoPermissions") {
            group = "verification"
            description = "Fails if the merged manifest requests any permission (e.g. INTERNET)."
            inputs.file(manifest)
            doLast {
                val doc = DocumentBuilderFactory.newInstance()
                    .apply { isNamespaceAware = true }
                    .newDocumentBuilder()
                    .parse(manifest.get().asFile)
                val requested = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
                    val nodes = doc.getElementsByTagName(tag)
                    (0 until nodes.length).map { i ->
                        nodes.item(i).attributes
                            .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                            .nodeValue
                    }
                }
                val unexpected = requested.filterNot { it in allowed }
                if (unexpected.isNotEmpty()) {
                    throw GradleException("Merged manifest requests forbidden permissions: $unexpected")
                }
                logger.lifecycle("Permissions in ${variant.name} manifest: ${requested.ifEmpty { listOf("none") }}")
            }
        }
        tasks.matching { it.name == "assemble$taskSuffix" || it.name == "bundle$taskSuffix" }
            .configureEach { dependsOn(check) }
    }
}
