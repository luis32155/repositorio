package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "customer_identity", schema = "cdc_customer")
public record CustomerIdentityEntity(@Id UUID customerIdentityKey) {}
