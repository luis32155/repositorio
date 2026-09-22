package pe.com.scotiabank.tapp.service.registration.api.adapters.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.document.TransactionProjectionDocument;
import pe.com.scotiabank.tapp.service.registration.api.fixtures.TransactionProjectionEventFixture;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class RedisTransactionProjectionCacheAdapterTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:05:00Z");
  private static final String EXPECTED_KEY = "transaction_projection:TXN-001|ABC-001";

  @Mock private ReactiveRedisTemplate<String, TransactionProjectionDocument> redisTemplate;

  @Mock private ReactiveValueOperations<String, TransactionProjectionDocument> valueOperations;

  private RedisTransactionProjectionCacheAdapter adapter;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    adapter = new RedisTransactionProjectionCacheAdapter(redisTemplate, clock);
  }

  @Test
  void shouldStoreDocumentWithTtlWhenExpirationIsPresent() {
    var event = TransactionProjectionEventFixture.completedEvent();
    Instant expiresAt = NOW.plus(Duration.ofDays(365));
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.set(eq(EXPECTED_KEY), any(), any(Duration.class)))
        .willReturn(Mono.just(true));

    StepVerifier.create(adapter.put(event, expiresAt)).verifyComplete();

    var docCaptor = ArgumentCaptor.forClass(TransactionProjectionDocument.class);
    var ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOperations).set(eq(EXPECTED_KEY), docCaptor.capture(), ttlCaptor.capture());
    assertThat(docCaptor.getValue().getId()).isEqualTo("TXN-001|ABC-001");
    assertThat(docCaptor.getValue().getLastEventId())
        .isEqualTo(TransactionProjectionEventFixture.EVENT_ID);
    assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(365));
  }

  @Test
  void shouldStoreDocumentWithoutTtlWhenExpirationIsNull() {
    var event = TransactionProjectionEventFixture.processingEvent();
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.set(eq(EXPECTED_KEY), any())).willReturn(Mono.just(true));

    StepVerifier.create(adapter.put(event, null)).verifyComplete();

    verify(valueOperations).set(eq(EXPECTED_KEY), any());
  }

  @Test
  void shouldCompleteSilentlyWhenRedisFails() {
    var event = TransactionProjectionEventFixture.completedEvent();
    Instant expiresAt = NOW.plus(Duration.ofDays(365));
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.set(eq(EXPECTED_KEY), any(), any(Duration.class)))
        .willReturn(Mono.error(new IllegalStateException("Redis caído")));

    StepVerifier.create(adapter.put(event, expiresAt)).verifyComplete();
  }
}
