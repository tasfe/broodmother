package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.deciderules.DecideResult;
import org.hustsse.spider.deciderules.DecideRule;
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.handler.AbstractBeanNameAwareHandler;
import org.hustsse.spider.model.CrawlURL;

public class CandidateJudge  extends AbstractBeanNameAwareHandler {

	private DecideRule rule;

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {
		// 使用rule判定url通过或拒绝。
		if(rule.test(url) == DecideResult.PASS) {
			url.setAllowed(true);
			ctx.proceed();
		}

		// 如果判定失败做点什么？需要记录吗？
	}

	public DecideRule getRule() {
		return rule;
	}

	public void setRule(DecideRule rule) {
		this.rule = rule;
	}
}
