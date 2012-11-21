package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.framework.Handler;
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.model.CrawlURL;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 2 role:
 *
 * a) prepare the candidate before schedule it into the frontier;
 * b) prepare the seed
 *
 * @author Administrator
 *
 */
public class FrontierPreparer implements Handler {

	@Autowired
	QueueAssignmentPolicy queueAssignmentPolicy;

	@Autowired
	UrlPriorityPolicy urlPriorityPolicy;

	@Autowired
	CanonicalPolicy canonicalPolicy;

	public FrontierPreparer() {
		urlPriorityPolicy = new DefaultUrlPriorityPolicy();
		queueAssignmentPolicy = new DefaultQueueAssignmentPolicy();
		canonicalPolicy = new DefaultCanonicalPolicy();
	}

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {

		url.setWorkQueueKey(getWorkQueueKeyFor(url));

		url.setPriority(getPriorityFor(url));

		url.setCanonicalStr(getCanonicalStrFor(url));

		ctx.proceed();
	}

	private String getCanonicalStrFor(CrawlURL url) {
		return canonicalPolicy.getCanonicalStrFor(url);
	}

	private int getPriorityFor(CrawlURL url) {
		return urlPriorityPolicy.getPriorityFor(url);
	}

	private String getWorkQueueKeyFor(CrawlURL url) {
		return queueAssignmentPolicy.getWorkQueueKeyFor(url);
	}

}
