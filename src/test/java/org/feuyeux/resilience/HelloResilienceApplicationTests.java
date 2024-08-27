package org.feuyeux.resilience;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.collection.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.feuyeux.resilience.service.BackendABackendService.BACKEND_A;
import static org.feuyeux.resilience.service.BackendBBackendService.BACKEND_B;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HelloResilienceApplication.class
)
@ExtendWith(SpringExtension.class)
@AutoConfigureObservability
@Slf4j
public class HelloResilienceApplicationTests {
    static final String FAILED_WITH_RETRY = "failed_with_retry";
    static final String SUCCESS_WITHOUT_RETRY = "successful_without_retry";

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    protected RetryRegistry retryRegistry;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    public void setup() {
        transitionToClosedState(BACKEND_A);
        transitionToClosedState(BACKEND_B);
    }

    // CircuitBreaker Test
    @Test
    public void testCircuitBreaker() {
        // When
        Stream.rangeClosed(1, 2).forEach((count) -> produceFailure(BACKEND_A));
        // Then
        checkHealthStatus(BACKEND_A, CircuitBreaker.State.OPEN);
        // When
        Stream.rangeClosed(1, 4).forEach((count) -> produceFailure(BACKEND_B));
        // Then
        checkHealthStatus(BACKEND_B, CircuitBreaker.State.OPEN);
    }

    @Test
    public void testCircuitBreaker2() {
        transitionToOpenState(BACKEND_A);
        circuitBreakerRegistry.circuitBreaker(BACKEND_A).transitionToHalfOpenState();
        // When
        Stream.rangeClosed(1, 3).forEach((count) -> produceSuccess(BACKEND_A));
        // Then
        checkHealthStatus(BACKEND_A, CircuitBreaker.State.CLOSED);
        //
        transitionToOpenState(BACKEND_B);
        circuitBreakerRegistry.circuitBreaker(BACKEND_B).transitionToHalfOpenState();
        // When
        Stream.rangeClosed(1, 3).forEach((count) -> produceSuccess(BACKEND_B));
        // Then
        checkHealthStatus(BACKEND_B, CircuitBreaker.State.CLOSED);
    }

    // Retry Test
    @Test
    public void testRetry() {
        // When
        float currentCount = getCurrentCount(FAILED_WITH_RETRY, BACKEND_A);
        produceFailure(BACKEND_A);
        checkMetrics(FAILED_WITH_RETRY, BACKEND_A, currentCount + 1);

        // When
        currentCount = getCurrentCount(FAILED_WITH_RETRY, BACKEND_B);
        produceFailure(BACKEND_B);
        checkMetrics(FAILED_WITH_RETRY, BACKEND_B, currentCount + 1);

        currentCount = getCurrentCount(SUCCESS_WITHOUT_RETRY, BACKEND_A);
        produceSuccess(BACKEND_A);
        checkMetrics(SUCCESS_WITHOUT_RETRY, BACKEND_A, currentCount + 1);

        currentCount = getCurrentCount(SUCCESS_WITHOUT_RETRY, BACKEND_B);
        produceSuccess(BACKEND_B);
        checkMetrics(SUCCESS_WITHOUT_RETRY, BACKEND_B, currentCount + 1);
    }

