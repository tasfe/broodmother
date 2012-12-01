package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.model.CrawlURL;

/**
 * CrawlURL的入队策略，决定CrawlURL被调度至哪个WorkQueue。
 *
 * @author Anderson
 *
 */
public interface QueueAssignmentPolicy {

	/**
	 * 得到给定CrawlURL的WorkQueue key
	 *
	 * @param url
	 * @return workQueue key
	 */
	String getWorkQueueKeyFor(CrawlURL url);

}
