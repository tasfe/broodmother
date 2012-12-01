package org.hustsse.spider.deciderules;

import java.util.List;

import org.hustsse.spider.model.CrawlURL;

/**
 * 使用一个DecideRule序列判断一个url是否通过。当且仅当序列中所有Rule对url test 的结果都为
 * {@link DecideResult#PASS}时才允许其通过，只要有一个Rule的结果是{@link DecideResult#REJECT}
 * ，DecideRuleSequence 即会将之拒绝。
 *
 * @author Administrator
 *
 */
public class DecideRuleSequence implements DecideRule {

	private List<DecideRule> rules;

	@Override
	public DecideResult test(CrawlURL url) {
		assert rules != null;
		for (DecideRule rule : rules) {
			if (rule.test(url) == DecideResult.REJECT)
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
