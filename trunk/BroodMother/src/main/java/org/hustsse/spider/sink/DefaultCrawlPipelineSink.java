package org.hustsse.spider.sink;

import org.hustsse.spider.exception.PipelineException;
import org.hustsse.spider.framework.PipelineSink;
import org.hustsse.spider.model.CrawlURL;

public class DefaultCrawlPipelineSink implements PipelineSink{

	@Override
	public void uriSunk(CrawlURL e) {
		//TODO 记录时间、重试？
	}

	@Override
	public void exceptionCaught(CrawlURL e, PipelineException cause) {

	}
}
