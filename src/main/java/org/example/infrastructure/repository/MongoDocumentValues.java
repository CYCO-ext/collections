package org.example.infrastructure.repository;

import org.bson.Document;

final class MongoDocumentValues {
    private MongoDocumentValues() {
    }

    static Double asDouble(Document document, String field) {
        Object value = document.get(field);
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
