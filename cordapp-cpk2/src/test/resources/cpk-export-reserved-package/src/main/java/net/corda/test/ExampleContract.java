package net.corda.test;

import net.corda.v5.ledger.contracts.Contract;
import net.corda.v5.ledger.transactions.LedgerTransaction;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ExampleContract implements Contract {
    @Override
    public void verify(@NotNull LedgerTransaction ltx) {
        try (InputStream input = new ByteArrayInputStream("Hello Corda!".getBytes(UTF_8))) {
            IOUtils.copy(input, System.out);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }
}
