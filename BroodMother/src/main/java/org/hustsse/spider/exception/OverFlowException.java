package org.hustsse.spider.exception;

public class OverFlowException extends RuntimeException {
	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public OverFlowException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public OverFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public OverFlowException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public OverFlowException(Throwable cause) {
        super(cause);
    }

}
