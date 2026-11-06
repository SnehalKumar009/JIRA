package dev.tracker.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializationFailedException;
import org.springframework.core.serializer.support.SerializingConverter;

@Configuration(proxyBeanMethods = false)
class SessionConfig {

    private static final Logger log = LoggerFactory.getLogger(SessionConfig.class);

    /**
     * Session attributes are Java-serialized into PostgreSQL. If an upgrade changes a serialized
     * class, the stale attribute is dropped instead of failing every request of that session; the
     * user simply signs in again.
     */
    @Bean("springSessionConversionService")
    GenericConversionService springSessionConversionService() {
        GenericConversionService conversion = new GenericConversionService();
        conversion.addConverter(Object.class, byte[].class, new SerializingConverter());
        DeserializingConverter deserializer = new DeserializingConverter(SessionConfig.class.getClassLoader());
        conversion.addConverter(byte[].class, Object.class, bytes -> {
            try {
                return deserializer.convert(bytes);
            } catch (SerializationFailedException e) {
                log.warn("Dropping a session attribute that no longer deserializes: {}", e.getMessage());
                return null;
            }
        });
        return conversion;
    }
}
