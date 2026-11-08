package dev.iliamuravev.ledger.transfer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TransferProperties.class)
class TransferEngineConfig {

    @Bean
    @Primary
    TransferEngine transferEngine(TransferProperties properties, PessimisticTransferEngine pessimistic,
            OptimisticTransferEngine optimistic) {
        return switch (properties.locking()) {
            case PESSIMISTIC -> pessimistic;
            case OPTIMISTIC -> optimistic;
        };
    }
}
