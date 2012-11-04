package org.hustsse.spider.exception;

public class URIException extends RuntimeException {

	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public URIException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public URIException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public URIException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public URIException(Throwable cause) {
        super(cause);
    }
}
