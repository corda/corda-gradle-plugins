package com.example.library.impl;

import com.example.library.ExternalLibrary;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ExternalLibraryImpl implements ExternalLibrary {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalLibraryImpl.class);

    @Override
    public String apply(String data) {
        LOG.info("Message: {}", data);
        return data;
    }
}
