package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.model.CrawlURL;

/**
 * URL的优先级策略，优先级影响到CrawlURL在WorkQueue中出队的顺序。
 *
 * @author Anderson
 *
 */
public interface UrlPriorityPolicy {

	/**
	 * 计算给定CrawlURL的优先级。
	 * @param url
	 * @return
	 */
	int getPriorityFor(CrawlURL url);

}
