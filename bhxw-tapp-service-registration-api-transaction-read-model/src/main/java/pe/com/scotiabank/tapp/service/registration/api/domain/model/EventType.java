package pe.com.scotiabank.tapp.service.registration.api.domain.model;

public enum EventType {
  DebitRequested,
  ValidationPassed,
  DebitExecuted,
  CallbackRequested,
  CallbackDelivered,
  NotificationRequested,
  NotificationDelivered,
  LogAuditRequested,
  LogAuditDelivered
}
