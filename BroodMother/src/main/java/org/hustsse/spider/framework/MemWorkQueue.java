package org.hustsse.spider.framework;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.hustsse.spider.exception.EnqueueFailedException;
import org.hustsse.spider.exception.OverFlowException;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * workqueue基于内存的实现，based on PriorityQueue
 *
 * @author Administrator
 *
 */
public class MemWorkQueue extends AbstractWorkQueue {
	private static Logger logger = LoggerFactory.getLogger(MemWorkQueue.class);

	private static final int DEFAULT_INITIAL_CAPACITY = 1000;

	Queue<CrawlURL> urls;
	protected AtomicLong count = new AtomicLong(0);

	public MemWorkQueue(String key, int maxLength) {
		super(key,(long)maxLength);
		urls = new PriorityBlockingQueue<CrawlURL>(DEFAULT_INITIAL_CAPACITY);
	}

	@Override
	public void enqueue(CrawlURL u) throws OverFlowException {
		// 超出最大限制了，discard
		if (count.intValue() >= maxLength && maxLength > 0) {
			throw new OverFlowException("MemWorkQueue[" + workQueueKey + "]元素数目已达上限，数量：" + count.intValue() + "，入队失败。");
		}
		if (urls.add(u)) {
			count.incrementAndGet();
			return;
		}
		// PriorityBlockingQueue是无界的，入队失败说明资源被耗尽，可能OOM了
		throw new EnqueueFailedException("MemWorkQueue[" + workQueueKey + "]入队失败，资源被耗尽？url：" + u + "，url数量：" + count.intValue());
	}

	@Override
	public CrawlURL dequeue() {
		CrawlURL u = urls.poll();
		if (u != null)
			count.decrementAndGet();
		return u;
	}

	@Override
	public void setMaxLength(long maxLength) {
		if(Integer.MAX_VALUE < maxLength)
			this.maxLength = Integer.MAX_VALUE;
		else
			this.maxLength = (int) maxLength;
	}

	@Override
	public long count() {
		return count.intValue();
	}

}
