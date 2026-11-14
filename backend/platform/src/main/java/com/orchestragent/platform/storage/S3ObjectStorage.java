package com.orchestragent.platform.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ObjectStorage} on any S3-compatible store (RustFS in development), through the MinIO Java SDK, which is a
 * plain S3 client. The bucket is created on the first write, not at startup, so the application starts without the
 * store and only the features that need it fail.
 */
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    /** S3 error codes that mean "nothing there", answered as an empty result instead of an error. */
    private static final Set<String> MISSING = Set.of("NoSuchKey", "NoSuchBucket", "NoSuchObject");

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public S3ObjectStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public static S3ObjectStorage from(StorageProperties properties) {
        MinioClient.Builder builder = MinioClient.builder().endpoint(properties.endpoint());
        if (!properties.accessKey().isBlank()) {
            builder.credentials(properties.accessKey(), properties.secretKey());
        }
        return new S3ObjectStorage(builder.build(), properties.bucket());
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .data(content, content.length)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | RuntimeException e) {
            throw unavailable("store", e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try (GetObjectResponse response = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            String contentType = response.headers().get("Content-Type");
            return Optional.of(new StoredObject(response.readAllBytes(), contentType));
        } catch (ErrorResponseException e) {
            if (MISSING.contains(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw unavailable("read", e);
        } catch (MinioException | IOException | RuntimeException e) {
            throw unavailable("read", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException e) {
            if (!MISSING.contains(e.errorResponse().code())) {
                throw unavailable("delete", e);
            }
        } catch (MinioException | RuntimeException e) {
            throw unavailable("delete", e);
        }
    }

    private void ensureBucket() throws MinioException {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Created storage bucket {}", bucket);
                } catch (ErrorResponseException e) {
                    // Another instance created it in the meantime.
                    if (!"BucketAlreadyOwnedByYou".equals(e.errorResponse().code())) {
                        throw e;
                    }
                }
            }
            bucketReady = true;
        }
    }

    private StorageUnavailableException unavailable(String action, Exception cause) {
        // The cause names the endpoint and bucket, never credentials.
        log.warn("Object storage could not {} an object: {}", action, cause.toString());
        return new StorageUnavailableException("Object storage is not available", cause);
    }
}
