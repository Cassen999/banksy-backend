# Fix Plan — oidc-logout

## Root Cause

- **Test failures:** 2 failure(s)/error(s) detected
- **Low coverage class:** `org.example.service.PlaidLinkService` (line: 100.0%, branch: 75.0%)

## Step-by-Step Fix

1. Identify the failing test: `AuthControllerTest.shouldReturn200_whenLogoutCalled`
2. Remove the test — it covered `POST /api/auth/logout` which was deleted as part of this feature
3. Remove the now-unused `csrf` and `post` imports from the test class

## Fix Applied

- **What was changed:** Removed `shouldReturn200_whenLogoutCalled` test and its unused imports (`csrf`, `post`) from `AuthControllerTest.java`
- **Why it resolves the issue:** The test targeted `POST /api/auth/logout`, which was intentionally removed in this feature. The endpoint no longer exists, so the test is invalid.

## Relevant Test Output

```
at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.apache.maven.surefire.junitplatform.LazyLauncher.execute(LazyLauncher.java:56)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.execute(JUnitPlatformProvider.java:194)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invokeAllTests(JUnitPlatformProvider.java:150)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invoke(JUnitPlatformProvider.java:124)
	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:385)
	at org.apache.maven.surefire.booter.ForkedBooter.execute(ForkedBooter.java:162)
	at org.apache.maven.surefire.booter.ForkedBooter.run(ForkedBooter.java:507)
	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:495)
Caused by: java.io.IOException: Error while instrumenting org/example/repository/NotificationRepository$MockitoMock$SPaT7nTG$auxiliary$YqMOTZ7g with JaCoCo 0.8.12.202403310830/dbfb6f2.
	at org.jacoco.agent.rt.internal_aeaf9ab.core.instr.Instrumenter.instrumentError(Instrumenter.java:161)
	at org.jacoco.agent.rt.internal_aeaf9ab.core.instr.Instrumenter.instrument(Instrumenter.java:111)
	at org.jacoco.agent.rt.internal_aeaf9ab.CoverageTransformer.transform(CoverageTransformer.java:92)
	... 111 more
Caused by: java.lang.IllegalArgumentException: Unsupported class file major version 70
	at org.jacoco.agent.rt.internal_aeaf9ab.asm.ClassReader.<init>(ClassReader.java:200)
	at org.jacoco.agent.rt.internal_aeaf9ab.asm.ClassReader.<init>(ClassReader.java:180)
	at org.jacoco.agent.rt.internal_aeaf9ab.asm.ClassReader.<init>(ClassReader.java:166)
	at org.jacoco.agent.rt.internal_aeaf9ab.core.internal.instr.InstrSupport.classReaderFor(InstrSupport.java:280)
	at org.jacoco.agent.rt.internal_aeaf9ab.core.instr.Instrumenter.instrument(Instrumenter.java:77)
	at org.jacoco.agent.rt.internal_aeaf9ab.core.instr.Instrumenter.instrument(Instrumenter.java:109)
	... 112 more

```