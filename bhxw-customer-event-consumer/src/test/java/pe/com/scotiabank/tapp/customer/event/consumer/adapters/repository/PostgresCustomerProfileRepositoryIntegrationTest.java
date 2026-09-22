package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionStatus;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@EnabledIfEnvironmentVariable(named = "CUSTOMER_TEST_R2DBC_URL", matches = ".+")
@SpringBootTest(
    properties = {
      "spring.sql.init.mode=always",
      "spring.r2dbc.username=customer_test",
      "spring.r2dbc.password=",
      "spring.kafka.listener.auto-startup=false"
    })
@SuppressWarnings({"PMD.UnitTestContainsTooManyAsserts", "PMD.AvoidDuplicateLiterals"})
class PostgresCustomerProfileRepositoryIntegrationTest {
  @Autowired private DatabaseClient database;
  @Autowired private PostgresCustomerProfileRepositoryAdapter repository;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.r2dbc.url", () -> System.getenv("CUSTOMER_TEST_R2DBC_URL"));
  }

  @BeforeEach
  void setUp() {
    database
        .sql("TRUNCATE cdc_customer.users, cdc_customer.enterprises CASCADE")
        .fetch()
        .rowsUpdated()
        .block(Duration.ofSeconds(10));
  }

  @Test
  void shouldPersistProducerSnapshotAcrossSevenTablesWithDynamicFields() {
    var event = CustomerEventFixture.event();
    ((ObjectNode) event.payload()).putObject("new_dynamic_section").putArray("flags").add("A");
    var account =
        (ObjectNode)
            CustomerEventFixture.user(event)
                .path("entitlements")
                .path("services")
                .get(0)
                .path("accounts")
                .get(0);
    account.put("transaction_limit", new BigDecimal("12345678901234567890.123456789"));
    account.putObject("future_account_details").put("enabled", true);
    StepVerifier.create(repository.upsert(CustomerEventFixture.snapshot(event)))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CustomerProjectionStatus.APPLIED))
        .verifyComplete();
    assertThat(count("cdc_customer.enterprises")).isEqualTo(1);
    assertThat(count("cdc_customer.users")).isEqualTo(2);
    assertThat(count("cdc_customer.enterprise_users")).isEqualTo(2);
    assertThat(count("cdc_customer.services")).isEqualTo(1);
    assertThat(count("cdc_customer.accounts")).isEqualTo(2);
    assertThat(count("cdc_customer.user_services")).isEqualTo(1);
    assertThat(count("cdc_customer.user_service_accounts")).isEqualTo(2);
    assertThat(
            text(
                "SELECT person_type AS value FROM cdc_customer.users WHERE user_attributes->>'user_id' = '55421'"))
        .isEqualTo("12345678");
    assertThat(
            text(
                "SELECT birth_date::text AS value FROM cdc_customer.users WHERE user_attributes->>'user_id' = '55421'"))
        .isEqualTo("2026-12-25");
    assertThat(
            text(
                "SELECT marital_status AS value FROM cdc_customer.users WHERE user_attributes->>'user_id' = '55421'"))
        .isEqualTo("3");
    assertThat(
            text(
                "SELECT gender AS value FROM cdc_customer.users WHERE user_attributes->>'user_id' = '55421'"))
        .isEqualTo("1");
    assertThat(text("SELECT constitution_date::text AS value FROM cdc_customer.enterprises"))
        .isEqualTo("2026-12-25");
    assertThat(
            text(
                "SELECT payload_snapshot#>>'{new_dynamic_section,flags,0}' AS value FROM cdc_customer.enterprise_users WHERE external_user_id = '55421'"))
        .isEqualTo("A");
    assertThat(
            text(
                "SELECT payload_snapshot#>>'{triggering_user_details,user_id}' AS value FROM cdc_customer.enterprise_users WHERE external_user_id = '55421'"))
        .isEqualTo("54901");
    assertThat(
            text(
                "SELECT event_metadata->>'country_code' AS value FROM cdc_customer.enterprise_users WHERE external_user_id = '55421'"))
        .isEqualTo("604");
    assertThat(
            text(
                "SELECT transaction_limit::text AS value FROM cdc_customer.user_service_accounts WHERE account_order = 0"))
        .isEqualTo("12345678901234567890.123456789");
    assertThat(
            text(
                "SELECT entitlement_attributes#>>'{future_account_details,enabled}' AS value FROM cdc_customer.user_service_accounts WHERE account_order = 0"))
        .isEqualTo("true");
  }

  @Test
  void shouldIgnoreDuplicateAndOlderSnapshotsButAcceptReusedEventName() {
    var initial = CustomerEventFixture.snapshot(CustomerEventFixture.event());
    var newer = CustomerEventFixture.eventAt("2026-09-22T10:30:00Z");
    CustomerEventFixture.user(newer).put("user_full_name", "Updated");
    StepVerifier.create(repository.upsert(initial).then(repository.upsert(initial)))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CustomerProjectionStatus.IGNORED))
        .verifyComplete();
    StepVerifier.create(repository.upsert(CustomerEventFixture.snapshot(newer)))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CustomerProjectionStatus.APPLIED))
        .verifyComplete();
    StepVerifier.create(repository.upsert(initial))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CustomerProjectionStatus.IGNORED))
        .verifyComplete();
    assertThat(
            text(
                "SELECT user_full_name AS value FROM cdc_customer.users WHERE user_attributes->>'user_id' = '55421'"))
        .isEqualTo("Updated");
  }

  @Test
  void shouldRejectConflictingSnapshotWithSameTimestamp() {
    var initial = CustomerEventFixture.event();
    var conflicting = CustomerEventFixture.event();
    CustomerEventFixture.user(conflicting).put("user_full_name", "Different");
    StepVerifier.create(
            repository
                .upsert(CustomerEventFixture.snapshot(initial))
                .then(repository.upsert(CustomerEventFixture.snapshot(conflicting))))
        .expectError(InvalidCustomerEventException.class)
        .verify();
    assertThat(
            text(
                "SELECT user_full_name AS value FROM cdc_customer.users WHERE user_attributes->>'user_id' = '55421'"))
        .isEqualTo("Steve Rogers");
  }

  @Test
  void shouldRemoveRevokedServicesAndAccountsOnlyForTargetUser() {
    var other = CustomerEventFixture.event();
    CustomerEventFixture.user(other).put("user_id", "OTHER");
    var revoked = CustomerEventFixture.eventAt("2026-09-22T10:30:00Z");
    ((ArrayNode) CustomerEventFixture.user(revoked).path("entitlements").path("services"))
        .removeAll();
    StepVerifier.create(
            repository
                .upsert(CustomerEventFixture.snapshot(CustomerEventFixture.event()))
                .then(repository.upsert(CustomerEventFixture.snapshot(other)))
                .then(repository.upsert(CustomerEventFixture.snapshot(revoked))))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(count("cdc_customer.enterprise_users")).isEqualTo(3);
    assertThat(count("cdc_customer.user_services")).isEqualTo(1);
    assertThat(count("cdc_customer.user_service_accounts")).isEqualTo(2);
    assertThat(
            text(
                "SELECT eu.external_user_id AS value FROM cdc_customer.user_services us JOIN cdc_customer.enterprise_users eu ON eu.enterprise_user_key = us.enterprise_user_key"))
        .isEqualTo("OTHER");
  }

  @Test
  void shouldRollbackAllChangesWhenAccountInsertFails() {
    var invalid = CustomerEventFixture.eventAt("2026-09-22T10:30:00Z");
    ((ObjectNode) CustomerEventFixture.user(invalid).path("enterprise_details"))
        .put("enterprise_name", "Changed");
    var account =
        (ObjectNode)
            CustomerEventFixture.user(invalid)
                .path("entitlements")
                .path("services")
                .get(0)
                .path("accounts")
                .get(0);
    account.put("daily_limit", -1);
    // Bypass input validation intentionally to exercise the database constraint and rollback.
    StepVerifier.create(
            repository
                .upsert(CustomerEventFixture.snapshot(CustomerEventFixture.event()))
                .then(repository.upsert(CustomerEventFixture.snapshot(invalid))))
        .expectError(CustomerPersistenceException.class)
        .verify();
    assertThat(text("SELECT enterprise_name AS value FROM cdc_customer.enterprises"))
        .isEqualTo("Acme Corporation");
    assertThat(count("cdc_customer.user_service_accounts")).isEqualTo(2);
    assertThat(
            text(
                "SELECT payload_snapshot#>>'{user_details,event_timestamp}' AS value FROM cdc_customer.enterprise_users WHERE external_user_id = '55421'"))
        .isEqualTo("2026-09-21T10:30:00Z");
  }

  @Test
  void shouldPreserveNewestSnapshotUnderConcurrencyIncludingNanoseconds() {
    StepVerifier.create(
            Flux.range(1, 20)
                .flatMap(
                    number -> {
                      var timestamp = Instant.parse("2026-09-22T10:30:00Z").plusNanos(number);
                      var event = CustomerEventFixture.eventAt(timestamp.toString());
                      CustomerEventFixture.user(event).put("user_full_name", "Version " + number);
                      return repository.upsert(CustomerEventFixture.snapshot(event));
                    },
                    5)
                .then())
        .verifyComplete();
    assertThat(
            text(
                "SELECT user_full_name AS value FROM cdc_customer.users WHERE user_attributes->>'user_id' = '55421'"))
        .isEqualTo("Version 20");
    assertThat(count("cdc_customer.enterprise_users")).isEqualTo(2);
    assertThat(count("cdc_customer.user_service_accounts")).isEqualTo(2);
  }

  @Test
  void shouldScopeSameUserAndAccountToDifferentEnterprises() {
    var other = CustomerEventFixture.event();
    ((ObjectNode) CustomerEventFixture.user(other).path("enterprise_details"))
        .put("enterprise_sco_id", "OTHER-ENTERPRISE");
    StepVerifier.create(
            repository
                .upsert(CustomerEventFixture.snapshot(CustomerEventFixture.event()))
                .then(repository.upsert(CustomerEventFixture.snapshot(other))))
        .expectNextCount(1)
        .verifyComplete();
    assertThat(count("cdc_customer.enterprises")).isEqualTo(2);
    assertThat(count("cdc_customer.users")).isEqualTo(3);
    assertThat(count("cdc_customer.user_service_accounts")).isEqualTo(4);
  }

  @Test
  void shouldNotEraseFullProfileWhenSameUserLaterAppearsAsPartialActor() {
    var actorAsTarget = CustomerEventFixture.event();
    CustomerEventFixture.user(actorAsTarget).put("user_id", "54901");
    CustomerEventFixture.user(actorAsTarget).put("document_number", "ACTOR-DOCUMENT");
    ((ObjectNode) actorAsTarget.payload().path("triggering_user_details"))
        .put("user_id", "FIRST-ADMIN");

    StepVerifier.create(
            repository
                .upsert(CustomerEventFixture.snapshot(actorAsTarget))
                .then(
                    repository.upsert(CustomerEventFixture.snapshot(CustomerEventFixture.event()))))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(
            text(
                "SELECT document_number AS value FROM cdc_customer.users u "
                    + "JOIN cdc_customer.enterprise_users eu ON eu.user_key = u.user_key "
                    + "WHERE eu.external_user_id = '54901'"))
        .isEqualTo("ACTOR-DOCUMENT");
  }

  private String text(String sql) {
    return database
        .sql(sql)
        .map(row -> row.get("value", String.class))
        .one()
        .block(Duration.ofSeconds(5));
  }

  private long count(String table) {
    return database
        .sql("SELECT count(*) AS total FROM " + table)
        .map(row -> row.get("total", Long.class))
        .one()
        .block(Duration.ofSeconds(5));
  }
}
