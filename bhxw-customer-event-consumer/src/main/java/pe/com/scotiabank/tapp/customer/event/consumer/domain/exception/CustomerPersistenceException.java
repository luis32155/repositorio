package pe.com.scotiabank.tapp.customer.event.consumer.domain.exception;

public class CustomerPersistenceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CustomerPersistenceException(String message) {
    super(message);
  }

  public CustomerPersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
