package pe.com.scotiabank.tapp.service.registration.api.adapters.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.document.TransactionProjectionDocument;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfiguration {

  @Bean
  public ReactiveRedisTemplate<String, TransactionProjectionDocument>
      transactionProjectionRedisTemplate(
          ReactiveRedisConnectionFactory connectionFactory, JsonMapper jsonMapper) {

    RedisSerializer<TransactionProjectionDocument> valueSerializer =
        new TransactionProjectionRedisSerializer(jsonMapper);

    RedisSerializationContext<String, TransactionProjectionDocument> context =
        RedisSerializationContext.<String, TransactionProjectionDocument>newSerializationContext(
                RedisSerializer.string())
            .value(valueSerializer)
            .hashValue(valueSerializer)
            .build();

    return new ReactiveRedisTemplate<>(connectionFactory, context);
  }

  private record TransactionProjectionRedisSerializer(JsonMapper jsonMapper)
      implements RedisSerializer<TransactionProjectionDocument> {

    @Override
    public byte[] serialize(TransactionProjectionDocument value) {
      return value == null ? new byte[0] : jsonMapper.writeValueAsBytes(value);
    }

    @Override
    public TransactionProjectionDocument deserialize(byte[] bytes) {
      return bytes == null || bytes.length == 0
          ? null
          : jsonMapper.readValue(bytes, TransactionProjectionDocument.class);
    }
  }
}
