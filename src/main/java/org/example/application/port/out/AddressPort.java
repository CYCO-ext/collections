package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.domain.entity.Address;

public interface AddressPort {
    Uni<Address> findById(String id);
}

