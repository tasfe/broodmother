package org.hustsse.spider.handler.crawl.extractor;

import java.net.MalformedURLException;

import org.hustsse.spider.framework.AbstractBeanNameAwareHandler;
import org.hustsse.spider.framework.HandlerContext;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.model.URL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 链接抽取器，从HTML文本中提取URL。
 * <p>
 * 基于JSOUP实现，目前仅抽取a、iframe、frame三种元素。
 *
 * @author Anderson
 *
 */
public class ExtractorHTML extends AbstractBeanNameAwareHandler {
	Logger logger = LoggerFactory.getLogger(ExtractorHTML.class);

	@Override
	public void process(HandlerContext ctx, CrawlURL url) {
		CrawlURL crawlUrl = (CrawlURL) url;

		// relative url到absolute
		// url的转换，有两种方式，一种是用jsoup，"abs:href"；另一种用URL类的构造器，指定baseURL。这里用后一种
		Document doc = Jsoup.parse(crawlUrl.getResponse().getContentStr());

		// 查找base标签，设定derelative url使用的base url
		Elements base = doc.select("base");
		if (base != null && base.size() > 0 && base.first().hasAttr("href")) {
			try {
				URL baseURL = new URL(base.first().attr("href"));
				crawlUrl.setBaseURL(baseURL);
			} catch (MalformedURLException e) {
				logger.info("定义错误的base标签：" + base.first().html() + "，将使用自身进行相对路径的derelative。URL：" + crawlUrl.toString());
			}
		}

		// 抽取子链接，这里仅抽取iframe、frame、a这三种标签
		Elements frames = doc.select("iframe frame");
		for (Element frame : frames) {
			String src = frame.attr("src");
			try {
				URL outlink = new URL(crawlUrl.getBaseURL(), src);
				CrawlURL candidateURL = new CrawlURL(outlink);
				candidateURL.setVia(crawlUrl.getURL());
				crawlUrl.addCandidate(candidateURL);
			} catch (MalformedURLException e) {
				// not a legal url , ignore
			}
		}

		Elements links = doc.select("a");
		for (Element link : links) {
			String href = link.attr("href");
			try {
				URL outlink = new URL(crawlUrl.getBaseURL(), href);
				CrawlURL candidateURL = new CrawlURL(outlink);
				candidateURL.setVia(crawlUrl.getURL());
				crawlUrl.addCandidate(candidateURL);
			} catch (MalformedURLException e) {
				// not a legal url , ignore
			}
		}

		ctx.proceed();
	}
}
