import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

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
        // The iPhone app links this as HabitCore.xcframework (static); Apple-silicon simulators only.
        val xcf = XCFramework("HabitCore")
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "HabitCore"
                isStatic = true
                xcf.add(this)
            }
        }
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
