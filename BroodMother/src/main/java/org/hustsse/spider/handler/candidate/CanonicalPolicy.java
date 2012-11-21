package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.model.CrawlURL;

public interface CanonicalPolicy {

	String getCanonicalStrFor(CrawlURL url);

}
