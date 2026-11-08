package dev.iliamuravev.ledger.fx;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledger.fx")
public record FxProperties(
        @DefaultValue("static-config") String source,
        Map<String, BigDecimal> usdPerUnit) {

    public FxProperties {
        usdPerUnit = usdPerUnit == null ? Map.of() : Map.copyOf(usdPerUnit);
    }
}
