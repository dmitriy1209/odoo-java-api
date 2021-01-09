package com.odoojava.api;

import java.util.Arrays;

@lombok.Getter
public class Response {
    
    private final boolean isSuccessful;
    private final Exception errorCause;
    private final Object responseObject;
    private final Object[] responseObjectAsArray;
    
    public Response(final Exception errorCause) {
        this.isSuccessful = false;
        this.errorCause = errorCause;
        this.responseObject = null;
        this.responseObjectAsArray = new Object[0];
    }
    
    public Response(final Object responseObject) {
        this.isSuccessful = true;
        this.errorCause = null;
        this.responseObject = responseObject;
        this.responseObjectAsArray = responseObject instanceof Object[] ? 
                (Object[]) responseObject : new Object[]{responseObject};
    }

    public Object[] getResponseObjectAsArray() {
        return Arrays.copyOf(responseObjectAsArray, responseObjectAsArray.length);
    }

//    @Override
//    public String toString() {
//        return "Response{" + "isSuccessful=" + isSuccessful + ", errorCause=" + errorCause + 
//                ", responseObject=" + Arrays.toString((Object[])responseObject) + 
//                ", responseObjectAsArray=" + (responseObjectAsArray==null?null:Arrays.toString(responseObjectAsArray)) + '}';
//    }
    
}
