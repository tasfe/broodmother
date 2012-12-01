package org.hustsse.spider.handler.candidate;

import java.util.List;

import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.framework.Pipeline;
import org.hustsse.spider.handler.AbstractBeanNameAwareHandler;
import org.hustsse.spider.model.CrawlURL;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * CrawlURL抽取链接完毕后，使用CandidateHandler对所有candidate
 * url进行处理。CandidateHandler为每个Candidate创建一个Pipeline并启动之，我们称之为“Candidate
 * Pipeline”。
 *
 * @author Anderson
 *
 */
public class CandidateHandler extends AbstractBeanNameAwareHandler implements ApplicationContextAware {
	private ApplicationContext appContext;

	/**
	 * Pipeline for candidate url. Keep the bean id to use the "prototype"
	 * feature。
	 */
	private String candidatePipelineBeanId;

	public String getCandidatePipelineBeanId() {
		return candidatePipelineBeanId;
	}

	public void setCandidatePipelineBeanId(String candidatePipelineBeanId) {
		this.candidatePipelineBeanId = candidatePipelineBeanId;
	}

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {
		List<CrawlURL> candidates = url.getCandidates();
		if (candidates == null)
			return;

		// error page, clear & return.
		if (url.getResponseStatusCode() < 200 || url.getResponseStatusCode() >= 400) {
			candidates.clear();
			return;
		}

		// run the candidate pipeline. The pipeline is responsible to
		// decide which url to reject or accept,and to schedule it to
		// the frontier.
		for (CrawlURL candidate : candidates) {
			Pipeline candidatePipeline = getCandidatePipeline();
			candidatePipeline.attachTo(candidate);
			candidatePipeline.start();
		}
		candidates.clear();
	}

	private Pipeline getCandidatePipeline() {
		return (Pipeline) appContext.getBean(candidatePipelineBeanId);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.appContext = applicationContext;
	}
}
