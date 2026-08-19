import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
}

// Đọc DB_URL và MAPTILER_API_KEY từ local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val dbUrl = localProperties.getProperty("DB_URL") ?: ""
val mapTilerApiKey = localProperties.getProperty("MAPTILER_API_KEY") ?: ""
val mqttHost = localProperties.getProperty("MQTT_HOST") ?: ""
val mqttPort = localProperties.getProperty("MQTT_PORT") ?: "8883"
val mqttUsername = localProperties.getProperty("MQTT_USERNAME") ?: ""
val mqttPassword = localProperties.getProperty("MQTT_PASSWORD") ?: ""
val mqttUseSsl = localProperties.getProperty("MQTT_USE_SSL") ?: "true"
val mqttEnv = localProperties.getProperty("MQTT_ENV") ?: "dev"
val mqttDefaultRobotId = localProperties.getProperty("MQTT_DEFAULT_ROBOT_ID") ?: "defaultRobot"

android {
    namespace = "com.example.autodeliveryapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.autodeliveryapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DB_URL", "\"$dbUrl\"")
        buildConfigField("String", "MAPTILER_API_KEY", "\"$mapTilerApiKey\"")
        
        buildConfigField("String", "MQTT_HOST", "\"$mqttHost\"")
        buildConfigField("int", "MQTT_PORT", "$mqttPort")
        buildConfigField("String", "MQTT_USERNAME", "\"$mqttUsername\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"$mqttPassword\"")
        buildConfigField("boolean", "MQTT_USE_SSL", "$mqttUseSsl")
        buildConfigField("String", "MQTT_ENV", "\"$mqttEnv\"")
        buildConfigField("String", "MQTT_DEFAULT_ROBOT_ID", "\"$mqttDefaultRobotId\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    // Core & UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.circleimageview)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-messaging")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.0.0")

    // MQTT removed

    // Location
    implementation("com.google.android.gms:play-services-location:21.2.0")
}

val generateGoogleServicesJson = tasks.register("generateGoogleServicesJson") {
    val templateFile = file("google-services.json.template")
    val outputFile = file("google-services.json")
    val localPropertiesFile = rootProject.file("local.properties")

    inputs.file(templateFile)
    inputs.file(localPropertiesFile)
    outputs.file(outputFile)

    doLast {
        val props = Properties()
        if (localPropertiesFile.exists()) {
            val fis = FileInputStream(localPropertiesFile)
            props.load(fis)
            fis.close()
        }
        val apiKey = props.getProperty("Firebase_API_Key")?.trim() ?: ""
        if (apiKey.isEmpty() || apiKey == "Firebase_API_Key") {
            throw GradleException("Firebase_API_Key in local.properties is missing, empty, or is the placeholder 'Firebase_API_Key'!")
        }
        if (templateFile.exists()) {
            var content = templateFile.readText()
            content = content.replace("Firebase_API_Key", apiKey)
            outputFile.writeText(content)
            logger.lifecycle("Successfully generated google-services.json from template with valid API Key.")
        } else {
            throw GradleException("google-services.json.template does not exist in the app folder!")
        }
    }
}

tasks.configureEach {
    if (name.startsWith("process") && name.endsWith("GoogleServices")) {
        dependsOn(generateGoogleServicesJson)
    }
}

