package org.example.application.adapter;
import io.smallrye.mutiny.Uni;
import org.example.domain.entity.Address;
import org.example.domain.entity.CollectionRequest;
import java.util.List;
import java.util.Map;
public interface AddressEnrichmentAdapter {
    Uni<Address> enrich(CollectionRequest request);
    Uni<Map<String, Address>> enrichBatch(List<CollectionRequest> requests);
}
