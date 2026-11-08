package dev.iliamuravev.ledger.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledger.events")
public record EventsProperties(
        @DefaultValue("transfer-events") String topic,
        @DefaultValue("3") int partitions,
        @DefaultValue Relay relay) {

    public String deadLetterTopic() {
        return topic + ".DLT";
    }

    public record Relay(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("100") int batchSize,
            @DefaultValue("10s") Duration sendTimeout,
            @DefaultValue("7d") Duration retention) {
    }
}
