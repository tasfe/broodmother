package org.hustsse.spider.framework;

import org.hustsse.spider.model.CrawlURL;

/**
 * Pipeline中的处理器，对CrawlURL进行实际的处理。
 *
 * @author Anderson
 *
 */
public interface Handler {
	/**
	 * 得到Handler的名称
	 *
	 * @return handler的名称
	 */
	String getName();

	/**
	 * 处理CrawlURL
	 *
	 * @param ctx
	 * @param url
	 */
	void process(HandlerContext ctx, CrawlURL url);
}
