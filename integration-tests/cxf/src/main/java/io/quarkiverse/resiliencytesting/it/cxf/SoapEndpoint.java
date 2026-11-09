package io.quarkiverse.resiliencytesting.it.cxf;

import jakarta.jws.WebService;

@WebService(endpointInterface = "io.quarkiverse.resiliencytesting.it.cxf.SoapService")
public class SoapEndpoint implements SoapService {

    @Override
    public String hello(String name) {
        return "Hello " + name;
    }
}
