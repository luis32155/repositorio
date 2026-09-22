package pe.com.scotiabank.tapp.service.registration.api.adapters.repository.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "transaction_projection", schema = "transaction_read_model")
public record TransactionProjectionEntity(@Id String documentId) {}
