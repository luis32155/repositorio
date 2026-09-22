package pe.com.scotiabank.tapp.service.registration.api.domain.exception;

public class ProjectionPersistenceException extends RuntimeException {

  public ProjectionPersistenceException(String message) {
    super(message);
  }

  public ProjectionPersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
