package org.hustsse.spider.sink;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.framework.Frontier;
import org.hustsse.spider.framework.PipelineSink;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * “Crawl Pipeline”的默认的PipelineSink实现，待爬取的URL经过Crawl Pipeline中的Handler处理后
 * 将进入这里做最终的处理。
 *
 * @author Anderson
 *
 */
public class DefaultCrawlPipelineSink implements PipelineSink {

	Logger logger = LoggerFactory.getLogger(DefaultCrawlPipelineSink.class.getName());

	@Autowired
	Frontier frontier;

	@Override
	public void uriSunk(CrawlURL url) {
		// 如果url需要重试且没有超过最大重试次数，将其重新Schedule进Frontier
		if (url.isNeedRetry()) {
			if (url.getRetryTimes() >= CrawlURL.DEFAULT_RETRY_TIMES) {
				logger.debug("抓取失败，url：{}", url);
				return;
			}

			// retry
			url.incRetryTimes();
			try {
				frontier.schedule(url);
			} catch (RuntimeException e) {
				logger.error("schedule failed when retry,url:" + url, e);
			}
		}
	}

	@Override
	public void exceptionCaught(CrawlURL e, PipelineException cause) {
		logger.error("Unexpected exception in the Crawl Pipeline !:" + e, cause);
	}
}
