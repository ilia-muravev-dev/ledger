package dev.iliamuravev.ledger;

import dev.iliamuravev.ledger.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.from(LedgerApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
