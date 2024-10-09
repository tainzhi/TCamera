plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

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
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            applicationIdSuffix = ".release"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs["release"]
        }
    }

    applicationVariants.all {
        outputs.forEach { output ->
            check(output is com.android.build.gradle.internal.api.ApkVariantOutputImpl)
            // output.outputFileName = "TCamera_${versionName}.apk"
            if (output is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                if (buildType.name == "debug") {
                    output.outputFileName =
                            "TCamera_${flavorName}_${versionName}_${buildType.name}.apk"
                } else if (buildType.name == "release") {
                    output.outputFileName = "TCamera_${flavorName}_${versionName}.apk"
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
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
    implementation("com.github.CymChad:BaseRecyclerViewAdapterHelper:3.0.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}