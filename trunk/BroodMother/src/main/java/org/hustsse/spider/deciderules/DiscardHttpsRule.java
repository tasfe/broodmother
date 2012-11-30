package org.hustsse.spider.deciderules;

import org.hustsse.spider.model.CrawlURL;

public class DiscardHttpsRule implements DecideRule {

	@Override
	public DecideResult test(CrawlURL url) {
		if(url.getURL().getProtocol().toLowerCase().equals("https"))
			return DecideResult.REJECT;
//		if(!url.getURL().getHost().toLowerCase().equals("www.douban.com"))
//			return DecideResult.REJECT;
		return DecideResult.PASS;
	}

}