    // Limit Test
    @Test
    public void testRateLimiting() throws InterruptedException {
        TimeUnit.MICROSECONDS.sleep(100);
        Stream.rangeClosed(1, 100).forEach((count) -> {
            ResponseEntity<String> response = restTemplate.getForEntity("/" + BACKEND_A + "/limit", String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            String body = response.getBody();
            log.info("A[{}] statusCode:{},body:{}", count, statusCode, body);
            response = restTemplate.getForEntity("/" + BACKEND_B + "/successWithRateLimiter", String.class);
            statusCode = response.getStatusCode();
            body = response.getBody();
            log.info("B[{}] statusCode:{},body:{}", count, statusCode, body);
        });
    }

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    // Bulkhead Test
    @Test
    public void testBulkhead0() {
        ThreadPoolBulkheadConfig config = ThreadPoolBulkheadConfig.custom()
                .maxThreadPoolSize(2)
                .coreThreadPoolSize(1)
                .queueCapacity(1)
                .build();
        ThreadPoolBulkhead bulkhead = ThreadPoolBulkhead.of("backendX", config);
        Supplier<CompletionStage<String>> supplier = ThreadPoolBulkhead.decorateSupplier(bulkhead, () -> hello());
        Stream.rangeClosed(1, 20).toJavaParallelStream().forEach((count) -> {
            try {
                CompletableFuture<String> completableFuture = supplier.get().toCompletableFuture();
                String result = completableFuture.get(5, TimeUnit.SECONDS);
                log.info("result:{}", result);
            } catch (InterruptedException | ExecutionException | TimeoutException | BulkheadFullException e) {
                log.error(">>{}<<", e.getMessage());
            }
        });
    }

    public static String hello() {
        return "Hello";
    }

    public void testBulkhead() {
        AtomicInteger num = new AtomicInteger();
        Stream.rangeClosed(1, 5).toJavaParallelStream().forEach((count) -> {
            ResponseEntity<String> response = restTemplate.getForEntity("/" + BACKEND_B + "/futureSuccess", String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            int actual = num.incrementAndGet();
            if (statusCode == HttpStatus.OK) {
                String body = response.getBody();
                log.info("<B{}|{}> statusCode:{},body:{}", count, actual, statusCode, body);
            } else {
                // 3 + 1
                assertThat(actual).isGreaterThan(4);
                log.info("<B{}|{}> statusCode:{}", count, actual, statusCode);
            }
        });
        log.info("~");
        AtomicInteger num2 = new AtomicInteger();
        Stream.rangeClosed(1, 10).toJavaParallelStream().forEach((count) -> {
            ResponseEntity<String> response = restTemplate.getForEntity("/" + BACKEND_A + "/futureSuccess", String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            if (statusCode == HttpStatus.OK) {
                String body = response.getBody();
                log.info("<A{}> statusCode:{},body:{}", count, statusCode, body);
                num2.incrementAndGet();
            } else {
                log.info("<A{}> statusCode:{}", count, statusCode);
            }
        });
        int actual = num2.get();
        // 4 + 2
        assertThat(actual).isEqualTo(6);
    }

    private void produceFailure(String backend) {
        ResponseEntity<String> response = restTemplate.getForEntity("/" + backend + "/failure", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private void produceSuccess(String backend) {
        ResponseEntity<String> response = restTemplate.getForEntity("/" + backend + "/success", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void transitionToOpenState(String circuitBreakerName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        circuitBreaker.transitionToOpenState();
    }

    private void transitionToClosedState(String circuitBreakerName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        circuitBreaker.transitionToClosedState();
    }

    private void checkHealthStatus(String circuitBreakerName, CircuitBreaker.State state) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        log.info("CircuitBreaker[{}]: {}", circuitBreaker.getName(), circuitBreaker.getState());
        assertThat(circuitBreaker.getState()).isEqualTo(state);
    }

    private float getCurrentCount(String kind, String backend) {
        Retry.Metrics metrics = retryRegistry.retry(backend).getMetrics();

        if (FAILED_WITH_RETRY.equals(kind)) {
            return metrics.getNumberOfFailedCallsWithRetryAttempt();
        }
        if (SUCCESS_WITHOUT_RETRY.equals(kind)) {
            return metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt();
        }

        return 0;
    }

    private void checkMetrics(String kind, String backend, float count) {
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(metricsResponse.getBody()).isNotNull();
        String response = metricsResponse.getBody();
        String metricName = getMetricName(kind, backend);
        assertThat(response).contains(metricName + count);
    }

    protected static String getMetricName(String kind, String backend) {
        return "resilience4j_retry_calls_total{application=\"hello-resilience\",kind=\"" + kind + "\",name=\"" + backend + "\"} ";
    }
}
