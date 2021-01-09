package com.odoojava.api;

public class OdooAuthenticationException extends OdooApiException{

    private static final long serialVersionUID = 1728161620463462823L;
    
    public OdooAuthenticationException(String message) {
        super(message);
    }

    public OdooAuthenticationException(Throwable cause) {
        super(cause);
    }

    public OdooAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
