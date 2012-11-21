package org.hustsse.spider.deciderules;

public enum DecideResult {
	/** The URL is successed in passing the rule, and will be decided by the next rule,if any. */
	PASS,
	/** The URL failed passing the rule. */
	REJECT;
}
