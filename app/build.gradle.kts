plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.tut2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tut2"
        minSdk =24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++11", "-O3") // Use += listOf() for clarity
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt") // Use file() for path clarity
        }
    }
    ndkVersion = "25.2.9519653" // Specify your desired NDK version

    buildTypes {
        release {
            isMinifyEnabled = true // Enable minification for release builds
            // isShrinkResources = true // Consider enabling resource shrinking as well
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // You can define specific settings for debug builds here if needed
            // For example, to disable minification for debug:
            // isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Good practice to explicitly define build features
    buildFeatures {
        // viewBinding = true // Uncomment if you plan to use View Binding
        // dataBinding = true // Uncomment if you plan to use Data Binding
        // compose = true     // Uncomment if you plan to use Jetpack Compose
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout) // This is fine

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
