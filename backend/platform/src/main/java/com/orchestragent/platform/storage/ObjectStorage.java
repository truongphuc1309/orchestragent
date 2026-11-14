package com.orchestragent.platform.storage;

import java.util.Optional;

/**
 * A key-value store for files. Keys start with the name of the context that owns them ({@code identity/...}), so
 * a context that moves to its own service can take its objects with it. Every method throws
 * {@link StorageUnavailableException} when the store cannot be reached.
 */
public interface ObjectStorage {

    /** Stores the object, replacing any object with the same key. */
    void put(String key, byte[] content, String contentType);

    /** Empty when there is no object with this key. */
    Optional<StoredObject> get(String key);

    /** Deleting a key that does not exist is not an error. */
    void delete(String key);

    record StoredObject(byte[] content, String contentType) {
    }
}
