# Jabel - Javac 12 plugin that makes it emit Java 8 bytecode

> Because life is too short to wait for your users to upgrade their Java!


## Motivation

While Java is evolving and introduces new language features, the majority of OSS libraries
are  still using Java 8 as their target because it still dominates.

But, since most of features after Java 8 did not require a change in the bytecode,
`javac` could emit Java 8 bytecode even when compiling Java 12 sources.

## How Jabel works

Although Jabel is an annotation processor, it does not run any processing,
but instruments the java compiler classes and makes it think that Java 12 target
does not support certain bytecode features like indy strings, nestmates, and others.

It also makes it emit Java 8 bytecode major/minor version (instead of Java 12 experimental).

The result is a valid Java 8 bytecode for your switch expressions, `var` declarations,
and other features unavaliable in Java 8.

## How to use

The plugin is distributed with [Jitpack](https://jitpack.io)

First, you need to add Jitpack to your repository list:
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

```

Then, add Jabel as any other annotation processor:
```groovy
dependencies {
    annotationProcessor 'com.github.bsideup.jabel:jabel-javac-plugin:0.1.0'
}
```

Now, use Java 12 and, if you want to use [Switch expressions](https://openjdk.java.net/jeps/325),
enable preview features:
```groovy
sourceCompatibility = targetCompatibility = 12

compileJava {
    options.compilerArgs = [
            "--enable-preview"
    ]
}
```

That's it! Compile your project and verify that the result is Java 8 bytecode (52.0):
```shell script
$ javap -v example/build/classes/java/main/com/example/JabelExample.class
Classfile /Users/bsideup/Work/bsideup/jabel/example/build/classes/java/main/com/example/JabelExample.class
  Last modified 31 Aug 2019; size 1463 bytes
  MD5 checksum d98fb6c3bc1b4046fe745983340b7295
  Compiled from "JabelExample.java"
public class com.example.JabelExample
  minor version: 0
  major version: 52
```