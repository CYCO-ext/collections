# Tasks: collection-address

Overview
--------
Atomic implementation tasks for enriching collection search and collection-by-id responses with full address information.

Implementation Status
---------------------

- Status: Completed
- Search endpoint: `GET /api/collections/search` now returns `addressId` plus a full `address` object.
- By-id endpoint: `GET /api/collections/{id}` uses the same enriched `CollectionSearchResult` shape.
- Address source: `AddressPort.findById(addressId)`.
- Error behavior: missing collection addresses return HTTP 404 with `Collection address not found: {addressId}`.
- Existing behavior preserved: status, collectorId, and generatorId filters; newest-first ordering from the collection search port; blank id and collection-not-found behavior.
- Verification: `SearchCollectionsUseCaseTest`, `GetCollectionByIdUseCaseTest`, `CollectionSearchResourceTest`, and IDE project build passed.

Phase 1 - Contract and Response Shape
-------------------------------------

T0 - Confirm enriched collection response contract

- ID: collection-address-T0
- Status: Completed
- Traceability: CADDR-001, CADDR-002, CADDR-003, CADDR-006
- Depends on: none
- What: Confirm that collection search keeps all existing fields, keeps `addressId`, and adds an `address` object with the complete address field set. Confirm collection-by-id should use the same enriched response shape.
- Where: `.specs/features/collection-address/spec.md`, `.specs/features/collection-address/design.md`.
- Done: Contract keeps all existing fields, preserves `addressId`, and adds the embedded address object for search and by-id responses.
- Tests: Documentation review.
- Gate: Passed.

T1 - Add collection address result model

- ID: collection-address-T1
- Status: Completed
- Traceability: CADDR-001, CADDR-002
- Depends on: T0
- What: Add an application result record for the embedded address payload with id, street, number, city, state, zipCode, latitude, longitude, enrichmentStatus, and enrichmentSource.
- Where: `src/main/java/org/example/application/usecase/SearchCollectionsUseCase.java`.
- Done: Added `CollectionAddressResult` without changing persistence entities.
- Tests: `SearchCollectionsUseCaseTest`, `GetCollectionByIdUseCaseTest`, and `CollectionSearchResourceTest` passed.
- Gate: IDE build passed.

T2 - Update collection search result shape

- ID: collection-address-T2
- Status: Completed
- Traceability: CADDR-001, CADDR-002, CADDR-006
- Depends on: T1
- What: Add `address` to `CollectionSearchResult` while preserving `addressId` and all existing fields.
- Where: `src/main/java/org/example/application/usecase/SearchCollectionsUseCase.java`.
- Done: `CollectionSearchResult` now includes `address`; search and by-id use cases expose the same enriched result model.
- Tests: Existing tests updated for the new constructor and field.
- Gate: IDE build passed.

Phase 2 - Application Enrichment
--------------------------------

T3 - Add missing address exception

- ID: collection-address-T3
- Status: Completed
- Traceability: CADDR-005
- Depends on: T0
- What: Add an application exception for missing collection addresses so REST can return HTTP 404.
- Where: `src/main/java/org/example/application/usecase/CollectionAddressNotFoundException.java`.
- Done: Missing address ids produce `Collection address not found: {addressId}`.
- Tests: Unit and REST tests cover the exception path.
- Gate: IDE build passed.

T4 - Inject AddressPort into search use case

- ID: collection-address-T4
- Status: Completed
- Traceability: CADDR-004, CADDR-007
- Depends on: T1, T2, T3
- What: Inject `AddressPort` and enrich each collection request returned by `CollectionRequestPort.search(query)` through `AddressPort.findById(addressId)`.
- Where: `src/main/java/org/example/application/usecase/SearchCollectionsUseCase.java`.
- Done: Search results include full address data and preserve the returned collection order.
- Tests: `SearchCollectionsUseCaseTest` passed.
- Gate: Focused use case test passed.

T5 - Enrich collection-by-id response

