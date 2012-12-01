package org.hustsse.spider.handler.crawl.extractor;

import java.net.MalformedURLException;

import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.handler.AbstractBeanNameAwareHandler;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.util.httpcodec.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从http响应header中抽取链接。目前只抽取Location，处理重定向的情况。重定向时抽取完毕后直接跳入CandidateHandler中，
 * 不解析http response body和写出。
 *
 * @author Anderson
 *
 */
public class ExtractorHttp extends AbstractBeanNameAwareHandler {
	Logger logger = LoggerFactory.getLogger(ExtractorHttp.class);

	/** candidate handler to jump when broken location header found */
	String candidateHandlerName;

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {
		// location
		String loc = url.getResponse().getHeader(HttpHeaders.Names.LOCATION);
		// no location, proceed the pipeline
		if (loc == null) {
			ctx.proceed();
			return;
		}

		try {
			// location found, 跳过ExtracxtorHttp等后续，直接进入candidate handler
			addCandidateFromHeader(url, loc);
			ctx.jumpTo(candidateHandlerName);
		} catch (MalformedURLException e) {
			// location found but broken，视该URL为broken的，停止继续处理
			url.setNeedRetry(false);
			ctx.finish();
		}

	}

	private void addCandidateFromHeader(CrawlURL url, String urlInHeader) throws MalformedURLException {
		CrawlURL candidate = new CrawlURL(urlInHeader, url.getURL());
		// 从种子重定向来的，也认为是种子
		candidate.setSeed(url.isSeed());
		url.addCandidate(candidate);
	}

	public String getCandidateHandlerName() {
		return candidateHandlerName;
	}

	public void setCandidateHandlerName(String candidateHandlerName) {
		this.candidateHandlerName = candidateHandlerName;
	}
}
