package org.hustsse.spider.exception;

public class URLException extends RuntimeException {

	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public URLException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public URLException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public URLException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public URLException(Throwable cause) {
        super(cause);
    }
}
