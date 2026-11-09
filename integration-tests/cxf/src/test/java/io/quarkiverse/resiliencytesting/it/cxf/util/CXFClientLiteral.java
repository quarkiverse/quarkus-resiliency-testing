package io.quarkiverse.resiliencytesting.it.cxf.util;

import jakarta.enterprise.util.AnnotationLiteral;

import io.quarkiverse.cxf.annotation.CXFClient;

/**
 * Utility class that constructs the CXF Client key to be used across tests
 */
public final class CXFClientLiteral extends AnnotationLiteral<CXFClient> implements CXFClient {

    public final String clientName;

    public CXFClientLiteral(String clientName) {
        this.clientName = clientName;
    }

    @Override
    public String value() {
        return clientName;
    }
}
