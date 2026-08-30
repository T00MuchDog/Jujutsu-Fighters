package com.jjktbf.server.challenge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** JSON codec for ordered per-fighter move sets stored with match participants. */
final class MoveSetCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<List<String>>> TYPE = new TypeReference<>() { };

    private MoveSetCodec() {
    }

    static String encode(List<List<String>> moveSetIds) {
        List<List<String>> safe = moveSetIds == null ? List.of() : moveSetIds.stream()
            .map(ids -> ids == null ? List.<String>of() : List.copyOf(ids))
            .toList();
        try {
            return MAPPER.writeValueAsString(safe);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Move sets could not be encoded", exception);
        }
    }

    static List<List<String>> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        try {
            List<List<String>> decoded = MAPPER.readValue(encoded, TYPE);
            if (decoded == null) return List.of();
            return decoded.stream()
                .map(ids -> ids == null ? List.<String>of() : List.copyOf(ids))
                .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored move sets are invalid", exception);
        }
    }
}
