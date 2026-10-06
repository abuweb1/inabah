// Baseline Profile и macrobenchmark (этап 7, docs/android/09): сценарии запуска, ленты азкаров с плеером
// и листания хадисов. Профиль — ./gradlew :app:generateReleaseBaselineProfile на запущенном эмуляторе
// (API 33+, с Google APIs); замеры — ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.inabah.android.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

// Генерация — на подключённом устройстве (эмулятор Pixel_10_Pro или телефон), не на управляемом Gradle.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
