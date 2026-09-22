package pe.com.scotiabank.tapp.customer.event.consumer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.scotiabank.tapp.customer.event.consumer.application.mapping.CustomerSnapshotMapper;
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.output.CustomerCoreRepositoryPort;
import pe.com.scotiabank.tapp.customer.event.consumer.application.validation.CustomerEventValidator;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class CustomerEventProjectionServiceTest {
  @Mock private CustomerCoreRepositoryPort repository;
  private CustomerEventProjectionService service;

  @BeforeEach
  void setUp() {
    service =
        new CustomerEventProjectionService(
            new CustomerEventValidator(), new CustomerSnapshotMapper(), repository);
  }

  @Test
  void shouldAcceptFullDynamicSnapshot() {
    var event = CustomerEventFixture.event();
    CustomerEventFixture.payload(event).putObject("future_section").put("enabled", true);
    var expected = CustomerEventFixture.projectionResult();
    given(repository.replace(any()))
        .willAnswer(
            call -> {
              var snapshot =
                  call.getArgument(
                      0,
                      pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot
                          .class);
              assertThat(snapshot.payload().path("future_section").path("enabled").asBoolean())
                  .isTrue();
              assertThat(snapshot.customerKey()).isEqualTo("604|01|12345678");
              return Mono.just(expected);
            });
    StepVerifier.create(service.process(event)).expectNext(expected).verifyComplete();
  }

  @Test
  void shouldAcceptEmptyArraySections() {
    var event = CustomerEventFixture.event();
    CustomerEventFixture.payload(event).putArray("digital_contacts");
    CustomerEventFixture.payload(event).putArray("customer_account_links");
    CustomerEventFixture.payload(event).putArray("account_consolidations");
    CustomerEventFixture.payload(event).putArray("account_profiles");
    given(repository.replace(any())).willReturn(Mono.just(CustomerEventFixture.projectionResult()));
    StepVerifier.create(service.process(event)).expectNextCount(1).verifyComplete();
  }

  @Test
  void shouldRejectMissingIdentityOrNonFullSnapshot() {
    var missing = CustomerEventFixture.event();
    CustomerEventFixture.payload(missing).remove("customer_identity");
    assertRejected(missing);

    var partial = CustomerEventFixture.event();
    CustomerEventFixture.payload(partial).put("snapshot_mode", "PARTIAL");
    assertRejected(partial);
  }

  @Test
  void shouldRejectMissingArrayAndDuplicateAccount() {
    var missing = CustomerEventFixture.event();
    CustomerEventFixture.payload(missing).remove("digital_contacts");
    assertRejected(missing);

    var duplicate = CustomerEventFixture.event();
    var accounts = (ArrayNode) duplicate.payload().path("account_profiles");
    accounts.add(accounts.get(0).deepCopy());
    assertRejected(duplicate);
  }

  @Test
  void shouldRejectBrokenCustomerAndAccountRelationships() {
    var wrongCustomer = CustomerEventFixture.event();
    ((ObjectNode) wrongCustomer.payload().path("digital_contacts").get(0))
        .put("document_number", "OTHER");
    assertRejected(wrongCustomer);

    var missingAccount = CustomerEventFixture.event();
    ((ObjectNode) missingAccount.payload().path("customer_account_links").get(0))
        .put("account_number", "111111111");
    assertRejected(missingAccount);
  }

  @Test
  void shouldRejectInvalidDateTimestampAndSequence() {
    var date = CustomerEventFixture.event();
    ((ObjectNode) date.payload().path("individual_profile")).put("birth_date", "2026-02-30");
    assertRejected(date);

    var timestamp = CustomerEventFixture.event();
    ((ObjectNode) timestamp.metadata()).put("event_timestamp", "yesterday");
    assertRejected(timestamp);

    var sequence = CustomerEventFixture.event();
    ((ObjectNode) sequence.metadata()).put("source_sequence", "not-a-number");
    assertRejected(sequence);
  }

  @Test
  void shouldRejectSnapshotWithoutPersonProfile() {
    var event = CustomerEventFixture.event();
    CustomerEventFixture.payload(event).remove("individual_profile");
    assertRejected(event);
  }

  @Test
  void shouldPropagatePersistenceFailure() {
    given(repository.replace(any()))
        .willReturn(Mono.error(new CustomerPersistenceException("offline")));
    StepVerifier.create(service.process(CustomerEventFixture.event()))
        .expectError(CustomerPersistenceException.class)
        .verify();
  }

  @Test
  void shouldRejectNullEvent() {
    assertRejected(null);
  }

  private void assertRejected(CustomerEventEnvelope event) {
    StepVerifier.create(service.process(event))
        .expectError(InvalidCustomerEventException.class)
        .verify();
    verifyNoInteractions(repository);
  }
}
