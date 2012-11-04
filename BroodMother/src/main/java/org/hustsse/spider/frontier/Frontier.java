package org.hustsse.spider.frontier;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.hustsse.spider.handler.Handler;
import org.hustsse.spider.handler.crawl.extractor.ExtractorHTML;
import org.hustsse.spider.handler.crawl.fetcher.nio.NioFetcher;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.pipeline.DefaultPipeline;
import org.hustsse.spider.pipeline.Pipeline;

public class Frontier {

	BlockingQueue<CrawlURL> crawlURIs = new LinkedBlockingQueue<CrawlURL>();

	public Frontier(){
		//test code


		Handler h1 = new NioFetcher(Executors.newCachedThreadPool(),Executors.newCachedThreadPool(),3);
		Handler h2 = new ExtractorHTML();

		Pipeline p1 = new DefaultPipeline(h1,h2);
		Pipeline p2 = new DefaultPipeline(h1,h2);

		CrawlURL u1 = new CrawlURL("http://www.songtaste.com/song/1199232/",p1);
		CrawlURL u2 = new CrawlURL("http://www.douban.com/accounts/register",p2);

		crawlURIs.add(u1);
		crawlURIs.add(u2);
	}

	public CrawlURL next() {
		try {
			return crawlURIs.take();
		} catch (InterruptedException e) {
			return null;
		}
	}

}
