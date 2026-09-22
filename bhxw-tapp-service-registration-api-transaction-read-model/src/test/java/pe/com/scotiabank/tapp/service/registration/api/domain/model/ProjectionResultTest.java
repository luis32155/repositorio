package pe.com.scotiabank.tapp.service.registration.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pe.com.scotiabank.tapp.service.registration.api.fixtures.TransactionProjectionEventFixture;

@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class ProjectionResultTest {

  @Test
  void shouldCreateAppliedResult() {
    var event = TransactionProjectionEventFixture.completedEvent();

    ProjectionResult result = ProjectionResult.applied(event);

    assertCommonFields(result);
    assertThat(result.result()).isEqualTo(ProjectionResultType.APPLIED);
    assertThat(result.currentVersion()).isEqualTo(event.payload().aggregateVersion());
  }

  @Test
  void shouldCreateDuplicateResultWithCurrentStoredVersion() {
    var event = TransactionProjectionEventFixture.completedEvent();

    ProjectionResult result = ProjectionResult.duplicate(event, 15L);

    assertCommonFields(result);
    assertThat(result.result()).isEqualTo(ProjectionResultType.DUPLICATE_IGNORED);
    assertThat(result.currentVersion()).isEqualTo(15L);
  }

  @Test
  void shouldCreateOutdatedResultWithCurrentStoredVersion() {
    var event = TransactionProjectionEventFixture.completedEvent();

    ProjectionResult result = ProjectionResult.outdated(event, 20L);

    assertCommonFields(result);
    assertThat(result.result()).isEqualTo(ProjectionResultType.OUTDATED_IGNORED);
    assertThat(result.currentVersion()).isEqualTo(20L);
  }

  private void assertCommonFields(ProjectionResult result) {
    assertThat(result.transactionId()).isEqualTo(TransactionProjectionEventFixture.TRANSACTION_ID);
    assertThat(result.reqMsgId()).isEqualTo(TransactionProjectionEventFixture.REQ_MSG_ID);
    assertThat(result.eventId()).isEqualTo(TransactionProjectionEventFixture.EVENT_ID);
    assertThat(result.incomingVersion())
        .isEqualTo(TransactionProjectionEventFixture.AGGREGATE_VERSION);
  }
}
