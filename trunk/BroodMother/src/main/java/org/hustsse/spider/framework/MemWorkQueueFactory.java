package org.hustsse.spider.framework;

public class MemWorkQueueFactory implements WorkQueueFactory {

	private int maxLengthPerWorkQueue;

	public int getMaxLengthPerWorkQueue() {
		return maxLengthPerWorkQueue;
	}

	public void setMaxLengthPerWorkQueue(int maxLengthPerWorkQueue) {
		this.maxLengthPerWorkQueue = maxLengthPerWorkQueue;
	}

	@Override
	public WorkQueue createWorkQueueFor(String workQueueKey) {
		return new MemWorkQueue(workQueueKey, maxLengthPerWorkQueue);
	}

}
