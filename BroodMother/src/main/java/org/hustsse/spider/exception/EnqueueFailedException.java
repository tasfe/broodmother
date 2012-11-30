package org.hustsse.spider.exception;

public class EnqueueFailedException extends RuntimeException {
	private static final long serialVersionUID = -2819159425867539607L;

	/**
	 * Creates a new exception.
	 */
	public EnqueueFailedException() {
		super();
	}

	/**
	 * Creates a new exception.
	 */
	public EnqueueFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates a new exception.
	 */
	public EnqueueFailedException(String message) {
		super(message);
	}

	/**
	 * Creates a new exception.
	 */
	public EnqueueFailedException(Throwable cause) {
		super(cause);
	}
}
