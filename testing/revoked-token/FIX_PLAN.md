# Fix Plan: Repository Test Environment Failures

**Linked report:** `TEST_REPORT.md`  
**Affected tests:** `PlaidItemRepositoryTest`, `UserRepositoryTest`, `PlaidAccountRepositoryTest`, `OAuthIdentityRepositoryTest`

---

## Root Cause

Testcontainers 1.21.2 bundles a Docker Java client that negotiates API version 1.32 on initial connection. The Docker Desktop version installed on this machine enforces a minimum API version of 1.40 and rejects the handshake with `Status 400: "client version 1.32 is too old. Minimum supported API version is 1.40"`. Testcontainers cannot initialize the PostgreSQL container and the entire test class fails before any test methods run.

This is not a code defect — the test logic, entity mappings, and SQL are correct. The failure is purely an environment incompatibility between the Testcontainers dependency version and the local Docker Desktop installation.

---

## Fix Plan

**Option A — Upgrade Testcontainers (recommended)**

1. In `pom.xml`, update the Testcontainers BOM version:
   ```xml
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>testcontainers-bom</artifactId>
       <version>1.21.2</version>  <!-- bump to latest stable -->
       <type>pom</type>
       <scope>import</scope>
   </dependency>
   ```
   Check https://github.com/testcontainers/testcontainers-java/releases for the version that bundles a Docker Java client supporting API 1.40+.

2. Run `mvn test` and confirm repository tests pass.

**Option B — Pin Docker API version via properties**

Add to `~/.testcontainers.properties`:
```
testcontainers.reuse.enable=false
```
And set `DOCKER_API_VERSION=1.41` in the environment when running tests, if the Testcontainers version respects that override.

**Option C — Downgrade Docker Desktop**

Revert Docker Desktop to a version that accepts API 1.32. Not recommended as it creates a different incompatibility footprint.

---

## After the Fix Is Applied

Update this file with which option was used and the result, then append `<!-- FIX IMPLEMENTED -->` once the user confirms the tests pass.
