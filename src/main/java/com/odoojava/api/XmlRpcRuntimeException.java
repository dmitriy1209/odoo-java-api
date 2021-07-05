package com.odoojava.api;

public class XmlRpcRuntimeException extends RuntimeException{
    
    private static final long serialVersionUID = 1228901864529073799L;

    public XmlRpcRuntimeException(Throwable cause) {
        super(cause);
    }

    public XmlRpcRuntimeException(String message) {
        super(message);
    }
    
}