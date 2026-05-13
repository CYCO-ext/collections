# Feature: collection-address

Problem
-------
Collection search currently returns `addressId` only. Clients that render collection lists or collection details need the complete address information without making a second request per collection.

Goal
----
When searching collections, include the full address information for each returned collection request.

The same enriched response should be used by the collection-by-id/detail flow when it reuses the search collection response model, so list and detail clients can share one payload shape.

Scope
-----

In scope:

- Enrich collection search results with full address information from the existing address store.
- Keep `addressId` in the collection summary for backward compatibility.
- Add an `address` object containing id, street, number, city, state, zipCode, latitude, longitude, enrichmentStatus, and enrichmentSource.
- Reuse the existing `AddressPort.findById(id)` persistence boundary.
- Preserve existing search filters: status, collectorId, and generatorId.
- Preserve newest-first ordering from `search-collections`.
- Apply the same response shape to collection-by-id if it reuses `SearchCollectionsUseCase.CollectionSearchResult`.
- Add unit and REST tests for successful address enrichment and missing address behavior.
- Update README/API documentation.

Out of scope:

- Creating or updating address records.
- Address geocoding or enrichment calls.
- Collector address endpoint changes.
- Pagination or new search filters.
- Removing the existing `addressId` field.
- Adding MongoDB joins or aggregation unless needed for performance later.

Constraints & Assumptions
-------------------------

- Collection requests store only `addressId`.
- Full address data is available through `AddressPort.findById(id)`.
- Search remains a read-only operation.
- Search results must remain ordered by `createdAt` descending after enrichment.
- If an address is missing for a collection request, the first implementation should fail explicitly with HTTP 404 instead of returning partial address data silently.
- Runtime REST paths include the global `/api` prefix.

Acceptance Criteria
-------------------

AC-1: `GET /api/collections/search` returns each collection with an `address` object in addition to `addressId`.

AC-2: The `address` object includes id, street, number, city, state, zipCode, latitude, longitude, enrichmentStatus, and enrichmentSource.

AC-3: Search filters by status, collectorId, and generatorId continue to work.

AC-4: Search results remain ordered with most recent `createdAt` first.

AC-5: Missing collection addresses return HTTP 404 with a clear message.

AC-6: Collection-by-id responses include the same address object if they reuse the search result model.

AC-7: The search operation remains read-only and does not mutate collections or addresses.

AC-8: Focused unit and REST tests cover enriched success, missing address, existing filters, and collection-by-id response shape.

Traceability IDs
----------------

- CADDR-001: Address object in collection search response
- CADDR-002: Complete address field set
- CADDR-003: Existing filter and ordering preservation
- CADDR-004: Address lookup through `AddressPort`
- CADDR-005: Missing address HTTP 404 behavior
- CADDR-006: Collection-by-id response alignment
- CADDR-007: Read-only behavior
- CADDR-008: Focused tests and documentation
