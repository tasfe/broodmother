package org.hustsse.spider.handler.crawl;

import org.hustsse.spider.handler.AbstractHandler;
import org.hustsse.spider.handler.HandlerContext;
import org.hustsse.spider.model.URL;

public class PrintHandler3 extends AbstractHandler {
	@Override
	public void process(HandlerContext ctx,URL uri) {
		System.out.println(3);
		ctx.proceed();
	}
}
