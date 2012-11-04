package org.hustsse.spider.exception;

public class PipelineException extends RuntimeException {

	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public PipelineException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public PipelineException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public PipelineException(Throwable cause) {
        super(cause);
    }
}
