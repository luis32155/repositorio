package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionStatus;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;
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
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class PostgresCustomerCoreRepositoryIntegrationTest {
  @Autowired private DatabaseClient database;
  @Autowired private PostgresCustomerCoreRepositoryAdapter repository;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.r2dbc.url", () -> System.getenv("CUSTOMER_TEST_R2DBC_URL"));
  }

  @BeforeEach
  void setUp() {
    database
        .sql(
            "TRUNCATE cdc_customer.customer_identity, "
                + "cdc_customer.customer_account_profile CASCADE")
        .fetch()
        .rowsUpdated()
        .block(Duration.ofSeconds(10));
  }

  @Test
  void shouldPersistOneDynamicSnapshotAcrossSevenTables() {
    var event = CustomerEventFixture.event();
    CustomerEventFixture.payload(event).putObject("new_dynamic_section").put("flag", "A");
    StepVerifier.create(repository.replace(CustomerEventFixture.snapshot(event)))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CustomerProjectionStatus.APPLIED))
        .verifyComplete();

    assertThat(count("customer_identity")).isEqualTo(1);
    assertThat(count("customer_individual")).isEqualTo(1);
    assertThat(count("customer_legal_entity")).isZero();
    assertThat(count("customer_digital_contact")).isEqualTo(2);
    assertThat(count("customer_account_profile")).isEqualTo(3);
    assertThat(count("customer_account_link")).isEqualTo(2);
    assertThat(count("customer_account_consolidation")).isEqualTo(1);
    assertThat(
            text(
                "SELECT raw_payload#>>'{new_dynamic_section,flag}' AS value "
                    + "FROM cdc_customer.customer_identity"))
        .isEqualTo("A");
  }

  @Test
  void shouldReplaceChildrenAndIgnoreOlderSequence() {
    var newer = CustomerEventFixture.eventAt("2026-09-22T11:00:00Z", 1002);
    CustomerEventFixture.identity(newer).put("person_name", "UPDATED CUSTOMER");
    ((ArrayNode) newer.payload().path("digital_contacts")).remove(1);
    ((ArrayNode) newer.payload().path("customer_account_links")).remove(1);

    StepVerifier.create(
            repository
                .replace(CustomerEventFixture.snapshot(CustomerEventFixture.event()))
                .then(repository.replace(CustomerEventFixture.snapshot(newer)))
                .then(
                    repository.replace(
                        CustomerEventFixture.snapshot(CustomerEventFixture.event()))))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CustomerProjectionStatus.IGNORED))
        .verifyComplete();

    assertThat(text("SELECT person_name AS value FROM cdc_customer.customer_identity"))
        .isEqualTo("UPDATED CUSTOMER");
    assertThat(count("customer_digital_contact")).isEqualTo(1);
    assertThat(count("customer_account_link")).isEqualTo(1);
  }

  @Test
  void shouldRollbackAllSevenTableChangesWhenAConstraintFails() {
    var initial = CustomerEventFixture.event();
    var invalid = CustomerEventFixture.eventAt("2026-09-22T11:00:00Z", 1002);
    CustomerEventFixture.identity(invalid).put("person_name", "MUST ROLLBACK");
    ((ObjectNode) invalid.payload().path("digital_contacts").get(0)).put("contact_type", "9");

    StepVerifier.create(
            repository
                .replace(CustomerEventFixture.snapshot(initial))
                .then(repository.replace(CustomerEventFixture.snapshot(invalid))))
        .expectError(CustomerPersistenceException.class)
        .verify();

    assertThat(text("SELECT person_name AS value FROM cdc_customer.customer_identity"))
        .isEqualTo("STEVE ROGERS");
    assertThat(count("customer_digital_contact")).isEqualTo(2);
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
        .sql("SELECT count(*) AS total FROM cdc_customer." + table)
        .map(row -> row.get("total", Long.class))
        .one()
        .block(Duration.ofSeconds(5));
  }
}
