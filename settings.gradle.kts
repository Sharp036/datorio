rootProject.name = "datorio"
include("backend")

// The Gradle plugins and their classpath come from Maven Central first: the build host cannot always reach
// plugins.gradle.org (the TLS handshake is cut), and every plugin used here is published to Central under
// the module names below, so the plugin markers of the portal are not needed. The portal stays as a fallback.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                "org.jetbrains.kotlin.plugin.spring" -> useModule("org.jetbrains.kotlin:kotlin-allopen:${requested.version}")
                "org.jetbrains.kotlin.plugin.jpa" -> useModule("org.jetbrains.kotlin:kotlin-noarg:${requested.version}")
                "org.springframework.boot" -> useModule("org.springframework.boot:spring-boot-gradle-plugin:${requested.version}")
                "io.spring.dependency-management" -> useModule("io.spring.gradle:dependency-management-plugin:${requested.version}")
            }
        }
    }
}
