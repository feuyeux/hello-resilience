# hello-resilience

Microservices Resilience Patterns

| name                | how does it work?                           | description                                                                               | links                                                                                                                                                                                                                                                                                                           |
| :------------------ | :------------------------------------------ | :---------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Retry**           | repeats failed executions                   | Many faults are transient and may self-correct after a short delay.                       | [overview](https://github.com/resilience4j/resilience4j?tab=readme-ov-file#circuitbreaker-retry-fallback), [documentation](https://resilience4j.readme.io/docs/retry), [Spring](https://resilience4j.readme.io/docs/getting-started-3#annotations)                                                              |
| **Circuit Breaker** | temporary blocks possible failures          | When a system is seriously struggling, failing fast is better than making clients wait.   | [overview](https://github.com/resilience4j/resilience4j?tab=readme-ov-file#circuitbreaker-retry-fallback), [documentation](https://resilience4j.readme.io/docs/circuitbreaker), [Feign](https://resilience4j.readme.io/docs/feign), [Spring](https://resilience4j.readme.io/docs/getting-started-3#annotations) |
| **Rate Limiter**    | limits executions/period                    | Limit the rate of incoming requests.                                                      | [overview](https://github.com/resilience4j/resilience4j?tab=readme-ov-file#ratelimiter), [documentation](https://resilience4j.readme.io/docs/ratelimiter), [Feign](https://resilience4j.readme.io/docs/feign), [Spring](https://resilience4j.readme.io/docs/getting-started-3#annotations)                      |
| **Time Limiter**    | limits duration of execution                | Beyond a certain wait interval, a successful result is unlikely.                          | [documentation](https://resilience4j.readme.io/docs/timeout), [Spring](https://resilience4j.readme.io/docs/getting-started-3#annotations)                                                                                                                                                                       |
| **Bulkhead**        | limits concurrent executions                | Resources are isolated into pools so that if one fails, the others will continue working. | [overview](https://github.com/resilience4j/resilience4j?tab=readme-ov-file#bulkhead), [documentation](https://resilience4j.readme.io/docs/bulkhead), [Spring](https://resilience4j.readme.io/docs/getting-started-3#annotations)                                                                                |
| **Cache**           | memorizes a successful result               | Some proportion of requests may be similar.                                               | [documentation](https://resilience4j.readme.io/docs/cache)                                                                                                                                                                                                                                                      |
| **Fallback**        | provides an alternative result for failures | Things will still fail - plan what you will do when that happens.                         | [Try::recover](https://github.com/resilience4j/resilience4j?tab=readme-ov-file#circuitbreaker-retry-fallback), [Spring](https://resilience4j.readme.io/docs/getting-started-3#section-annotations), [Feign](https://resilience4j.readme.io/docs/feign)                                                          |

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>${resilience4jVersion}</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-ratelimiter</artifactId>
    <version>${resilience4jVersion}</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-retry</artifactId>
    <version>${resilience4jVersion}</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-bulkhead</artifactId>
    <version>${resilience4jVersion}</version>
</dependency>
```

## Circuit Breaker

```sh
mvn test -Dtest=HelloResilienceApplicationTests#testCircuitBreaker
```

## Retry

```sh
mvn test -Dtest=HelloResilienceApplicationTests#testRetry
```

## Rate limiter

```sh
mvn test -Dtest=HelloResilienceApplicationTests#testRateLimiting
```

## Bulkhead

```sh
mvn test -Dtest=HelloResilienceApplicationTests#testBulkhead
```

---

<https://github.com/resilience4j/resilience4j-spring-boot3-demo>
