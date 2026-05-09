
package org.example.infrastructure.address;

import io.smallrye.mutiny.Uni;
import org.example.domain.entity.Address;
import org.example.infrastructure.event.SyncAddressEvent;
import org.example.infrastructure.repository.AddressCacheRepository;
import org.example.infrastructure.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AddressEnrichmentAdapterTest {

    private AddressRepository addressRepository;
    private NominatimClient nominatimClient;
    private ViacepClient viacepClient;
    private AddressCacheRepository cacheRepository;
    private AddressEnrichmentAdapter adapter;

    @BeforeEach
    void setUp() {
        addressRepository = mock(AddressRepository.class);
        nominatimClient = mock(NominatimClient.class);
        viacepClient = mock(ViacepClient.class);
        cacheRepository = mock(AddressCacheRepository.class);

        adapter = new AddressEnrichmentAdapter();
        adapter.addressRepository = addressRepository;
        adapter.nominatimClient = nominatimClient;
        adapter.viacepClient = viacepClient;
        adapter.cacheRepository = cacheRepository;
        adapter.enrichmentEnabled = true;
        adapter.viacepEnabled = true;
        adapter.userAgent = "collections-service-test";
    }

    @Test
    void enrichNormalizesWithViaCepGeneratesIdAndGeocodes() {
        Address viaCep = new Address();
        viaCep.setZipCode("01001000");
        viaCep.setStreet("Praça da Sé");
        viaCep.setCity("São Paulo");
        viaCep.setState("SP");

        when(viacepClient.geocodeCep("01001000")).thenReturn(Uni.createFrom().item(Optional.of(viaCep)));
        when(addressRepository.findDuplicate(any(Address.class))).thenReturn(Uni.createFrom().nullItem());
        when(cacheRepository.findByKey(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(nominatimClient.geocode(anyString(), anyString()))
                .thenReturn(Uni.createFrom().item(Optional.of(new double[]{-23.55052, -46.633308})));
        when(cacheRepository.upsert(anyString(), any(Address.class), anyString())).thenReturn(Uni.createFrom().voidItem());

        SyncAddressEvent event = new SyncAddressEvent(null, null, null, "01001-000", "100", null, null);

        Address result = adapter.enrich(event).await().indefinitely();

        assertNotNull(result.getId());
        assertEquals("01001000", result.getZipCode());
        assertEquals("Praça da Sé", result.getStreet());
        assertEquals("São Paulo", result.getCity());
        assertEquals("SP", result.getState());
        assertEquals("100", result.getNumber());
        assertEquals(-23.55052, result.getLatitude());
        assertEquals(-46.633308, result.getLongitude());
        assertEquals("ENRICHED", result.getEnrichmentStatus());
        assertEquals("viacep+nominatim", result.getEnrichmentSource());
        verify(addressRepository).findDuplicate(any(Address.class));
        verify(cacheRepository).upsert(anyString(), any(Address.class), anyString());
    }

    @Test
    void enrichReturnsDuplicateWithoutCallingGeocoding() {
        Address viaCep = new Address();
        viaCep.setZipCode("01001000");
        viaCep.setStreet("Praça da Sé");
        viaCep.setCity("São Paulo");
        viaCep.setState("SP");

        Address duplicate = new Address(
                "existing-address",
                "Praça da Sé",
                "São Paulo",
                "01001000",
                "100",
                "SP",
                -23.55052,
                -46.633308,
                "ENRICHED",
                "viacep+nominatim"
        );

        when(viacepClient.geocodeCep("01001000")).thenReturn(Uni.createFrom().item(Optional.of(viaCep)));
        when(addressRepository.findDuplicate(any(Address.class))).thenReturn(Uni.createFrom().item(duplicate));

        SyncAddressEvent event = new SyncAddressEvent(null, null, null, "01001-000", "100", null, null);

        Address result = adapter.enrich(event).await().indefinitely();

        assertEquals("existing-address", result.getId());
        assertEquals("Praça da Sé", result.getStreet());
        assertEquals("São Paulo", result.getCity());
        assertEquals("100", result.getNumber());
        verify(nominatimClient, never()).geocode(anyString(), anyString());
        verify(cacheRepository, never()).findByKey(anyString());
    }
}
