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
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    // на эмулятор и подписываются отладочным ключом. У самого release подписи в сборке нет (ключ выпуска — 08).
    buildTypes.matching { it.name == "benchmarkRelease" || it.name == "nonMinifiedRelease" }.configureEach {
        signingConfig = signingConfigs.getByName("debug")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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

// Общие с iOS тексты и записи (корень репозитория) → ассеты APK:
// - data/<файл>.json → assets/data/ (только верхний уровень: data/pending/ в приложение не входит);
// - audio/<раздел>/<файл>.mp3 → assets/audio/ одной папкой (в данных у зикра только имя файла);
// - лицензия шрифта → assets/licenses/.
// Строчные комментарии, а не /** */: шаблоны путей со звёздочкой внутри блочного комментария
// Kotlin читает как начало вложенного комментария.
abstract class CopySharedContentTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val audioFiles: ConfigurableFileCollection

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
            from(audioFiles) {
                eachFile { relativePath = RelativePath(true, "audio", name) }
            }
            includeEmptyDirs = false
            from(licenseFiles) { into("licenses") }
        }
    }
}

val repositoryRoot: Directory = rootProject.layout.projectDirectory.dir("..")

val copySharedContent = tasks.register<CopySharedContentTask>("copySharedContent") {
    dataFiles.from(repositoryRoot.dir("data").asFileTree.matching { include("*.json") })
    audioFiles.from(repositoryRoot.dir("audio").asFileTree.matching { include("*/*.mp3") })
    licenseFiles.from(repositoryRoot.file("Inabah/Resources/Fonts/ScheherazadeNew-OFL.txt"))
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
    }
}
