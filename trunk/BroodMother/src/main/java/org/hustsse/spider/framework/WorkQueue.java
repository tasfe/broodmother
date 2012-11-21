package org.hustsse.spider.framework;

import org.hustsse.spider.model.CrawlURL;

/**
 * 没有超时/wait版本的方法，调用者负责超时控制
 * @author Administrator
 *
 */
public interface WorkQueue {

	void enqueue(CrawlURL u);

	String getKey();
	void setKey(String wqKey);

	CrawlURL dequeue();

	boolean isEmpty();

	/**
	 * 得到上次URL出队时间，单位nanoSecond
	 * @return
	 */
	long getLastDequeueTime();
	void setLastDequeueTime(long lastDequeueTime);

	long getPolitenessInterval();
	void setPolitenessInterval(long politenessInterval);
}
