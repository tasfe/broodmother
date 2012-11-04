package org.hustsse.spider.model;

import org.hustsse.spider.handler.crawl.fetcher.httpcodec.HttpResponse;
import org.hustsse.spider.pipeline.Pipeline;

public class CrawlURL extends URL{

	private HttpResponse response;

	public CrawlURL(String url, Pipeline pipeline) {
		super(url, pipeline);
	}

	public HttpResponse getResponse() {
		return response;
	}

	public void setResponse(HttpResponse response) {
		this.response = response;
	}


}
