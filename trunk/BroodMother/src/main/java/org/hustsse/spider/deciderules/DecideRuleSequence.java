package org.hustsse.spider.deciderules;

import java.util.List;

import org.hustsse.spider.model.CrawlURL;

public class DecideRuleSequence implements DecideRule{

	private List<DecideRule> rules;

	@Override
	public DecideResult test(CrawlURL url) {
		assert rules != null;
		for (DecideRule rule : rules) {
			if(rule.test(url) == DecideResult.REJECT)
				return DecideResult.REJECT;
		}
		return DecideResult.PASS;
	}

	public List<DecideRule> getRules() {
		return rules;
	}

	public void setRules(List<DecideRule> rules) {
		this.rules = rules;
	}
}
