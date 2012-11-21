package org.hustsse.spider.framework;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import org.hustsse.spider.model.CrawlURL;

/**
 * workqueue基于内存的实现，based on PriorityQueue
 *
 * @author Administrator
 *
 */
public class MemWorkQueue implements WorkQueue {

	private static final int DEFAULT_INITIAL_CAPACITY = 1000;

	Queue<CrawlURL> urls;
	String workQueueKey;
	long lastDequeueTime;
	long politenessInterval;

	public MemWorkQueue(String key) {
		this.workQueueKey = key;
		urls = new PriorityBlockingQueue<CrawlURL>(DEFAULT_INITIAL_CAPACITY);
	}

	@Override
	public void enqueue(CrawlURL u) {
		urls.add(u);
	}

	@Override
	public CrawlURL dequeue() {
		return urls.poll();
	}

	@Override
	public boolean isEmpty() {
		return urls.isEmpty();
	}

	@Override
	public String getKey() {
		return workQueueKey;
	}

	@Override
	public void setKey(String wqKey) {
		workQueueKey = wqKey;
	}

	@Override
	public long getLastDequeueTime() {
		return lastDequeueTime;
	}

	@Override
	public void setLastDequeueTime(long lastDequeueTime) {
		this.lastDequeueTime = lastDequeueTime;
	}

	@Override
	public long getPolitenessInterval() {
		return 0;
	}

	@Override
	public void setPolitenessInterval(long politenessInterval) {
		this.politenessInterval = politenessInterval;
	}

}
