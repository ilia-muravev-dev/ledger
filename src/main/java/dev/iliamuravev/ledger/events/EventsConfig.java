package dev.iliamuravev.ledger.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(EventsProperties.class)
class EventsConfig {

    @Bean
    NewTopic transferEventsTopic(EventsProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(properties.partitions()).replicas(1).build();
    }

    @Bean
    NewTopic transferEventsDeadLetterTopic(EventsProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic()).partitions(properties.partitions()).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template, EventsProperties properties) {
        var backOff = new ExponentialBackOff(200, 2.0);
        backOff.setMaxElapsedTime(5_000);
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        return handler;
    }
}
