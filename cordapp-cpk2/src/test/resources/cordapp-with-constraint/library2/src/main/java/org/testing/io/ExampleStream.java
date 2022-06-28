package org.testing.io;

import org.apache.commons.io.input.AutoCloseInputStream;
import java.io.InputStream;

public class ExampleStream extends AutoCloseInputStream {
    public ExampleStream(InputStream input) {
        super(input);
    }
}
