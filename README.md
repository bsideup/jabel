# Jabel - use Javac 12+ syntax when targeting Java 8

> Because life is too short to wait for your users to upgrade their Java!


## Motivation

While Java is evolving and introduces new language features, the majority of OSS libraries
are  still using Java 8 as their target because it still dominates.

But, since most of features after Java 8 did not require a change in the bytecode,
`javac` could emit Java 8 bytecode even when compiling Java 12 sources.

## How Jabel works

Although Jabel is an annotation processor, it does not run any processing,
but instruments the java compiler classes and makes it treat some new Java 9+ languages features
as they were supported in Java 8.

The result is a valid Java 8 bytecode for your switch expressions, `var` declarations,
and other features unavaliable in Java 8.

## How to use

The plugin is distributed with [Jitpack](https://jitpack.io)

### Maven
Make sure you have Jitpack added to the repositories list:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Jabel has to be added as an annotation processor to your maven-compiler-plugin:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
            <configuration>
                <!-- Make sure we're not using Java 9+ APIs -->
                <release>8</release>
                <annotationProcessorPaths>
                    <annotationProcessorPath>
                        <groupId>com.github.bsideup.jabel</groupId>
                        <artifactId>jabel-javac-plugin</artifactId>
                        <version>0.2.0</version>
                    </annotationProcessorPath>
                </annotationProcessorPaths>
                <annotationProcessors>
                    <annotationProcessor>com.github.bsideup.jabel.JabelJavacProcessor</annotationProcessor>
                </annotationProcessors>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Compile your project and verify that Jabel is installed and successfully reports:
```
[INFO] --- maven-compiler-plugin:3.8.1:compile (default-compile) @ tester.thirteen ---
[INFO] Changes detected - recompiling the module!
Jabel: initialized. Enabled features:
	- VAR_SYNTAX_IMPLICIT_LAMBDAS
	- LOCAL_VARIABLE_TYPE_INFERENCE
	- PRIVATE_SAFE_VARARGS
	- SWITCH_MULTIPLE_CASE_LABELS
	- EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES
	- SWITCH_EXPRESSION
	- DIAMOND_WITH_ANONYMOUS_CLASS_CREATION
	- TEXT_BLOCKS
	- SWITCH_RULE
```

### Gradle
First, you need to add Jitpack to your repository list:
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Then, add Jabel as any other annotation processor:
```groovy
dependencies {
    annotationProcessor 'com.github.bsideup.jabel:jabel-javac-plugin:0.2.0'
}
```

Now, even if you set source/target/release to 8, the compiler will let you use some new language features.
The full list of features will be printed during the compilation.
```groovy
sourceCompatibility = targetCompatibility = 8

compileJava {
    options.compilerArgs = [
            "--release", "8" // Avoid using Java 12 APIs
    ]
}
```

Compile your project and verify that the result is still a valid Java 8 bytecode (52.0):
```shell script
$ ./gradlew --no-daemon clean :example:test

> Task :example:compileJava
Jabel: initialized. Enabled features:
        - LOCAL_VARIABLE_TYPE_INFERENCE
        - SWITCH_EXPRESSION
        - PRIVATE_SAFE_VARARGS
        - SWITCH_MULTIPLE_CASE_LABELS
        - VAR_SYNTAX_IMPLICIT_LAMBDAS
        - DIAMOND_WITH_ANONYMOUS_CLASS_CREATION
        - SWITCH_RULE
        - EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES


BUILD SUCCESSFUL in 6s
8 actionable tasks: 8 executed

$ javap -v example/build/classes/java/main/com/example/JabelExample.class
Classfile /Users/bsideup/Work/bsideup/jabel/example/build/classes/java/main/com/example/JabelExample.class
  Last modified 31 Aug 2019; size 1463 bytes
  MD5 checksum d98fb6c3bc1b4046fe745983340b7295
  Compiled from "JabelExample.java"
public class com.example.JabelExample
  minor version: 0
  major version: 52
```