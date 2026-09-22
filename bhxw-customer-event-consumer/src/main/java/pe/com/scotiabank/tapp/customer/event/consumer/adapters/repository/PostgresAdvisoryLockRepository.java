package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Executes the PostgreSQL transaction lock that has no CRUD entity representation. */
@Repository
@RequiredArgsConstructor
public class PostgresAdvisoryLockRepository {
  private final DatabaseClient database;

  public Mono<Void> acquire(String identity) {
    return database
        .sql("SELECT pg_advisory_xact_lock(hashtextextended(:identity, 0))")
        .bind("identity", identity)
        .map((row, metadata) -> true)
        .one()
        .then();
  }
}
