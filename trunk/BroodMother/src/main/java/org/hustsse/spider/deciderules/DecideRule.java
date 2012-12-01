package org.hustsse.spider.deciderules;

import org.hustsse.spider.model.CrawlURL;

/**
 * test a CrawlURL
 *
 * @author Anderson
 *
 */
public interface DecideRule {
	DecideResult test(CrawlURL url);
}
