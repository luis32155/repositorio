package pe.com.scotiabank.tapp.service.registration.api.adapters.repository.document;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.EventType;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionEventDocument {

  private String eventId;

  private EventType eventType;

  private long aggregateVersion;

  private Instant occurredAt;
}
