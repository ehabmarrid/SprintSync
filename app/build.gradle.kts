import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun firebaseValue(key: String): String =
    localProperties.getProperty(key, "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "com.ehab.sprintsync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ehab.sprintsync"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseValue("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseValue("FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseValue("FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${firebaseValue("FIREBASE_DATABASE_URL")}\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"${firebaseValue("FIREBASE_STORAGE_BUCKET")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // AGP 9.0.1 currently crashes inside this AndroidX detector when it visits
    // Kotlin SAM callbacks. Other lint checks stay enabled.
    lint {
        disable += setOf("UnsafeOptInUsageError", "UnsafeOptInUsageWarning")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.11.0")
    // Declared rather than inherited. Coroutines already arrive transitively via Firebase
    // and Lifecycle, but only because those libraries expose them as `api` - another
    // project's classification decision, which can change in a patch release. The android
    // artifact is the right one to name: it brings core with it and supplies the
    // Dispatchers.Main implementation viewModelScope needs at runtime.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.code.gson:gson:2.14.0")

    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")

    testImplementation("junit:junit:4.13.2")
}
