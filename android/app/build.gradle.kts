import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
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
