package org.hustsse.spider.framework;

import org.hustsse.spider.model.CrawlController;

/**
 * Handler的上下文，Handler通过HandlerContext控制逻辑在Pipeline中如何流转，
 * 并通过HandlerContext与CrawlController进行交互。
 *
 * @author Anderson
 *
 */
public interface HandlerContext {

	/**
	 * to the next handler
	 */
	void proceed();

	/**
	 * pause the pipeline, current handler will be the "breakpoint".
	 */
	void pause();

	// void pauseAndSkip();

	/**
	 * go to the sink
	 */
	void finish();

	/**
	 * get the handler
	 * @return
	 */
	Handler getHandler();

	/**
	 * jump to another handler
	 * @param handlerName
	 */
	void jumpTo(String handlerName);

	/**
	 * get the {@link CrawlController}
	 * @return CrawlController
	 */
	CrawlController getController();

}
