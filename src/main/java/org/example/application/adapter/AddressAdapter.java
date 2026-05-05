package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.AddressPort;
import org.example.domain.entity.Address;
import org.example.infrastructure.repository.AddressRepository;

@Singleton
public class AddressAdapter implements AddressPort {

    @Inject
    AddressRepository repository;

    @Override
    public Uni<Address> findById(String id) {
        return repository.findById(id);
    }
}

