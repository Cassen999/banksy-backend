# Test Report — recurring-transactions

## Summary

| Check | Result |
|-------|--------|
| Tests | PASS |
| Line Coverage | 97.8% (PASS) |
| Branch Coverage | 94.0% (PASS) |

## Coverage Results

- **Line coverage:** 97.8% (threshold: 90.0%)
- **Branch coverage:** 94.0% (threshold: 90.0%)

### Classes Below Threshold

- `org.example.model.RecurringResponse$PersonalFinanceCategoryDto` — line: 0.0%, branch: 100.0%
- `org.example.model.RecurringResponse$AmountDto` — line: 0.0%, branch: 100.0%
- `org.example.service.PlaidLinkService` — line: 100.0%, branch: 75.0%

## Test Results

- **Status:** PASS
- **Failures/Errors:** 0

### Maven Output

```
eTest
[INFO] Running org.example.service.BalanceServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.060 s -- in org.example.service.BalanceServiceTest
[INFO] Running org.example.service.RemoveBankServiceTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.088 s -- in org.example.service.RemoveBankServiceTest
[INFO] Running org.example.service.TransactionsServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.135 s -- in org.example.service.TransactionsServiceTest
[INFO] Running org.example.service.NotificationServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.049 s -- in org.example.service.NotificationServiceTest
[INFO] Running org.example.service.PlaidEnvironmentServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.166 s -- in org.example.service.PlaidEnvironmentServiceTest
[INFO] 
[INFO] Results:
[INFO] 
[WARNING] Tests run: 172, Failures: 0, Errors: 0, Skipped: 22
[INFO] 
[INFO] 
[INFO] --- jacoco:0.8.12:report (report) @ banksy ---
[INFO] Loading execution data file /Users/hannahgerber/IdeaProjects/banksy/target/jacoco.exec
[INFO] Analyzed bundle 'banksy' with 29 classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  9.485 s
[INFO] Finished at: 2026-06-12T15:11:32-06:00
[INFO] ------------------------------------------------------------------------
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (/Users/hannahgerber/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.6/byte-buddy-agent-1.17.6.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
WARNING: Final field securityContextRepository in class org.springframework.security.web.context.SecurityContextHolderFilter has been mutated reflectively by class org.springframework.util.ReflectionUtils in unnamed module @3e3047e6 (file:/Users/hannahgerber/.m2/repository/org/springframework/spring-core/6.2.8/spring-core-6.2.8.jar)
WARNING: Use --enable-final-field-mutation=ALL-UNNAMED to avoid a warning
WARNING: Mutating final fields will be blocked in a future release unless final field mutation is enabled

```

## Observations

_Add any observations about test quality, gaps, or edge cases here._