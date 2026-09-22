package pe.com.scotiabank.tapp.service.registration.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pe.com.scotiabank.tapp.service.registration.api.fixtures.TransactionProjectionEventFixture;

class TransactionProjectionPayloadTest {

  @Test
  void shouldBuildProjectionIdFromTransactionAndRequestMessage() {
    var payload = TransactionProjectionEventFixture.completedEvent().payload();

    String documentId = payload.documentId();

    assertThat(documentId).isEqualTo("TXN-001|ABC-001");
  }
}
