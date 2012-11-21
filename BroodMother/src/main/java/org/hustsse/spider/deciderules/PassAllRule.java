package org.hustsse.spider.deciderules;

import org.hustsse.spider.model.CrawlURL;

public class PassAllRule implements DecideRule {

	@Override
	public DecideResult test(CrawlURL url) {
		return DecideResult.PASS;
	}

}
