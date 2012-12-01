package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.model.CrawlURL;

/**
 * 默认的url分配策略，workQueue按照host组织起来。Per host 1 workQueue， key为[host:port]形式。
 *
 * @author Anderson
 *
 */
public class DefaultQueueAssignmentPolicy implements QueueAssignmentPolicy {

	@Override
	public String getWorkQueueKeyFor(CrawlURL url) {
		return url.getURL().getHost() + ":" + url.getURL().getPort();
	}

}
