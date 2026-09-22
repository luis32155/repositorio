package pe.com.scotiabank.tapp.customer.event.consumer.adapters.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class PostgresTransactionConfiguration {
  @Bean
  public TransactionalOperator customerTransactions(ReactiveTransactionManager manager) {
    return TransactionalOperator.create(manager);
  }
}
