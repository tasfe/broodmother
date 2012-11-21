package org.hustsse.spider.exception;

public class CrawlJobException extends RuntimeException {

	private static final long serialVersionUID = -2819159425867539607L;

	/**
     * Creates a new exception.
     */
    public CrawlJobException() {
        super();
    }

    /**
     * Creates a new exception.
     */
    public CrawlJobException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public CrawlJobException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public CrawlJobException(Throwable cause) {
        super(cause);
    }
}
