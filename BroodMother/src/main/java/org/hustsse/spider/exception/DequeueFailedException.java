package org.hustsse.spider.exception;

public class DequeueFailedException extends RuntimeException {
	private static final long serialVersionUID = -2819159425867539607L;

	/**
	 * Creates a new exception.
	 */
	public DequeueFailedException() {
		super();
	}

	/**
	 * Creates a new exception.
	 */
	public DequeueFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates a new exception.
	 */
	public DequeueFailedException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception.
	 */
	public DequeueFailedException(Throwable cause) {
		super(cause);
	}
}
