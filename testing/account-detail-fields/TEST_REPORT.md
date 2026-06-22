# Test Report — account-detail-fields

## Summary

| Check | Result |
|-------|--------|
| Tests | PASS |
| Line Coverage | 98.2% (PASS) |
| Branch Coverage | 93.8% (PASS) |

## Coverage Results

- **Line coverage:** 98.2% (threshold: 90.0%)
- **Branch coverage:** 93.8% (threshold: 90.0%)

### Classes Below Threshold

- `org.example.model.RecurringResponse$PersonalFinanceCategoryDto` — line: 0.0%, branch: 100.0%
- `org.example.model.RecurringResponse$AmountDto` — line: 0.0%, branch: 100.0%
- `org.example.service.PlaidLinkService` — line: 100.0%, branch: 75.0%

## Test Results

- **Status:** PASS
- **Failures/Errors:** 0

### Maven Output

```
 Running org.example.service.RemoveBankServiceTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.079 s -- in org.example.service.RemoveBankServiceTest
[INFO] Running org.example.service.MonthlyGlanceServiceTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.225 s -- in org.example.service.MonthlyGlanceServiceTest
[INFO] Running org.example.service.TransactionsServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in org.example.service.TransactionsServiceTest
[INFO] Running org.example.service.NotificationServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.040 s -- in org.example.service.NotificationServiceTest
[INFO] Running org.example.service.PlaidEnvironmentServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.125 s -- in org.example.service.PlaidEnvironmentServiceTest
[INFO] 
[INFO] Results:
[INFO] 
[WARNING] Tests run: 220, Failures: 0, Errors: 0, Skipped: 33
[INFO] 
[INFO] 
[INFO] --- jacoco:0.8.12:report (report) @ banksy ---
[INFO] Loading execution data file /Users/hannahgerber/IdeaProjects/banksy/target/jacoco.exec
[INFO] Analyzed bundle 'banksy' with 37 classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.116 s
[INFO] Finished at: 2026-06-22T14:39:23-06:00
[INFO] ------------------------------------------------------------------------
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
WARNING: A Java agent has been loaded dynamically (/Users/hannahgerber/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.6/byte-buddy-agent-1.17.6.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: Final field securityContextRepository in class org.springframework.security.web.context.SecurityContextHolderFilter has been mutated reflectively by class org.springframework.util.ReflectionUtils in unnamed module @3e3047e6 (file:/Users/hannahgerber/.m2/repository/org/springframework/spring-core/6.2.8/spring-core-6.2.8.jar)
WARNING: Use --enable-final-field-mutation=ALL-UNNAMED to avoid a warning
WARNING: Mutating final fields will be blocked in a future release unless final field mutation is enabled

```

## Observations

_Add any observations about test quality, gaps, or edge cases here._