plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// iOS targets only build on macOS (or when forced with -Phabittags.ios), so Linux dev and CI stay green.
val buildIos = System.getProperty("os.name").startsWith("Mac") || providers.gradleProperty("habittags.ios").isPresent

kotlin {
    jvmToolchain(17)
    jvm()
    if (buildIos) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
