import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.maven.publish)
}

val ndkVersionFor16Kb = "29.0.14206865"
val gdxJniLibsDir = layout.buildDirectory.dir("generated/jniLibs/gdx")
val gdxNativesArm64 by configurations.creating
val gdxNativesX8664 by configurations.creating

fun readLocalProperty(name: String): String? {
    val localProps = rootProject.file("local.properties")
    if (!localProps.exists()) return null
    val props = Properties()
    localProps.inputStream().use(props::load)
    return props.getProperty(name)
}

val sdkDirProvider = providers.provider {
    val sdkDir = readLocalProperty("sdk.dir")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: throw GradleException("Android SDK path is not configured (sdk.dir / ANDROID_SDK_ROOT / ANDROID_HOME)")
    file(sdkDir)
}

val ndkDirProvider = sdkDirProvider.map { sdkDir ->
    sdkDir.resolve("ndk").resolve(ndkVersionFor16Kb)
}

val ndkPrebuiltDirProvider = ndkDirProvider.map { ndkDir ->
    val prebuiltRoot = ndkDir.resolve("toolchains").resolve("llvm").resolve("prebuilt")
    prebuiltRoot.listFiles()
        ?.firstOrNull { it.isDirectory }
        ?: throw GradleException("Cannot locate NDK toolchain prebuilt dir under $prebuiltRoot")
}

val ndkLibcxxArm64Provider = ndkPrebuiltDirProvider.map { prebuilt ->
    prebuilt.resolve("sysroot").resolve("usr").resolve("lib").resolve("aarch64-linux-android").resolve("libc++_shared.so")
}

val ndkLibcxxX8664Provider = ndkPrebuiltDirProvider.map { prebuilt ->
    prebuilt.resolve("sysroot").resolve("usr").resolve("lib").resolve("x86_64-linux-android").resolve("libc++_shared.so")
}

android {
    namespace = "dev.damianpetla.physicsscene"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 33
        ndkVersion = ndkVersionFor16Kb

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main") {
            jniLibs {
                directories.add(gdxJniLibsDir.get().asFile.absolutePath)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.gdx.box2d)
    implementation(libs.jnigen.loader)
    implementation(libs.jnigen.runtime)
    gdxNativesArm64(libs.gdx.box2d.platform) {
        artifact {
            classifier = "natives-arm64-v8a"
        }
    }
    gdxNativesX8664(libs.gdx.box2d.platform) {
        artifact {
            classifier = "natives-x86_64"
        }
    }
    gdxNativesArm64(libs.jnigen.runtime.platform) {
        artifact {
            classifier = "natives-arm64-v8a"
        }
    }
    gdxNativesX8664(libs.jnigen.runtime.platform) {
        artifact {
            classifier = "natives-x86_64"
        }
    }
    testImplementation(libs.junit)
}

val extractGdxNatives by tasks.registering(Sync::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    into(gdxJniLibsDir)
    val arm64Natives = providers.provider { gdxNativesArm64.resolve().map { zipTree(it) } }
    val x8664Natives = providers.provider { gdxNativesX8664.resolve().map { zipTree(it) } }

    from(arm64Natives) {
        include("libgdx-box2d.so")
        include("libjnigen-runtime.so")
        into("arm64-v8a")
    }
    from(x8664Natives) {
        include("libgdx-box2d.so")
        include("libjnigen-runtime.so")
        into("x86_64")
    }
    from(ndkLibcxxArm64Provider) {
        into("arm64-v8a")
    }
    from(ndkLibcxxX8664Provider) {
        into("x86_64")
    }

    doFirst {
        val requiredFiles = listOf(
            ndkLibcxxArm64Provider.get(),
            ndkLibcxxX8664Provider.get(),
        )
        requiredFiles.forEach { file ->
            if (!file.exists()) {
                throw GradleException("Missing NDK libc++ file: ${file.absolutePath}")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(extractGdxNatives)
}

mavenPublishing {
    coordinates(
        groupId = providers.gradleProperty("GROUP").orElse("io.github.damianpetla").get(),
        artifactId = "physics-scene",
        version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0").get(),
    )

    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("PhysicsScene")
        description.set("Physics-first Jetpack Compose scene powered by Box2D.")
        inceptionYear.set("2026")
        url.set("https://github.com/damianpetla/PhysicsScene")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("damianpetla")
                name.set("Damian Petla")
                email.set("damianpetla@users.noreply.github.com")
                url.set("https://github.com/damianpetla")
            }
        }
        scm {
            url.set("https://github.com/damianpetla/PhysicsScene")
            connection.set("scm:git:git://github.com/damianpetla/PhysicsScene.git")
            developerConnection.set("scm:git:ssh://git@github.com/damianpetla/PhysicsScene.git")
        }
    }
}
