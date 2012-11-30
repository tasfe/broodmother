package org.hustsse.spider.framework;

/**
 * WorkQueue的工厂
 *
 * @author novo
 *
 */
public interface WorkQueueFactory {

	/**
	 * create a workqueue for a Key
	 *
	 * @param workQueueKey
	 * @return
	 */
	WorkQueue createWorkQueueFor(String workQueueKey);
}
