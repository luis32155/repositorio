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
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.output.CustomerProfileRepositoryPort;
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
// Related invalid snapshots share the same reactive rejection assertion.
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.UnitTestContainsTooManyAsserts"})
class CustomerEventProjectionServiceTest {
  @Mock private CustomerProfileRepositoryPort repository;
  private CustomerEventProjectionService service;

  @BeforeEach
  void setUp() {
    service =
        new CustomerEventProjectionService(
            new CustomerEventValidator(), new CustomerSnapshotMapper(), repository);
  }

  @Test
  void shouldAcceptFullSnapshotAndUnknownFields() {
    var event = CustomerEventFixture.event();
    CustomerEventFixture.user(event).putObject("future_extension").put("feature", true);
    var expected = CustomerEventFixture.projectionResult();
    given(repository.upsert(any()))
        .willAnswer(
            call -> {
              var snapshot =
                  call.getArgument(
                      0,
                      pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot
                          .class);
              assertThat(snapshot.event().payload().path("user_details").has("future_extension"))
                  .isTrue();
              return Mono.just(expected);
            });
    StepVerifier.create(service.process(event)).expectNext(expected).verifyComplete();
  }

  @Test
  void shouldRejectMissingUserWithoutWriting() {
    var event = CustomerEventFixture.event();
    ((ObjectNode) event.payload()).remove("user_details");
    assertRejected(event);
  }

  @Test
  void shouldRejectMissingServicesInsteadOfDeletingPermissions() {
    var event = CustomerEventFixture.event();
    ((ObjectNode) CustomerEventFixture.user(event).path("entitlements")).remove("services");
    assertRejected(event);
  }

  @Test
  void shouldAcceptExplicitlyEmptyServices() {
    var event = CustomerEventFixture.event();
    ((ArrayNode) CustomerEventFixture.user(event).path("entitlements").path("services"))
        .removeAll();
    given(repository.upsert(any())).willReturn(Mono.just(CustomerEventFixture.projectionResult()));
    StepVerifier.create(service.process(event)).expectNextCount(1).verifyComplete();
  }

  @Test
  void shouldRejectDuplicateServiceIds() {
    var event = CustomerEventFixture.event();
    var services =
        (ArrayNode) CustomerEventFixture.user(event).path("entitlements").path("services");
    services.add(services.get(0).deepCopy());
    assertRejected(event);
  }

  @Test
  void shouldRejectDuplicateAccountsAndInvalidLimits() {
    var event = CustomerEventFixture.event();
    var accounts =
        (ArrayNode)
            CustomerEventFixture.user(event)
                .path("entitlements")
                .path("services")
                .get(0)
                .path("accounts");
    accounts.add(accounts.get(0).deepCopy());
    assertRejected(event);
    accounts.remove(2);
    ((ObjectNode) accounts.get(0)).put("transaction_limit", -1);
    assertRejected(event);
  }

  @Test
  void shouldRejectNullEventAndInvalidTimestamp() {
    assertRejected(null);
    assertRejected(CustomerEventFixture.eventAt("yesterday"));
  }

  @Test
  void shouldPropagatePersistenceFailure() {
    given(repository.upsert(any()))
        .willReturn(Mono.error(new CustomerPersistenceException("offline")));
    StepVerifier.create(service.process(CustomerEventFixture.event()))
        .expectError(CustomerPersistenceException.class)
        .verify();
  }

  private void assertRejected(CustomerEventEnvelope event) {
    StepVerifier.create(service.process(event))
        .expectError(InvalidCustomerEventException.class)
        .verify();
    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectInvalidBirthAndConstitutionDates() {
    var birth = CustomerEventFixture.event();
    CustomerEventFixture.user(birth).put("birth_date", "2026-02-30");
    assertRejected(birth);
    var constitution = CustomerEventFixture.event();
    ((ObjectNode) CustomerEventFixture.user(constitution).path("enterprise_details"))
        .put("constitution_date", "25/12/2026");
    assertRejected(constitution);
  }
}
