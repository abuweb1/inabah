import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.inabah.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.inabah.android"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Ключ выпуска (docs/android/08): путь, псевдоним и пароли — свойства INABAH_* в ~/.gradle/gradle.properties,
    // вне проекта. Нет свойств — release собирается без подписи выпуска, как до ключа.
    val releaseKeystore = providers.gradleProperty("INABAH_KEYSTORE").orNull
    if (releaseKeystore != null) {
        fun requiredProperty(name: String): String = providers.gradleProperty(name).orNull
            ?: error("$name не задано в ~/.gradle/gradle.properties (рядом с INABAH_KEYSTORE)")
        signingConfigs.create("release") {
            storeFile = file(releaseKeystore)
            storePassword = requiredProperty("INABAH_KEYSTORE_PASSWORD")
            keyAlias = requiredProperty("INABAH_KEY_ALIAS")
            keyPassword = requiredProperty("INABAH_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8: сжатие и оптимизация кода и ресурсов (этап 7). Правила kotlinx.serialization, Media3,
            // Compose приходят из самих библиотек; свои — в proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Типы сборки плагина Baseline Profile (benchmarkRelease, nonMinifiedRelease) — копии release; ставятся
    // на эмулятор и подписываются отладочным ключом, а не ключом выпуска.
    buildTypes.matching { it.name == "benchmarkRelease" || it.name == "nonMinifiedRelease" }.configureEach {
        signingConfig = signingConfigs.getByName("debug")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Версия на экране «О приложении» — BuildConfig.VERSION_NAME / VERSION_CODE.
        buildConfig = true
    }

    // Язык интерфейса выбирает приложение, не система (app/AppLocale.kt): все переводы строк — в основном
    // APK, иначе Play отдаст только язык системы и выбранного перевода на устройстве не окажется.
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Отчёты компилятора Compose (стабильность параметров, пропускаемость) — только по запросу:
// ./gradlew :app:compileReleaseKotlin -Pcompose.reports → app/build/compose_reports.
composeCompiler {
    if (providers.gradleProperty("compose.reports").isPresent) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_reports")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.haze.blur)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Baseline Profile: ставит профиль при установке не из Play (APK) и отдаёт его Play; сам профиль —
    // из модуля :baselineprofile (src/release/generated/baselineProfiles).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Проверки доступности (ATF) в UI-тестах: размер касания, контраст, подписи — этап 7.
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Общие с iOS тексты (корень репозитория) → ассеты APK:
// - data/<файл>.json → assets/data/ (только верхний уровень: data/pending/ в приложение не входит);
// - лицензия шрифта → assets/licenses/.
// Аудиозаписей азкаров в приложении нет (2026-10-06): свои подключатся полем audio в данных
// и файлами в assets/audio/.
// Строчные комментарии, а не /** */: шаблоны путей со звёздочкой внутри блочного комментария
// Kotlin читает как начало вложенного комментария.
abstract class CopySharedContentTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenseFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        fileSystem.sync {
            into(outputDir)
            from(dataFiles) { into("data") }
            from(licenseFiles) { into("licenses") }
        }
    }
}

// Тестовые тоны (свои, 8 с; общие с тестами iOS) → assets/audio/ только debug-сборки: тесту службы
// воспроизведения нужен звук, а записей в приложении нет. В release не попадают.
abstract class CopyTestTonesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val toneFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        fileSystem.sync {
            into(outputDir)
            from(toneFiles) { into("audio") }
        }
    }
}

val repositoryRoot: Directory = rootProject.layout.projectDirectory.dir("..")

val copySharedContent = tasks.register<CopySharedContentTask>("copySharedContent") {
    dataFiles.from(repositoryRoot.dir("data").asFileTree.matching { include("*.json") })
    licenseFiles.from(repositoryRoot.file("Inabah/Resources/Fonts/ScheherazadeNew-OFL.txt"))
}

val copyTestTones = tasks.register<CopyTestTonesTask>("copyTestTones") {
    toneFiles.from(repositoryRoot.dir("InabahTests/Fixtures").asFileTree.matching { include("test-tone-*.wav") })
}

// JVM-тесты «Контент в ассетах» читают те же файлы, что попадают в APK.
tasks.withType<Test>().configureEach {
    dependsOn(copySharedContent)
    val assetsDir = copySharedContent.flatMap { it.outputDir }
    // Содержимое — вход задачи: правка data/ перезапускает тесты, а не берёт старый результат из кэша.
    inputs.dir(assetsDir).withPropertyName("sharedAssets").withPathSensitivity(PathSensitivity.RELATIVE)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider { listOf("-Dinabah.assetsDir=${assetsDir.get().asFile.absolutePath}") },
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copySharedContent,
            CopySharedContentTask::outputDir,
        )
        if (variant.buildType == "debug") {
            variant.sources.assets?.addGeneratedSourceDirectory(copyTestTones, CopyTestTonesTask::outputDir)
        }
    }
}
