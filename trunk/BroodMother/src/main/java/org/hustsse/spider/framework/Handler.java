package org.hustsse.spider.framework;

import org.hustsse.spider.model.CrawlURL;

public interface Handler {
	String getName();
	void process(HandlerContext ctx,CrawlURL url);
}
