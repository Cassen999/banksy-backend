# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Java 21 Maven project named `banksy`, group `org.example`. Currently a skeleton with a single entry point — `src/main/java/org/example/Main.java`.

## Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Package (produces target/banksy-1.0-SNAPSHOT.jar)
mvn package

# Run the main class after packaging
java -cp target/banksy-1.0-SNAPSHOT.jar org.example.Main

# Clean build artifacts
mvn clean
```

## Structure

Source lives under `src/main/java/org/example/` and tests under `src/test/java/org/example/` (not yet created). The `pom.xml` at the root has no dependencies declared yet — add them there as the project grows.
