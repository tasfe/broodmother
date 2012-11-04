package org.hustsse.spider.handler;

public interface HandlerContext {

	void proceed();

	// void jumpTo(String handlerName);

	void pause();

	// void pauseAndSkip();

	void toSink();

	Object getAttachment();

	void setAttachment(Object attachment);

	Handler getHandler();

}
