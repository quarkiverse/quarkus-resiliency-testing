package io.quarkiverse.resiliencytesting.it.cxf;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.cxf.annotation.CXFClient;

@ApplicationScoped
public class SoapCaller {

    @CXFClient
    SoapService paymentsClient;

    @CXFClient("orders")
    SoapService ordersClient;

    @CXFClient("receipts")
    SoapService receiptsClient;

    @CXFClient("default-endpoint")
    SoapService defaultEndpointClient;

    @CXFClient("inferred-interface")
    SoapService inferredInterfaceClient;

    @CXFClient("inferred-interface-default-endpoint")
    SoapService inferredInterfaceDefaultEndpointClient;

    @CXFClient
    SoapService noNameClient;

    public String callPayments(String name) {
        return paymentsClient.hello(name);
    }

    public String callOrders(String name) {
        return ordersClient.hello(name);
    }

    public String callReceipts(String name) {
        return receiptsClient.hello(name);
    }

    public String callFallback(String name) {
        return defaultEndpointClient.hello(name);
    }

    public String callInferred(String name) {
        return inferredInterfaceClient.hello(name);
    }

    public String callInterface(String name) {
        return inferredInterfaceDefaultEndpointClient.hello(name);
    }

    public String callNoName(String name) {
        return noNameClient.hello(name);
    }
}
