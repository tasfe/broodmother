package org.hustsse.spider.handler;

import org.hustsse.spider.model.URL;

public interface Handler {
	void process(HandlerContext ctx,URL uri);
}
