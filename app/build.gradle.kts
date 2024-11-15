plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val minutesSinceEpoch = System.currentTimeMillis() / 60000

android {
    signingConfigs {
        getByName("debug") {

        }
        create("release") {
            storeFile = file("..\\cert\\android.keystore")
            storePassword = "123456"
            keyAlias = "android"
            keyPassword = "tainzhi"
        }
    }
    namespace = "com.tainzhi.android.tcamera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tainzhi.android.tcamera"
        minSdk = 30
        targetSdk = 34
        versionCode = 1002
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                abiFilters("arm64-v8a")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs["debug"]
            buildConfigField("String", "BUILD_TIME", "\"${minutesSinceEpoch}\"")
            buildConfigField("int", "build_time", "${minutesSinceEpoch}")
            buildConfigField("Boolean", "DEBUG", "true")
        }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            applicationIdSuffix = ".release"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs["release"]
            buildConfigField("Boolean", "DEBUG", "false")
        }
    }

    applicationVariants.all {
        outputs.forEach { output ->
            check(output is com.android.build.gradle.internal.api.ApkVariantOutputImpl)
            // flavorName is null, so use buildType.name instead
            output.outputFileName =
                "TCamera_${versionName}_${buildType.name}.apk"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.baserecyclerviewadapterhelper)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}