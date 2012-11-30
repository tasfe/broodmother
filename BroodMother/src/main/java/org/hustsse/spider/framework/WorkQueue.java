package org.hustsse.spider.framework;

import org.hustsse.spider.exception.DequeueFailedException;
import org.hustsse.spider.exception.EnqueueFailedException;
import org.hustsse.spider.exception.OverFlowException;
import org.hustsse.spider.model.CrawlURL;

/**
 * 没有超时/wait版本的方法，调用者负责超时控制
 * @author Administrator
 *
 */
public interface WorkQueue {

	/**
	 *
	 * @param u
	 * @throws  OverFlowException
	 * @throws  EnqueueFailedException
	 */
	void enqueue(CrawlURL u) throws OverFlowException;

	String getKey();

	/**
	 *
	 * @return null if empty; non-null if not empty
	 * @throws DequeueFailedException if failed
	 */
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

	long count();
	long getMaxLength();

	// <=0时,没有限制; <count时依然起作用
	void setMaxLength(long maxLength);

	// void setDiscardedUrlHandler()   超过max时的处理器

	// diskBased()? close() ? sync()?等与资源相关的方法？类似bufferedouputstream

}
