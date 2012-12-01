package org.hustsse.spider.framework;

import org.hustsse.spider.model.CrawlController;


public interface HandlerContext {

	void proceed();

	void pause();

	// void pauseAndSkip();

	/**
	 * go to the sink
	 */
	void finish();

	Object getAttachment();

	void setAttachment(Object attachment);

	Handler getHandler();

	void jumpTo(String handlerName);

	CrawlController getController();

}
