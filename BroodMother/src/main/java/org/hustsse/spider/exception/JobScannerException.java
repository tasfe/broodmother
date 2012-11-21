package org.hustsse.spider.exception;

public class JobScannerException extends RuntimeException {

	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public JobScannerException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public JobScannerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public JobScannerException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public JobScannerException(Throwable cause) {
        super(cause);
    }
}
