package pe.com.scotiabank.tapp.customer.event.consumer.adapters.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;

@EnabledIfEnvironmentVariable(named = "CUSTOMER_TEST_R2DBC_URL", matches = ".+")
@SpringBootTest(
    properties = {
      "spring.sql.init.mode=always",
      "spring.r2dbc.username=customer_test",
      "spring.r2dbc.password=",
      "app.kafka.topic=customer-integration",
      "app.kafka.consumer-group=customer-integration-group",
      "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
      "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = "customer-integration",
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class CustomerKafkaPostgresIntegrationTest {
  @Autowired
  @Qualifier("applicationTaskExecutor")
  private AsyncTaskExecutor applicationTasks;

  @Autowired private KafkaTemplate<String, CustomerEventEnvelope> kafka;
  @Autowired private DatabaseClient database;
  @Autowired private KafkaListenerEndpointRegistry registry;

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrap;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.r2dbc.url", () -> System.getenv("CUSTOMER_TEST_R2DBC_URL"));
  }

  @Test
  void shouldUseVirtualThreadsForSpringApplicationTasks() throws Exception {
    var result = new CompletableFuture<Boolean>();
    applicationTasks.execute(() -> result.complete(Thread.currentThread().isVirtual()));
    assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void shouldCommitOnlyAfterProjectionAndStopWithoutCommittingInvalidRecord() throws Exception {
    database
        .sql("TRUNCATE cdc_customer.users, cdc_customer.enterprises CASCADE")
        .fetch()
        .rowsUpdated()
        .block(Duration.ofSeconds(5));
    var event = CustomerEventFixture.event();
    kafka.send("customer-integration", "40723|55421", event).get(10, TimeUnit.SECONDS);
    try (var admin =
        AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(
              () -> {
                var count =
                    database
                        .sql("SELECT count(*) AS total FROM cdc_customer.user_service_accounts")
                        .map(row -> row.get("total", Long.class))
                        .one()
                        .block(Duration.ofSeconds(5));
                assertThat(count).isEqualTo(2);
                assertThat(committed(admin)).isEqualTo(1);
              });
      kafka
          .send(
              "customer-integration",
              "40723|55421",
              new CustomerEventEnvelope(null, event.metadata()))
          .get(10, TimeUnit.SECONDS);
      await()
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(
              () ->
                  assertThat(registry.getListenerContainer("customerEvents").isRunning())
                      .isFalse());
      assertThat(committed(admin)).isEqualTo(1);
    }
  }

  private long committed(AdminClient admin) throws Exception {
    var offsets =
        admin
            .listConsumerGroupOffsets("customer-integration-group")
            .partitionsToOffsetAndMetadata()
            .get(5, TimeUnit.SECONDS);
    var offset = offsets.get(new TopicPartition("customer-integration", 0));
    return offset == null ? -1 : offset.offset();
  }
}
