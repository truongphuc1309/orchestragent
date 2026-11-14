package com.orchestragent.platform.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

/** Without a reachable store every call fails with the one exception the web layer answers with 503. */
class S3ObjectStorageTest {

    @Test
    void unreachableStoreIsReportedAsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ObjectStorage storage = S3ObjectStorage.from(
                new StorageProperties("http://127.0.0.1:" + closedPort, "access", "secret", "bucket"));

        assertThatThrownBy(() -> storage.put("identity/x", new byte[] {1}, "image/png"))
                .isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> storage.get("identity/x")).isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> storage.delete("identity/x")).isInstanceOf(StorageUnavailableException.class);
    }
}
