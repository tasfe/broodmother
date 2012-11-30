package org.hustsse.spider.framework;



public abstract class AbstractWorkQueue implements WorkQueue {
	protected String workQueueKey;
	protected long lastDequeueTime;
	protected long politenessInterval;
	/** 最大可容纳的元素个数，<=0则不限长 */
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
