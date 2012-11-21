package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.model.CrawlURL;

public class DefaultUrlPriorityPolicy implements UrlPriorityPolicy {

	@Override
	public int getPriorityFor(CrawlURL url) {
		if(url.isSeed()) {
			return CrawlURL.HIGHEST;
		}
		return CrawlURL.NORMAL;
	}

}
