package org.hustsse.spider.framework;

public interface WorkQueueFactory {
	WorkQueue createWorkQueueFor(String workQueueKey);
}
