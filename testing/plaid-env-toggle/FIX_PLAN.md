# Fix Plan — plaid-env-toggle

## Root Cause

- **Test failures:** 70 failure(s)/error(s) detected
- **Low coverage class:** `org.example.service.PlaidLinkService` (line: 100.0%, branch: 75.0%)

## Step-by-Step Fix

_To be completed by Claude during the fix cycle:_

1. 
2. 
3. 

## Fix Applied

_Update this section after the fix is implemented:_

- **What was changed:**
- **Why it resolves the issue:**

## Relevant Test Output

```
it.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.apache.maven.surefire.junitplatform.LazyLauncher.execute(LazyLauncher.java:56)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.execute(JUnitPlatformProvider.java:194)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invokeAllTests(JUnitPlatformProvider.java:150)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invoke(JUnitPlatformProvider.java:124)
	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:385)
	at org.apache.maven.surefire.booter.ForkedBooter.execute(ForkedBooter.java:162)
	at org.apache.maven.surefire.booter.ForkedBooter.run(ForkedBooter.java:507)
	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:495)
Caused by: java.io.IOException: Error while instrumenting org/example/repository/PlaidEnvironmentConfigRepository$MockitoMock$AK4FBTMg$auxiliary$LxoL79ao with JaCoCo 0.8.12.202403310830/dbfb6f2.
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