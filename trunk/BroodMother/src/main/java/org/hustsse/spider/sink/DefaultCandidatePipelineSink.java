package org.hustsse.spider.sink;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.framework.Frontier;
import org.hustsse.spider.framework.PipelineSink;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DefaultCandidatePipelineSink implements PipelineSink {

	Logger logger = LoggerFactory.getLogger(DefaultCandidatePipelineSink.class.getName());

	@Autowired
	Frontier frontier;

	@Override
	public void uriSunk(CrawlURL url) {
		if (Boolean.TRUE.equals(url.isAllowed())) {
			try {
				frontier.schedule(url);
			} catch (RuntimeException e) {
				logger.error("schedule failed,url:" + url, e);
			}
		}
	}

	@Override
	public void exceptionCaught(CrawlURL e, PipelineException cause) {
		logger.error("Error processing the candidate url:" + e, cause);
	}

}
