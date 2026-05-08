package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.domain.entity.Address;

public interface AddressEnrichmentPort {
    Uni<Address> enrich(Address address);
}
