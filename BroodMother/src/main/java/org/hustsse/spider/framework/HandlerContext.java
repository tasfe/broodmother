package org.hustsse.spider.framework;


public interface HandlerContext {

	void proceed();

	// void jumpTo(String handlerName);

	void pause();

	// void pauseAndSkip();

	/**
	 * go to the sink
	 */
	void finished();

	Object getAttachment();

	void setAttachment(Object attachment);

	Handler getHandler();

}
