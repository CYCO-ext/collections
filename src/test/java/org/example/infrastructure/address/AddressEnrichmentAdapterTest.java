package org.example.infrastructure.address;

import io.smallrye.mutiny.Uni;
import org.example.domain.entity.Address;
import org.example.infrastructure.event.SyncAddressEvent;
import org.example.infrastructure.repository.AddressCacheRepository;
import org.example.infrastructure.repository.AddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AddressEnrichmentAdapterTest {

    private AddressRepository addressRepository;
    private GoogleGeocodingClient googleGeocodingClient;
    private ViacepClient viacepClient;
    private AddressCacheRepository cacheRepository;
    private AddressEnrichmentAdapter adapter;

    @BeforeEach
    void setUp() {
        addressRepository = mock(AddressRepository.class);
        googleGeocodingClient = mock(GoogleGeocodingClient.class);
        viacepClient = mock(ViacepClient.class);
        cacheRepository = mock(AddressCacheRepository.class);

        adapter = new AddressEnrichmentAdapter();
        adapter.addressRepository = addressRepository;
        adapter.googleGeocodingClient = googleGeocodingClient;
        adapter.viacepClient = viacepClient;
        adapter.cacheRepository = cacheRepository;
        adapter.enrichmentEnabled = true;
        adapter.viacepEnabled = true;
    }

    @Test
    void enrichNormalizesWithViaCepGeneratesIdAndGeocodesWithGoogle() {
        Address viaCep = new Address();
        viaCep.setZipCode("01001000");
        viaCep.setStreet("Praça da Sé");
        viaCep.setCity("São Paulo");
        viaCep.setState("SP");

        when(viacepClient.geocodeCep("01001000")).thenReturn(Uni.createFrom().item(Optional.of(viaCep)));
        when(addressRepository.findDuplicate(any(Address.class))).thenReturn(Uni.createFrom().nullItem());
        when(cacheRepository.findByKey(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(googleGeocodingClient.geocode(any(GoogleGeocodingClient.GoogleGeocodingAddress.class)))
                .thenReturn(Uni.createFrom().item(Optional.of(new GoogleGeocodingClient.Coordinates(-23.55052, -46.633308))));
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
        assertEquals("viacep+google-geocoding", result.getEnrichmentSource());
        verify(addressRepository).findDuplicate(any(Address.class));
        verify(cacheRepository).upsert(anyString(), any(Address.class), anyString());

        ArgumentCaptor<GoogleGeocodingClient.GoogleGeocodingAddress> captor = ArgumentCaptor.forClass(GoogleGeocodingClient.GoogleGeocodingAddress.class);
        verify(googleGeocodingClient).geocode(captor.capture());
        assertEquals("Praça da Sé", captor.getValue().street());
        assertEquals("100", captor.getValue().number());
        assertEquals("São Paulo", captor.getValue().city());
        assertEquals("SP", captor.getValue().state());
        assertEquals("01001000", captor.getValue().zipCode());
    }

    @Test
    void enrichMarksAddressUnverifiedWhenGoogleReturnsEmpty() {
        when(viacepClient.geocodeCep("01001000")).thenReturn(Uni.createFrom().item(Optional.empty()));
        when(addressRepository.findDuplicate(any(Address.class))).thenReturn(Uni.createFrom().nullItem());
        when(cacheRepository.findByKey(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(googleGeocodingClient.geocode(any(GoogleGeocodingClient.GoogleGeocodingAddress.class)))
                .thenReturn(Uni.createFrom().item(Optional.empty()));
        when(cacheRepository.upsert(anyString(), any(Address.class), anyString())).thenReturn(Uni.createFrom().voidItem());

        SyncAddressEvent event = new SyncAddressEvent(null, "Main St", "Sao Paulo", "01001-000", "100", null, null);

        Address result = adapter.enrich(event).await().indefinitely();

        assertEquals("ADDRESS_UNVERIFIED", result.getEnrichmentStatus());
        assertEquals("viacep+google-geocoding", result.getEnrichmentSource());
        verify(cacheRepository).upsert(anyString(), any(Address.class), anyString());
    }

    @Test
    void enrichReturnsDuplicateWithoutCallingGoogleOrCache() {
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
                "viacep+google-geocoding"
        );

        when(viacepClient.geocodeCep("01001000")).thenReturn(Uni.createFrom().item(Optional.of(viaCep)));
        when(addressRepository.findDuplicate(any(Address.class))).thenReturn(Uni.createFrom().item(duplicate));

        SyncAddressEvent event = new SyncAddressEvent(null, null, null, "01001-000", "100", null, null);

        Address result = adapter.enrich(event).await().indefinitely();

        assertEquals("existing-address", result.getId());
        assertEquals("Praça da Sé", result.getStreet());
        assertEquals("São Paulo", result.getCity());
        assertEquals("100", result.getNumber());
        verify(googleGeocodingClient, never()).geocode(any(GoogleGeocodingClient.GoogleGeocodingAddress.class));
        verify(cacheRepository, never()).findByKey(anyString());
    }

    @Test
    void enrichReusesCachedCoordinatesWithoutCallingGoogle() {
        Address cached = new Address(
                "cached-address",
                "Praça da Sé",
                "São Paulo",
                "01001000",
                "100",
                "SP",
                -23.55052,
                -46.633308,
                "ENRICHED",
                "viacep+google-geocoding"
        );

        when(viacepClient.geocodeCep("01001000")).thenReturn(Uni.createFrom().item(Optional.empty()));
        when(addressRepository.findDuplicate(any(Address.class))).thenReturn(Uni.createFrom().nullItem());
        when(cacheRepository.findByKey(anyString())).thenReturn(Uni.createFrom().item(cached));

        SyncAddressEvent event = new SyncAddressEvent(null, "Praça da Sé", "São Paulo", "01001-000", "100", null, null);

        Address result = adapter.enrich(event).await().indefinitely();

        assertEquals(-23.55052, result.getLatitude());
        assertEquals(-46.633308, result.getLongitude());
        assertEquals("ENRICHED", result.getEnrichmentStatus());
        assertEquals("cache", result.getEnrichmentSource());
        verify(googleGeocodingClient, never()).geocode(any(GoogleGeocodingClient.GoogleGeocodingAddress.class));
    }

    @Test
    void enrichSkipsGoogleWhenCoordinatesAreProvided() {
        when(viacepClient.geocodeCep("01001000")).thenReturn(Uni.createFrom().item(Optional.empty()));
        when(addressRepository.findDuplicate(any(Address.class))).thenReturn(Uni.createFrom().nullItem());

        SyncAddressEvent event = new SyncAddressEvent(null, "Praça da Sé", "São Paulo", "01001-000", "100", -23.55052, -46.633308);

        Address result = adapter.enrich(event).await().indefinitely();

        assertEquals("ENRICHED", result.getEnrichmentStatus());
        assertEquals("provided", result.getEnrichmentSource());
        verify(cacheRepository, never()).findByKey(anyString());
        verify(googleGeocodingClient, never()).geocode(any(GoogleGeocodingClient.GoogleGeocodingAddress.class));
    }
}
