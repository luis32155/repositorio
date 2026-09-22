package pe.com.scotiabank.tapp.service.registration.api.domain.exception;

public class VersionConflictException extends RuntimeException {

  public VersionConflictException(String message) {
    super(message);
  }
}
