import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

val gitCommitHash =
    providers
        .exec {
            workingDir(rootProject.projectDir)
            commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { output -> output.trim().ifBlank { "unknown" } }
        .get()
val ciVersionCode =
    providers.environmentVariable("MORSS_VERSION_CODE").orNull?.toIntOrNull()
val keyProps = Properties()
val releaseKeyPropsFile: File = rootProject.file("signature/keystore_release.properties")
val debugKeyPropsFile: File = rootProject.file("signature/keystore.properties")


if (releaseKeyPropsFile.exists()) {
    println("Loading keystore properties from ${releaseKeyPropsFile.absolutePath}")
    FileInputStream(releaseKeyPropsFile).use { keyProps.load(it) }
} else if (debugKeyPropsFile.exists()) {
    FileInputStream(debugKeyPropsFile).use { keyProps.load(it) }
}

fun signingProperty(propertyName: String, environmentName: String): String? =
    project.providers.environmentVariable(environmentName).orNull
        ?.takeIf { it.isNotBlank() }
        ?: project.providers.gradleProperty(environmentName).orNull
            ?.takeIf { it.isNotBlank() }
        ?: project.providers.gradleProperty(propertyName).orNull
            ?.takeIf { it.isNotBlank() }
        ?: keyProps.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = signingProperty("storeFile", "MORSS_SIGNING_STORE_FILE")
val releaseStorePassword = signingProperty("storePassword", "MORSS_SIGNING_STORE_PASSWORD")
val releaseKeyAlias = signingProperty("keyAlias", "MORSS_SIGNING_KEY_ALIAS")
val releaseKeyPassword = signingProperty("keyPassword", "MORSS_SIGNING_KEY_PASSWORD")
val releaseStoreFile = releaseStoreFilePath?.let { project.file(it) }
val hasReleaseSigning =
    releaseStoreFile?.isFile == true &&
        listOf(releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

val releaseBuildRequested = gradle.startParameter.taskNames.any { requestedTask ->
    val taskName = requestedTask.substringAfterLast(':')
    (taskName.startsWith("assemble", ignoreCase = true) ||
        taskName.startsWith("bundle", ignoreCase = true) ||
        taskName.startsWith("package", ignoreCase = true)) &&
        taskName.endsWith("Release", ignoreCase = true)
}
if (releaseBuildRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is not configured. Provide " +
            "MORSS_SIGNING_STORE_FILE, MORSS_SIGNING_STORE_PASSWORD, " +
            "MORSS_SIGNING_KEY_ALIAS, and MORSS_SIGNING_KEY_PASSWORD " +
            "through environment variables or Gradle properties, " +
            "or configure signature/keystore_release.properties.",
    )
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.conice.morss"
        minSdk = 26
        targetSdk = 35
        // CI uses a monotonically increasing value so APKs signed by the same certificate can be
        // installed as updates. Local builds keep the stable development fallback.
        versionCode = ciVersionCode ?: 1
        versionName = "0.3.2.1"

        buildConfigField(
            "String",
            "USER_AGENT_STRING",
            "\"Morss/${versionName}(${versionCode})\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        room { schemaDirectory("$projectDir/schemas") }

        ksp { arg("room.incremental", "true") }
    }

    flavorDimensions.add("channel")
    productFlavors {
        create("github") {
            isDefault = true
            dimension = "channel"
        }
        create("fdroid") { dimension = "channel" }
        create("googlePlay") {
            dimension = "channel"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
            }
        }
    }
    lint { disable.addAll(listOf("MissingTranslation", "ExtraTranslation")) }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Morss-${defaultConfig.versionName}-${gitCommitHash}.apk"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { buildConfig = true }
    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        resources.excludes.add("rome-utils-*.jar")
    }
    // Locale directories are retained for project structure, but this fork ships English only.
    androidResources { generateLocaleConfig = false }
    namespace = "com.conice.morss"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xannotation-default-target=param-property",
        )
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

aboutLibraries { excludeFields = arrayOf("generated") }

dependencies {
    // AboutLibraries
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose)

    // Markdown
    implementation(libs.commonmark)

    // Compose
    implementation(libs.compose.html)
    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.androidx.ui.graphics)
    androidTestImplementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    androidTestImplementation(libs.compose.ui.test.junit4)
    implementation(libs.compose.material3)
    implementation(libs.material.components)

    // Coil
    implementation(libs.coil.base)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    // Hilt
    implementation(libs.hilt.work)
    implementation(libs.hilt.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.viewmodel)

    // AndroidX
    implementation(libs.android.svg)
    implementation(libs.opml.parser) {
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    implementation(libs.readability4j)
    implementation(libs.rome)
    implementation(libs.rome.modules)
    implementation(libs.telephoto)
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.profileinstaller)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.room.paging)
    implementation(libs.room.common)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.paging.common.ktx)
    implementation(libs.paging.runtime.ktx)
    implementation(libs.paging.compose)
    implementation(libs.browser)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.appwidget.preview)
    implementation(libs.glance.material3)
    implementation(libs.glance.preview)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    //    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigationevent)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.layout)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    // Android's local-test stubs throw for org.json and XmlPullParserFactory. Keep the
    // production APK unchanged while supplying their small JVM implementations to tests.
    testImplementation(libs.json.jvm)
    testImplementation(libs.kxml2)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.work.testing)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
