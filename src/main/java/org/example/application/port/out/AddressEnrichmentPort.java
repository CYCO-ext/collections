package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.domain.entity.Address;
import org.example.infrastructure.event.SyncAddressEvent;

public interface AddressEnrichmentPort {
    Uni<Address> enrich(SyncAddressEvent event);
}
