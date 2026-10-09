rootProject.name = "StockSteps"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// `-Pstocksteps.serverOnly=true` (the server container build) leaves out the mobile apps, which need the Android SDK and Xcode.
if (providers.gradleProperty("stocksteps.serverOnly").orNull != "true") {
    include(":app:androidApp")
    include(":app:shared")
}
include(":core")
include(":server")