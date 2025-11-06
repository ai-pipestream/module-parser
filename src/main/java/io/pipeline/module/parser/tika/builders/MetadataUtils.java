package io.pipeline.module.parser.tika.builders;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.Timestamp;
import io.pipeline.parsed.data.tika.base.v1.TikaBaseFields;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.function.Consumer;
import java.util.Base64;

/**
 * Utility methods for mapping Tika metadata to protobuf structures.
 * 
 * Provides common functionality for all metadata builders including:
 * - Type conversion utilities
 * - Struct building for unmapped metadata
 * - Base fields population
 * - Safe field extraction with logging
 */
public class MetadataUtils {
    
    private static final Logger LOG = Logger.getLogger(MetadataUtils.class);
    
    /**
     * Maps a string field from Tika metadata to protobuf builder.
     * 
     * @param metadata Tika metadata object
     * @param key Metadata key (can be String or Property)
     * @param setter Protobuf builder setter method
     * @param mappedFields Set to track mapped fields
     */
    public static void mapStringField(Metadata metadata, Object key, Consumer<String> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            setter.accept(value.trim());
            mappedFields.add(keyStr);
            LOG.tracef("Mapped string field: %s = %s", keyStr, value);
        }
    }
    
    /**
     * Maps an integer field from Tika metadata to protobuf builder.
     */
    public static void mapIntField(Metadata metadata, Object key, Consumer<Integer> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            try {
                int intValue = Integer.parseInt(value.trim());
                setter.accept(intValue);
                mappedFields.add(keyStr);
                LOG.tracef("Mapped int field: %s = %d", keyStr, intValue);
            } catch (NumberFormatException e) {
                LOG.warnf("Failed to parse integer value for field %s: %s", keyStr, value);
                // Don't add to mappedFields so it goes to struct as string
            }
        }
    }
    
    /**
     * Maps a long field from Tika metadata to protobuf builder.
     */
    public static void mapLongField(Metadata metadata, Object key, Consumer<Long> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            try {
                long longValue = Long.parseLong(value.trim());
                setter.accept(longValue);
                mappedFields.add(keyStr);
                LOG.tracef("Mapped long field: %s = %d", keyStr, longValue);
            } catch (NumberFormatException e) {
                LOG.warnf("Failed to parse long value for field %s: %s", keyStr, value);
                // Don't add to mappedFields so it goes to struct as string
            }
        }
    }
    
    /**
     * Maps a double field from Tika metadata to protobuf builder.
     */
    public static void mapDoubleField(Metadata metadata, Object key, Consumer<Double> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            try {
                double doubleValue = Double.parseDouble(value.trim());
                setter.accept(doubleValue);
                mappedFields.add(keyStr);
                LOG.tracef("Mapped double field: %s = %f", keyStr, doubleValue);
            } catch (NumberFormatException e) {
                LOG.warnf("Failed to parse double value for field %s: %s", keyStr, value);
                // Don't add to mappedFields so it goes to struct as string
            }
        }
    }
    
    /**
     * Maps a boolean field from Tika metadata to protobuf builder.
     */
    public static void mapBooleanField(Metadata metadata, Object key, Consumer<Boolean> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        
        if (value != null && !value.trim().isEmpty()) {
            String trimmedValue = value.trim().toLowerCase();
            boolean boolValue = "true".equals(trimmedValue) || "yes".equals(trimmedValue) || "1".equals(trimmedValue);
            setter.accept(boolValue);
            mappedFields.add(keyStr);
            LOG.tracef("Mapped boolean field: %s = %b (from '%s')", keyStr, boolValue, value);
        }
    }

    /**
     * Maps a boolean field and also sets a raw string fallback via the provided rawSetter.
     * Always sets the raw value if present; sets the boolean based on common truthy strings.
     */
    public static void mapBooleanFieldWithRaw(
            Metadata metadata,
            Object key,
            Consumer<Boolean> setter,
            Consumer<String> rawSetter,
            Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String value = metadata.get(keyStr);
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        String trimmed = value.trim();
        String lowered = trimmed.toLowerCase();
        boolean boolValue = "true".equals(lowered) || "yes".equals(lowered) || "1".equals(lowered);
        setter.accept(boolValue);
        rawSetter.accept(trimmed);
        mappedFields.add(keyStr);
        LOG.tracef("Mapped boolean field (with raw): %s = %b; raw='%s'", keyStr, boolValue, trimmed);
    }
    
    /**
     * Maps a timestamp field from Tika metadata to protobuf builder.
     */
    public static void mapTimestampField(Metadata metadata, Object key, Consumer<Timestamp> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        
        // Try to get as Date first (preferred)
        Date dateValue = null;
        try {
            if (key instanceof Property) {
                dateValue = metadata.getDate((Property) key);
            } else {
                // For string keys, try to parse the string value as date
                String stringValue = metadata.get(keyStr);
                if (stringValue != null && !stringValue.trim().isEmpty()) {
                    // Tika usually stores dates in ISO format or as milliseconds
                    try {
                        long millis = Long.parseLong(stringValue.trim());
                        dateValue = new Date(millis);
                    } catch (NumberFormatException e) {
                        // Try parsing as ISO date string
                        try {
                            Instant instant = Instant.parse(stringValue.trim());
                            dateValue = Date.from(instant);
                        } catch (Exception parseException) {
                            LOG.warnf("Failed to parse date value for field %s: %s", keyStr, stringValue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to extract date for field %s: %s", keyStr, e.getMessage());
        }
        
        if (dateValue != null) {
            Instant instant = dateValue.toInstant();
            Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
            setter.accept(timestamp);
            mappedFields.add(keyStr);
            LOG.tracef("Mapped timestamp field: %s = %s", keyStr, instant);
        }
    }

    /**
     * Maps a timestamp field with a raw fallback. If parsing to timestamp fails, sets the raw string via rawSetter.
     */
    public static void mapTimestampFieldWithRaw(
            Metadata metadata,
            Object key,
            Consumer<Timestamp> setter,
            Consumer<String> rawSetter,
            Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String original = metadata.get(keyStr);
        if (original == null || original.trim().isEmpty()) {
            return;
        }
        // First try the standard timestamp mapping path
        Date dateValue = null;
        try {
            if (key instanceof Property) {
                dateValue = metadata.getDate((Property) key);
            }
        } catch (Exception e) {
            // ignore and fallback to parsing below
        }
        if (dateValue == null) {
            // Fallback: parse from string (millis or ISO-8601)
            try {
                long millis = Long.parseLong(original.trim());
                dateValue = new Date(millis);
            } catch (NumberFormatException e) {
                try {
                    Instant instant = Instant.parse(original.trim());
                    dateValue = Date.from(instant);
                } catch (Exception ignore) {
                    // parsing failed
                }
            }
        }

        if (dateValue != null) {
            Instant instant = dateValue.toInstant();
            Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(instant.getEpochSecond())
                    .setNanos(instant.getNano())
                    .build();
            setter.accept(timestamp);
            mappedFields.add(keyStr);
            LOG.tracef("Mapped timestamp field (with raw available): %s = %s", keyStr, instant);
        } else {
            rawSetter.accept(original.trim());
            mappedFields.add(keyStr);
            LOG.tracef("Mapped timestamp raw fallback: %s = '%s'", keyStr, original.trim());
        }
    }
    
    /**
     * Maps a repeated string field from Tika metadata to protobuf builder.
     */
    public static void mapRepeatedStringField(Metadata metadata, Object key, Consumer<Iterable<String>> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String[] values = metadata.getValues(keyStr);
        
        if (values != null && values.length > 0) {
            java.util.List<String> valueList = new java.util.ArrayList<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    valueList.add(value.trim());
                }
            }
            if (!valueList.isEmpty()) {
                setter.accept(valueList);
                mappedFields.add(keyStr);
                LOG.tracef("Mapped repeated string field: %s = %s", keyStr, valueList);
            }
        }
    }
    
    /**
     * Maps a repeated integer field from Tika metadata to protobuf builder.
     */
    public static void mapRepeatedIntField(Metadata metadata, Object key, Consumer<Iterable<Integer>> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String[] values = metadata.getValues(keyStr);
        
        if (values != null && values.length > 0) {
            java.util.List<Integer> valueList = new java.util.ArrayList<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    try {
                        int intValue = Integer.parseInt(value.trim());
                        valueList.add(intValue);
                    } catch (NumberFormatException e) {
                        LOG.warnf("Failed to parse integer value in repeated field %s: %s", keyStr, value);
                    }
                }
            }
            if (!valueList.isEmpty()) {
                setter.accept(valueList);
                mappedFields.add(keyStr);
                LOG.tracef("Mapped repeated int field: %s = %s", keyStr, valueList);
            }
        }
    }
    
    /**
     * Maps a repeated double field from Tika metadata to protobuf builder.
     */
    public static void mapRepeatedDoubleField(Metadata metadata, Object key, Consumer<Iterable<Double>> setter, Set<String> mappedFields) {
        String keyStr = getKeyString(key);
        String[] values = metadata.getValues(keyStr);
        
        if (values != null && values.length > 0) {
            java.util.List<Double> valueList = new java.util.ArrayList<>();
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    try {
                        double doubleValue = Double.parseDouble(value.trim());
                        valueList.add(doubleValue);
                    } catch (NumberFormatException e) {
                        LOG.warnf("Failed to parse double value in repeated field %s: %s", keyStr, value);
                    }
                }
            }
            if (!valueList.isEmpty()) {
                setter.accept(valueList);
                mappedFields.add(keyStr);
                LOG.tracef("Mapped repeated double field: %s = %s", keyStr, valueList);
            }
        }
    }
    
    /**
     * Builds a Struct containing all unmapped metadata fields.
     * 
     * @param metadata Tika metadata object
     * @param mappedFields Set of fields that were already mapped to strongly-typed fields
     * @return Struct containing unmapped metadata as key-value pairs
     */
    public static Struct buildAdditionalMetadata(Metadata metadata, Set<String> mappedFields) {
        Struct.Builder structBuilder = Struct.newBuilder();
        
        String[] allFields = metadata.names();
        int unmappedCount = 0;
        
        for (String field : allFields) {
            if (!mappedFields.contains(field)) {
                String[] values = metadata.getValues(field);
                if (values != null && values.length > 0) {
                    if (values.length == 1) {
                        // Single value
                        String value = values[0];
                        if (value != null && !value.trim().isEmpty()) {
                            structBuilder.putFields(field, Value.newBuilder().setStringValue(value.trim()).build());
                            unmappedCount++;
                        }
                    } else {
                        // Multiple values - create a list
                        Value.Builder listBuilder = Value.newBuilder();
                        com.google.protobuf.ListValue.Builder listValueBuilder = com.google.protobuf.ListValue.newBuilder();
                        
                        for (String value : values) {
                            if (value != null && !value.trim().isEmpty()) {
                                listValueBuilder.addValues(Value.newBuilder().setStringValue(value.trim()).build());
                            }
                        }
                        
                        if (listValueBuilder.getValuesCount() > 0) {
                            listBuilder.setListValue(listValueBuilder.build());
                            structBuilder.putFields(field, listBuilder.build());
                            unmappedCount++;
                        }
                    }
                }
            }
        }
        
        LOG.debugf("Built additional metadata struct with %d unmapped fields out of %d total fields", 
                  unmappedCount, allFields.length);
        
        return structBuilder.build();
    }
    
    /**
     * Builds TikaBaseFields with common parsing metadata.
     * 
     * @param parserClass Tika parser class name
     * @param tikaVersion Tika version
     * @param metadata Original Tika metadata for additional info
     * @return TikaBaseFields with parsing metadata
     */
    public static TikaBaseFields buildBaseFields(String parserClass, String tikaVersion, Metadata metadata) {
        TikaBaseFields.Builder builder = TikaBaseFields.newBuilder();
        
        // Build raw metadata struct (all fields)
        Struct.Builder rawMetadataBuilder = Struct.newBuilder();
        String[] allFields = metadata.names();
        
        for (String field : allFields) {
            String[] values = metadata.getValues(field);
            if (values != null && values.length > 0) {
                if (values.length == 1) {
                    String value = values[0];
                    if (value != null) {
                        rawMetadataBuilder.putFields(field, Value.newBuilder().setStringValue(value).build());
                    }
                } else {
                    // Multiple values
                    com.google.protobuf.ListValue.Builder listBuilder = com.google.protobuf.ListValue.newBuilder();
                    for (String value : values) {
                        if (value != null) {
                            listBuilder.addValues(Value.newBuilder().setStringValue(value).build());
                        }
                    }
                    rawMetadataBuilder.putFields(field, Value.newBuilder().setListValue(listBuilder.build()).build());
                }
            }
        }
        
        builder.setRawMetadata(rawMetadataBuilder.build());
        
        // Set parser information
        if (parserClass != null && !parserClass.isEmpty()) {
            builder.setParserClass(parserClass);
        }
        
        if (tikaVersion != null && !tikaVersion.isEmpty()) {
            builder.setTikaVersion(tikaVersion);
        }
        
        // Set parse timestamp
        builder.setParseTimestamp(Instant.now().toString());
        
        // Add any parsing warnings (if present in metadata)
        String[] warnings = metadata.getValues("tika:parsing-warning");
        if (warnings != null && warnings.length > 0) {
            for (String warning : warnings) {
                if (warning != null && !warning.trim().isEmpty()) {
                    builder.addParseWarnings(warning.trim());
                }
            }
        }
        
        LOG.debugf("Built base fields with %d raw metadata fields, parser: %s, version: %s", 
                  allFields.length, parserClass, tikaVersion);
        
        return builder.build();
    }
    
    /**
     * Converts a key (String or Property) to string representation.
     */
    private static String getKeyString(Object key) {
        if (key instanceof Property) {
            return ((Property) key).getName();
        } else if (key instanceof String) {
            return (String) key;
        } else {
            return key.toString();
        }
    }
    
    /**
     * Gets the current Tika version.
     */
    public static String getTikaVersion() {
        try {
            // Try to get Tika version from package info
            Package tikaPackage = org.apache.tika.Tika.class.getPackage();
            if (tikaPackage != null && tikaPackage.getImplementationVersion() != null) {
                return tikaPackage.getImplementationVersion();
            }
        } catch (Exception e) {
            LOG.debug("Could not determine Tika version", e);
        }
        return "unknown";
    }

    /**
     * Attempts to retrieve raw document bytes that may have been injected into the metadata
     * under a special base64-encoded key by the caller. Returns null if not present or on error.
     */
    public static byte[] tryGetRawBytes(Metadata metadata) {
        try {
            String b64 = metadata.get("pipe:raw-bytes-b64");
            if (b64 == null || b64.isEmpty()) {
                return null;
            }
            // Soft limit: if extremely large, skip to avoid memory blowups
            if (b64.length() > 16_000_000) { // ~12MB base64
                LOG.debug("Skipping raw-bytes decode due to size limit");
                return null;
            }
            return Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            LOG.debug("Failed to decode raw-bytes from metadata", e);
            return null;
        }
    }
}
