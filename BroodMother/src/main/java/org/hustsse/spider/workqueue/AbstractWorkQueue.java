package org.hustsse.spider.workqueue;

import org.hustsse.spider.framework.WorkQueue;

/**
 * WorkQueue的抽象实现，提供了大部分具体实现可以共用的Key、上次入队时间、Politeness间隔、元素上限相关逻辑。
 *
 * @author Anderson
 *
 */
public abstract class AbstractWorkQueue implements WorkQueue {
	protected String workQueueKey;
	protected long lastDequeueTime;
	protected long politenessInterval;
	/** 最大可容纳的元素个数，默认无限制 */
	protected long maxLength = -1;

	public AbstractWorkQueue(String key, long maxLength) {
		this.workQueueKey = key;
		this.maxLength = maxLength;
	}

	public AbstractWorkQueue(String key) {
		this.workQueueKey = key;
	}

	@Override
	public String getKey() {
		return workQueueKey;
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
		return politenessInterval;
	}

	@Override
	public void setPolitenessInterval(long politenessInterval) {
		this.politenessInterval = politenessInterval;
	}

	@Override
	public long getMaxLength() {
		return maxLength;
	}

	@Override
	public void setMaxLength(long maxLength) {
		this.maxLength = maxLength;
	}

	@Override
	public boolean isEmpty() {
		return count() == 0;
	}

}
