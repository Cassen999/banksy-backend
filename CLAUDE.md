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

## Active Feature Tracking

The file `plans/.active-feature` must always contain the name of the
feature currently being worked on. This is used by all hooks to identify
the correct plan folder.

Rules:
- When you create a new plan folder under `plans/`, immediately write
  the folder name to `plans/.active-feature`
- When switching to work on a different existing feature, update
  `plans/.active-feature` with that feature's folder name before
  doing anything else
- Never delete `plans/.active-feature`
- The contents must exactly match an existing folder name under `plans/`