- ID: collection-address-T5
- Status: Completed
- Traceability: CADDR-004, CADDR-005, CADDR-006, CADDR-007
- Depends on: T4
- What: Update `GetCollectionByIdUseCase` to reuse the same address mapping/enrichment behavior used by search.
- Where: `src/main/java/org/example/application/usecase/GetCollectionByIdUseCase.java`.
- Done: `GetCollectionByIdUseCase` delegates response enrichment to `SearchCollectionsUseCase.toResult(...)`.
- Tests: `GetCollectionByIdUseCaseTest` passed.
- Gate: Focused use case test passed.

Phase 3 - REST Error Mapping
----------------------------

T6 - Map collection address missing errors to HTTP 404

- ID: collection-address-T6
- Status: Completed
- Traceability: CADDR-005
- Depends on: T3, T4, T5
- What: Update the collection REST resource so missing address failures return HTTP 404 for both search and by-id endpoints.
- Where: `src/main/java/org/example/presentation/rest/CollectionSearchResource.java`.
- Done: Both search and by-id endpoints map `CollectionAddressNotFoundException` to HTTP 404.
- Tests: `CollectionSearchResourceTest` passed.
- Gate: Focused REST test passed.

Phase 4 - Tests and Documentation
---------------------------------

T7 - Update search use case tests

- ID: collection-address-T7
- Status: Completed
- Traceability: CADDR-001, CADDR-002, CADDR-003, CADDR-004, CADDR-005, CADDR-007, CADDR-008
- Depends on: T4
- What: Add or update tests for enriched address success, missing address, filter preservation, order preservation, and read-only behavior.
- Where: `src/test/java/org/example/application/usecase/SearchCollectionsUseCaseTest.java`.
- Done: Search tests cover address enrichment, missing address, filter parsing/trimming, order preservation, invalid status, and no collection update calls.
- Tests: `SearchCollectionsUseCaseTest` passed.
- Gate: Passed.

T8 - Update collection-by-id use case tests

- ID: collection-address-T8
- Status: Completed
- Traceability: CADDR-002, CADDR-005, CADDR-006, CADDR-007, CADDR-008
- Depends on: T5
- What: Update by-id tests for enriched address success and missing address behavior.
- Where: `src/test/java/org/example/application/usecase/GetCollectionByIdUseCaseTest.java`.
- Done: By-id tests cover full address information, blank id validation, collection not found, missing address, and no update calls.
- Tests: `GetCollectionByIdUseCaseTest` passed.
- Gate: Passed.

T9 - Update REST tests

- ID: collection-address-T9
- Status: Completed
- Traceability: CADDR-001, CADDR-002, CADDR-005, CADDR-006, CADDR-008
- Depends on: T6
- What: Update collection REST tests to assert `address` is present in search and by-id responses and missing address maps to HTTP 404.
- Where: `src/test/java/org/example/presentation/rest/CollectionSearchResourceTest.java`.
- Done: REST tests cover enriched search/by-id results and missing address 404 mapping for both endpoints.
- Tests: `CollectionSearchResourceTest` passed.
- Gate: Passed.

T10 - Update README/API docs

- ID: collection-address-T10
- Status: Completed
- Traceability: CADDR-001, CADDR-002, CADDR-005, CADDR-006, CADDR-008
- Depends on: T2, T6
- What: Document the new `address` object in `GET /api/collections/search` and `GET /api/collections/{id}` examples, including the missing address HTTP 404 behavior.
- Where: `README.md`.
- Done: README examples show the embedded address object and missing address 404 behavior.
- Tests: Documentation review only.
- Gate: Passed.

T11 - Final verification

- ID: collection-address-T11
- Status: Completed
- Traceability: CADDR-008
- Depends on: T0 through T10
- What: Run focused use case tests, REST tests, and project build.
- Where: IDE test runner and project build.
- Done: All focused tests and build passed.
- Tests: `SearchCollectionsUseCaseTest`, `GetCollectionByIdUseCaseTest`, `CollectionSearchResourceTest`, and IDE project build.
- Gate: Build passed with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T1 and T3 were completed before application enrichment.
- T5 reused the search enrichment helper so search and by-id responses remain aligned.
- T7 through T11 completed the focused test, documentation, and build verification gates.
