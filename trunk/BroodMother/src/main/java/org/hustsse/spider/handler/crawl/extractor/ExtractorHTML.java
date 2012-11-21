package org.hustsse.spider.handler.crawl.extractor;

import org.hustsse.spider.exception.URLException;
import org.hustsse.spider.framework.Handler;
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.model.URL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractorHTML implements Handler {
	Logger logger = LoggerFactory.getLogger(ExtractorHTML.class);

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {
		CrawlURL crawlUrl = (CrawlURL) url;

		// relative url到absolute url的转换，有两种方式，一种是用jsoup，"abs:href"；另一种用URL类的构造器，指定baseURL。这里用后一种
		Document doc = Jsoup.parse(crawlUrl.getResponse().getContentStr());

		// 查找base标签，设定derelative url使用的base url
		Elements base = doc.select("base");
		if(base != null && base.size()>0) {
			try {
				URL baseURL = new URL(base.first().attr("href"));
				crawlUrl.setBaseURL(baseURL);
			}catch (URLException e) {
				logger.info("定义错误的base标签："+base.first().html()+"，将使用自身继续相对路径的derelative。");
			}
		}

		//抽取子链接，这里仅抽取iframe、frame、a这三种标签
		Elements frames = doc.select("iframe frame");
		for (Element frame : frames) {
			String src = frame.attr("src");
			try {
				URL outlink = new URL(crawlUrl.getBaseURL(), src);
				//TODO create candidate pipeline
				CrawlURL candidateURL = new CrawlURL(outlink);
				crawlUrl.addCandidate(candidateURL);
			}catch(URLException e) {
				logger.info("frame/iframe标签src发现不支持的协议："+ src);
			}
		}

		Elements links = doc.select("a");
		for (Element link : links) {
			String href = link.attr("href");
			try {
				URL outlink = new URL(crawlUrl.getBaseURL(), href);
				//TODO create candidate pipeline
				CrawlURL candidateURL = new CrawlURL(outlink);
				crawlUrl.addCandidate(candidateURL);
			}catch(URLException e) {
				logger.info("a标签href发现不支持的协议："+ href);
			}
		}

		ctx.proceed();
	}
}
