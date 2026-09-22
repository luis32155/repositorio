package pe.com.scotiabank.tapp.service.registration.api.domain.model;

public record ProjectionResult(
    ProjectionResultType result,
    String transactionId,
    String reqMsgId,
    String eventId,
    long incomingVersion,
    Long currentVersion) {

  public static ProjectionResult applied(TransactionProjectionEvent event) {
    return from(event, ProjectionResultType.APPLIED, event.payload().aggregateVersion());
  }

  public static ProjectionResult duplicate(TransactionProjectionEvent event, long currentVersion) {
    return from(event, ProjectionResultType.DUPLICATE_IGNORED, currentVersion);
  }

  public static ProjectionResult outdated(TransactionProjectionEvent event, long currentVersion) {
    return from(event, ProjectionResultType.OUTDATED_IGNORED, currentVersion);
  }

  private static ProjectionResult from(
      TransactionProjectionEvent event, ProjectionResultType result, long currentVersion) {
    return new ProjectionResult(
        result,
        event.payload().transactionId(),
        event.payload().reqMsgId(),
        event.metadata().eventId(),
        event.payload().aggregateVersion(),
        currentVersion);
  }
}
