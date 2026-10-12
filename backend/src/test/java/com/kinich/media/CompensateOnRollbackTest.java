package com.kinich.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.kinich.media.storage.MediaStorage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

class CompensateOnRollbackTest {

    private final List<String> deleted = new ArrayList<>();
    private final MediaStorage storage = new MediaStorage() {
        @Override public String store(byte[] content) { throw new UnsupportedOperationException(); }
        @Override public InputStream open(String storageKey) { throw new UnsupportedOperationException(); }
        @Override public void delete(String storageKey) { deleted.add(storageKey); }
    };

    @Test
    void deletesTheFileOnlyWhenTheTransactionRolledBack() {
        new CompensateOnRollback(storage, "k1").afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        new CompensateOnRollback(storage, "k2").afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        new CompensateOnRollback(storage, "k3").afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        assertThat(deleted).containsExactly("k1"); // UNKNOWN conserva el archivo: el commit pudo confirmarse
    }
}
