package org.hustsse.spider.exception;

public class CrawlControllerException extends RuntimeException {

	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public CrawlControllerException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public CrawlControllerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public CrawlControllerException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public CrawlControllerException(Throwable cause) {
        super(cause);
    }
}
