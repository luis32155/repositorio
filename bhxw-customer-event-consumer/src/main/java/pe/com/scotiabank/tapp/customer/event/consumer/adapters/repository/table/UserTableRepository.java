package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.UserEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface UserTableRepository extends ReactiveCrudRepository<UserEntity, UUID> {

  default Mono<UUID> insert(JsonNode user, Instant now) {
    return insertRow(user.toString(), now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.users
        (user_full_name, user_middle_name, user_country, user_preferred_language,
         user_email, user_email_domain, phone_number, document_type, document_number,
         person_type, birth_date, marital_status, gender, user_attributes,
         created_at, updated_at)
      SELECT p->>'user_full_name', p->>'user_middle_name', p->>'user_country',
        COALESCE(p->>'user_preferred_language', p->>'user_language'),
        p->>'user_email', p->>'user_email_domain', p->>'phone_number',
        CAST(p->>'document_type' AS integer), p->>'document_number', p->>'person_type',
        CAST(p->>'birth_date' AS date), p->>'marital_status', p->>'gender', p, :now, :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      RETURNING user_key
      """)
  Mono<UUID> insertRow(@Param("payload") String payload, @Param("now") Instant now);

  default Mono<Void> update(UUID userKey, JsonNode user, Instant now) {
    return updateRow(userKey, user.toString(), now).then();
  }

  @Modifying
  @Query(
      """
      UPDATE cdc_customer.users SET
        user_full_name = p->>'user_full_name',
        user_middle_name = p->>'user_middle_name',
        user_country = p->>'user_country',
        user_preferred_language = COALESCE(p->>'user_preferred_language', p->>'user_language'),
        user_email = p->>'user_email',
        user_email_domain = p->>'user_email_domain',
        phone_number = p->>'phone_number',
        document_type = CAST(p->>'document_type' AS integer),
        document_number = p->>'document_number',
        person_type = p->>'person_type',
        birth_date = CAST(p->>'birth_date' AS date),
        marital_status = p->>'marital_status',
        gender = p->>'gender',
        user_attributes = p,
        updated_at = :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      WHERE user_key = :userKey
      """)
  Mono<Integer> updateRow(
      @Param("userKey") UUID userKey, @Param("payload") String payload, @Param("now") Instant now);

  default Mono<Void> patch(UUID userKey, JsonNode user, Instant now) {
    return patchRow(userKey, user.toString(), now).then();
  }

  @Modifying
  @Query(
      """
      UPDATE cdc_customer.users SET
        user_full_name = COALESCE(p->>'user_full_name', user_full_name),
        user_middle_name = COALESCE(p->>'user_middle_name', user_middle_name),
        user_country = COALESCE(p->>'user_country', user_country),
        user_preferred_language = COALESCE(
          p->>'user_preferred_language', p->>'user_language', user_preferred_language),
        user_email = COALESCE(p->>'user_email', user_email),
        user_email_domain = COALESCE(p->>'user_email_domain', user_email_domain),
        phone_number = COALESCE(p->>'phone_number', phone_number),
        document_type = COALESCE(CAST(p->>'document_type' AS integer), document_type),
        document_number = COALESCE(p->>'document_number', document_number),
        person_type = COALESCE(p->>'person_type', person_type),
        birth_date = COALESCE(CAST(p->>'birth_date' AS date), birth_date),
        marital_status = COALESCE(p->>'marital_status', marital_status),
        gender = COALESCE(p->>'gender', gender),
        user_attributes = user_attributes || p,
        updated_at = :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      WHERE user_key = :userKey
      """)
  Mono<Integer> patchRow(
      @Param("userKey") UUID userKey, @Param("payload") String payload, @Param("now") Instant now);
}
