package org.hustsse.spider.exception;

public class ReactorException extends RuntimeException {

	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public ReactorException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public ReactorException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public ReactorException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public ReactorException(Throwable cause) {
        super(cause);
    }
}
