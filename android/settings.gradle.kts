pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "habit-tags"
include(":core")
if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) {
    include(":app")
}
