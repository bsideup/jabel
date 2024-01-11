plugins {
    java
    `maven-publish`
    signing
}

val jabelVersion: String by project
version = jabelVersion

dependencies {
    implementation(platform("net.bytebuddy:byte-buddy-parent:1.14.9"))
    implementation("net.bytebuddy:byte-buddy")
    implementation("net.bytebuddy:byte-buddy-agent")
    implementation("net.java.dev.jna:jna:5.13.0")
}

// <editor-fold desc="Publishing and Signing">

tasks.withType<Javadoc>{
    options.source = "8"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = sourceCompatibility
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = pomArtifactId
            from(components["java"])
            pom {
                name.set(pomName)
                packaging = pomPackaging
                description.set(pomDescription)
                url.set("https://github.com/pkware/jabel")
                setPkwareOrganization()

                developers {
                    developer {
                        id.set("all")
                        name.set("PKWARE, Inc.")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/pkware/jabel.git")
                    developerConnection.set("scm:git:ssh://github.com/pkware/jabel.git")
                    url.set("https://github.com/pkware/jabel")
                }

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        distribution.set("repo")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            url = uri(if (version.toString().isReleaseBuild) releaseRepositoryUrl else snapshotRepositoryUrl)
            credentials {
                username = repositoryUsername
                password = repositoryPassword
            }
        }
    }
}

signing {
    // Signing credentials are stored locally in the user's global gradle.properties file.
    // See https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials for more information.
    sign(publishing.publications["mavenJava"])
}

val String.isReleaseBuild
    get() = !contains("SNAPSHOT")

val Project.releaseRepositoryUrl: String
    get() = properties.getOrDefault(
        "RELEASE_REPOSITORY_URL",

        "https://oss.sonatype.org/service/local/staging/deploy/maven2",
    ).toString()

val Project.snapshotRepositoryUrl: String
    get() = properties.getOrDefault(
        "SNAPSHOT_REPOSITORY_URL",
        "https://oss.sonatype.org/content/repositories/snapshots",
    ).toString()

val Project.repositoryUsername: String
    get() = properties.getOrDefault("NEXUS_USERNAME", "").toString()

val Project.repositoryPassword: String
    get() = properties.getOrDefault("NEXUS_PASSWORD", "").toString()

val Project.pomPackaging: String
    get() = properties.getOrDefault("POM_PACKAGING", "jar").toString()

val Project.pomName: String?
    get() = properties["POM_NAME"]?.toString()

val Project.pomDescription: String?
    get() = properties["POM_DESCRIPTION"]?.toString()

val Project.pomArtifactId
    get() = properties.getOrDefault("POM_ARTIFACT_ID", name).toString()

fun MavenPom.setPkwareOrganization() {
    organization {
        name.set("PKWARE, Inc.")
        url.set("https://www.pkware.com")
    }
}
// </editor-fold>

