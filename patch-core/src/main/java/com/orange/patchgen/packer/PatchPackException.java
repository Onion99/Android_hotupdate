package com.orange.patchgen.packer;

/**
 * 补丁打包异常
 * 
 * Requirements: 4.1-4.6
 */
public class PatchPackException extends Exception {
    
    public PatchPackException(String message) {
        super(message);
    }

    public PatchPackException(String message, Throwable cause) {
        super(message, cause);
    }
}
