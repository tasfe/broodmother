package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.model.CrawlURL;

public interface QueueAssignmentPolicy {

	String getWorkQueueKeyFor(CrawlURL url);

}
