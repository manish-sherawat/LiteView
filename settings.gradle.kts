pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.ghostscript.com") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // For PdfiumAndroid fork
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.ghostscript.com") }
    }
}

rootProject.name = "LiteView"

include(":app")
include(":core")
include(":feature:dashboard")
include(":feature:reader-pdf")
include(":feature:reader-office")
include(":feature:reader-text")

include(":feature:scanner")
