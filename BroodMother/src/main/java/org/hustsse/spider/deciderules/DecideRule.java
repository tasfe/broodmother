package org.hustsse.spider.deciderules;

import org.hustsse.spider.model.CrawlURL;

public interface DecideRule {
	DecideResult test(CrawlURL url);
}
