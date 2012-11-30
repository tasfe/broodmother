package org.hustsse.spider.framework;

import org.hustsse.spider.model.CrawlURL;

public interface Pipeline {

	public static Object EMPTY_MSG = new Object();

	void start();

	/**
	 *
	 * @param resumeMsg
	 *            向breakpoint传递的消息，保存在pipeline中
	 */
	void resume(Object resumeMsg);

	boolean isPaused();

	CrawlURL getURL();

	PipelineSink getSink();

	void append(PipelineSink sink);

	/**
	 * 将一个pipeline与crawlURL绑定，该方法将维护二者的双向关联。 不要使用
	 * {@link CrawlURL#setPipeline(Pipeline)}方法设置 crawlURL的pipeline。
	 *
	 * @param uri
	 */
	void attachTo(CrawlURL url);

	Object getMessage();

	void clearMessage();

	HandlerContext getHandlerContext(String handlerName);
}
