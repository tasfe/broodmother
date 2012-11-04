package org.hustsse.spider.handler.crawl.extractor;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.hustsse.spider.handler.Handler;
import org.hustsse.spider.handler.HandlerContext;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.model.URL;
import org.hustsse.spider.util.CommonUtils;

public class ExtractorHTML implements Handler {

	@Override
	public void process(HandlerContext ctx, URL url) {
		CrawlURL crawlUrl = (CrawlURL) url;
		String c = crawlUrl.getResponse().getContentCharset();
		if(c == null)
			c = "GB2312";
		// 2. convert to string and print it
		Charset charset = Charset.forName(c);
		CharsetDecoder decoder = charset.newDecoder();
		try {
			crawlUrl.getResponse().getContent().flip();
			String s = decoder.decode(crawlUrl.getResponse().getContent()).toString();
			CommonUtils.toFile(s, "R:\\STR_"+crawlUrl.getHost()+".html");
		} catch (CharacterCodingException e) {
			e.printStackTrace();
		}finally {
			ctx.proceed();
		}
	}

}
