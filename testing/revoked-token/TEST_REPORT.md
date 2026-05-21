# Test Report — revoked-token

## Summary

| Check | Result |
|-------|--------|
| Tests | PASS |
| Line Coverage | 100.0% (PASS) |
| Branch Coverage | 92.9% (PASS) |

## Coverage Results

- **Line coverage:** 100.0% (threshold: 90.0%)
- **Branch coverage:** 92.9% (threshold: 90.0%)

### Classes Below Threshold

- `org.example.service.PlaidLinkService` — line: 100.0%, branch: 75.0%

## Test Results

- **Status:** PASS
- **Failures/Errors:** 0

### Maven Output

```
xecutionOrchestrator.java:94)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.lambda$execute$0(EngineExecutionOrchestrator.java:59)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.withInterceptedStreams(EngineExecutionOrchestrator.java:142)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:58)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:103)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:85)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.junit.platform.launcher.core.InterceptingLauncher.lambda$execute$1(InterceptingLauncher.java:39)
	at org.junit.platform.launcher.core.ClasspathAlignmentCheckingLauncherInterceptor.intercept(ClasspathAlignmentCheckingLauncherInterceptor.java:25)
	at org.junit.platform.launcher.core.InterceptingLauncher.execute(InterceptingLauncher.java:38)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.apache.maven.surefire.junitplatform.LazyLauncher.execute(LazyLauncher.java:56)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.execute(JUnitPlatformProvider.java:194)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invokeAllTests(JUnitPlatformProvider.java:150)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invoke(JUnitPlatformProvider.java:124)
	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:385)
	at org.apache.maven.surefire.booter.ForkedBooter.execute(ForkedBooter.java:162)
	at org.apache.maven.surefire.booter.ForkedBooter.run(ForkedBooter.java:507)
	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:495)
Caused by: java.io.IOException: Error while instrumenting org/example/repository/PlaidAccountRepository$MockitoMock$O5IY3xox$auxiliary$Oo2pIA8i with JaCoCo 0.8.12.202403310830/dbfb6f2.
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

## Observations

_Add any observations about test quality, gaps, or edge cases here._