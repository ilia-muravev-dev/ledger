package dev.iliamuravev.ledger.domain;

import java.util.Objects;
import java.util.UUID;

public record FxRoute(UUID sourcePositionId, UUID targetPositionId, FxConversion conversion) {

    public FxRoute {
        Objects.requireNonNull(sourcePositionId, "sourcePositionId");
        Objects.requireNonNull(targetPositionId, "targetPositionId");
        Objects.requireNonNull(conversion, "conversion");
    }
}
