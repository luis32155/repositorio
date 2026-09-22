package pe.com.scotiabank.tapp.customer.event.consumer.domain.exception;

public class InvalidCustomerEventException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidCustomerEventException(String message) {
    super(message);
  }

  public InvalidCustomerEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
