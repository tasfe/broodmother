package org.hustsse.spider.sink;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.framework.Frontier;
import org.hustsse.spider.framework.PipelineSink;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DefaultCandidatePipelineSink implements PipelineSink{

	Logger logger  = LoggerFactory.getLogger(DefaultCandidatePipelineSink.class.getName());

	@Autowired
	Frontier frontier;

	@Override
	public void uriSunk(CrawlURL e) {
		if(Boolean.TRUE.equals(e.isPassedRules())) {
			frontier.schedule(e);
		}
	}

	@Override
	public void exceptionCaught(CrawlURL e, PipelineException cause) {
		logger.error("Error processing the candidate url:"+e,cause);
	}

}
