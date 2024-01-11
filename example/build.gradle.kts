plugins {
    java
}

tasks.withType<JavaCompile>().configureEach {
    if (name == "compileTestJava") {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = sourceCompatibility
    } else {
        sourceCompatibility = JavaVersion.VERSION_16.toString()
    }

    options.release.set(8) // JavaVersion.VERSION_1_8

    javaCompiler.set(
        javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}

tasks.withType<Test> {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    )
}

dependencies {
    annotationProcessor(project(":jabel-javac-plugin"))
    compileOnly(project(":jabel-javac-plugin"))
    testImplementation("junit:junit:4.13.2")
}
