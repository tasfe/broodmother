package org.hustsse.spider.deciderules;

import org.hustsse.spider.model.CrawlURL;

/**
 * {@link DecideRule#test(CrawlURL)}对CrawlURL测试后的结果。
 *
 * @author Anderson
 *
 */
public enum DecideResult {
	/**
	 * The URL is successed in passing the rule, and will be decided by the next
	 * rule,if any.
	 */
	PASS,
	/** The URL failed passing the rule. */
	REJECT;
}
