pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS giúp cài đặt ở đây ghi đè lên các file build.gradle khác
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Cú pháp chuẩn cho file .gradle (Groovy)
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "My current tour"
include(":app")