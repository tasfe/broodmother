package org.hustsse.spider.framework;

import org.hustsse.spider.exception.DequeueFailedException;
import org.hustsse.spider.exception.EnqueueFailedException;
import org.hustsse.spider.exception.OverFlowException;
import org.hustsse.spider.model.CrawlURL;

/**
 * 待爬取的URL队列，每个队列与一个key关联，典型的key如[host:port]，即所有CrawlURL按host组织，
 * 每个host一个WorkQueue。
 *
 * @author novo
 *
 */
public interface WorkQueue {

	/**
	 * 入队
	 *
	 * @param u
	 * @throws OverFlowException
	 *             当元素个数超过最大限制时
	 * @throws EnqueueFailedException
	 *             当入队失败时
	 */
	void enqueue(CrawlURL u) throws OverFlowException;

	/**
	 * 得到该workQueue的key
	 *
	 * @return
	 */
	String getKey();

	/**
	 * 出队
	 *
	 * @return null if empty; non-null if not empty
	 * @throws DequeueFailedException
	 *             if failed
	 */
	CrawlURL dequeue();

	/**
	 * 是否为空
	 *
	 * @return
	 */
	boolean isEmpty();

	/**
	 * 得到上次URL出队时间，单位nanoSecond
	 *
	 * @return
	 */
	long getLastDequeueTime();

	/**
	 * 设置上次URL出队时间，单位nanoSecond
	 *
	 * @param lastDequeueTime
	 *            上次URL出队时间
	 */
	void setLastDequeueTime(long lastDequeueTime);

	/**
	 * 得到politenessInterval，单位nano
	 * second。为了保证对web服务器不造成过大压力，WorkQueue中的URL以一定的速率出队
	 * ，每两次出队之间间隔必须大于指定的politenessInterval。
	 *
	 * @return politenessInterval
	 */
	long getPolitenessInterval();

	/**
	 * 设置politenessInterval，单位nano second。
	 *
	 * @param politenessInterval
	 */
	void setPolitenessInterval(long politenessInterval);

	/**
	 * 得到WorkQueue的元素个数
	 *
	 * @return
	 */
	long count();

	/**
	 * 得到WorkQueue的元素上限
	 *
	 * @return
	 */
	long getMaxLength();

	/**
	 * 设置元素上限，当到达元素上限时入队失败。
	 *
	 * @param maxLength <=0时表示没有限制; <当前元素个数时依然起作用
	 */
	void setMaxLength(long maxLength);

	// void setDiscardedUrlHandler() 超过max时的处理器

	// diskBased()? close() ? sync()?等与资源相关的方法？类似bufferedouputstream

}
