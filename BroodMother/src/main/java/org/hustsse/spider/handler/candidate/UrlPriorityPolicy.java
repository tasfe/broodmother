package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.model.CrawlURL;

public interface UrlPriorityPolicy {

	int getPriorityFor(CrawlURL url);

}
