package org.hustsse.spider.sink;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.framework.Frontier;
import org.hustsse.spider.framework.PipelineSink;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * “Candidate Pipeline”的默认的PipelineSink实现，Candidate URL经过Candidate Pipeline中的Handler处理后
 * 将进入这里做最终的处理。
 * @author Anderson
 *
 */
public class DefaultCandidatePipelineSink implements PipelineSink {

	Logger logger = LoggerFactory.getLogger(DefaultCandidatePipelineSink.class.getName());

	@Autowired
	Frontier frontier;

	@Override
	public void uriSunk(CrawlURL url) {
		// 如果Candidate通过了，schedule进frontier
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
		logger.error("Unexpected exception in the crawl exception," + e, cause);
	}

}
