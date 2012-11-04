package org.hustsse.spider.exception;

public class BossException extends RuntimeException {
	private static final long serialVersionUID = -2819159425867539698L;

	/**
     * Creates a new exception.
     */
    public BossException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public BossException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public BossException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public BossException(Throwable cause) {
        super(cause);
    }

}
