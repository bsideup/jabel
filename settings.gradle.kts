include(
    "jabel-javac-plugin",
    "example"
)

rootProject.name = "jabel"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    `gradle-enterprise`
}

val isCiServer = System.getenv().containsKey("CI")

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        if (isCiServer) {
            tag("CI")
        }
    }
}

buildCache {
    local {
        // Disable on CI b/c local cache will always be empty and will be cleared after run
        isEnabled = !isCiServer
    }
}