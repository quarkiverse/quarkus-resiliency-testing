package io.quarkiverse.resiliencytesting.it.cxf;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

@WebService
public interface SoapService {

    @WebMethod
    String hello(String name);
}
