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

TODO