package pe.com.scotiabank.tapp.customer.event.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomerEventConsumerApplication {

  public static void main(String[] args) {
    SpringApplication.run(CustomerEventConsumerApplication.class, args);
  }
}
