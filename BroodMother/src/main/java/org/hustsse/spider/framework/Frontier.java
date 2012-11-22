package org.hustsse.spider.framework;

import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.hustsse.spider.model.CrawlController;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class Frontier {
	private static Logger logger = LoggerFactory.getLogger(Frontier.class);

	@Autowired
	CrawlController controller;
	@Autowired
	UrlUniqFilter urlUniqFilter;

	AtomicLong ordinal = new AtomicLong(0);	//url被scheduled in的序号

	BlockingQueue<CrawlURL> crawlURIs;

	Map<String, WorkQueue> allQueues = new ConcurrentHashMap<String, WorkQueue>();
	Queue<String> readyQueues = new ConcurrentLinkedQueue<String>();
	DelayQueue<DelayedWorkQueue> snoozedQueues = new DelayQueue<DelayedWorkQueue>();
	Set<String> emptyQueues = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());	// j.u.c没有提供ConcurrentHashSet，用这种折中的方式

	public Frontier() {
		startWakeThread();
	}

	Thread wakeSnoozedThread;
	class WakeThread implements Runnable{
		@Override
		public void run() {
			while(true) {
				try {
					DelayedWorkQueue waked = snoozedQueues.take();
					WorkQueue wq = waked.getWorkQueue();
					readyQueues.add(wq.getKey());
				} catch (InterruptedException e) {
			}
			}
		}
	}

	private void startWakeThread() {
		wakeSnoozedThread = new Thread(new WakeThread());
		wakeSnoozedThread.setDaemon(true);
		wakeSnoozedThread.setName("wake snoozed daemon thread");
		wakeSnoozedThread.start();
	}

	public CrawlURL next() {
		// 1. politeness 抓取间隔
		// 2. 同时进行抓取的并发数 - 先不做

		while(true) {

			//首先从readyqueues里拿一个workqueue
			WorkQueue wq = null;
			String key = null;
			while(true) {
				key = readyQueues.poll();
				// 没有ready的workQueue，返回null
				if(key == null)
					return null;
				wq = allQueues.get(key);
				// key在ready queues中但不在allQueues，记录错误并继续查看下个ready queue
				if(wq == null) {
					// this should not happen
					logger.error("key "+key+" 在readyQueues中但不在allQueues中！");
					continue;
				}
				// redy queue是个空的，将它放到emptyQueues中，继续查看下个ready queue
				if(wq.isEmpty()) {
					emptyQueues.add(key);
					continue;
				}
				// 这里已经成功拿到一个不为空的workqueue了，但是我们还得处理snooze
				if(snoozeIfNecessary(wq)) {
					continue;
				}
				break;
			}

			CrawlURL url = wq.dequeue();
			if(url == null) {
				// this should not happen
				logger.error("非空workQueue出队URL时得到null："+wq.toString());
				continue;
			}
			getCrawlPipeline().attachTo(url);
			wq.setLastDequeueTime(System.nanoTime());
			// 从wq中拿了url后，因为它是ready状态的，应该重新入readyQueues队列。
			// 我们使用了ConcurrentLinkedQueue这个实现，所以对所有就绪workqueue的
			// 调度实际上是round-robin轮转方式。
			// 需要对workQueue增加优先级特性吗？
			readyQueues.add(key);
			return url;
		}



/*		if (crawlURIs == null) {
			crawlURIs = new LinkedBlockingQueue<CrawlURL>();
			// CrawlURL u1 = new
			// CrawlURL("http://www.songtaste.com/song/1199232/");
			// CrawlURL u2 = new
			// CrawlURL("http://www.songtaste.com/userlist.php");
			CrawlURL u3 = new CrawlURL("http://bbs.nju.edu.cn/board?board=Pictures");
			// crawlURIs.add(u1);
			// crawlURIs.add(u2);
			crawlURIs.add(u3);
		}

		try {
			CrawlURL next = crawlURIs.take();
			getCrawlPipeline().attachTo(next);
			return next;
		} catch (InterruptedException e) {
			return null;
		}*/
	}

	private boolean snoozeIfNecessary(WorkQueue wq) {
		// handle politeness snooze
		long lastDequeueTime = wq.getLastDequeueTime();
		long now = System.nanoTime();
		long duration = now - lastDequeueTime;
		long politenessInterval = wq.getPolitenessInterval();
		// 距离上次取url过去的时间比politeness间隔短，需要休眠一会儿
		if(duration < politenessInterval) {
			DelayedWorkQueue dwq = new DelayedWorkQueue(wq, politenessInterval - duration);
			snoozedQueues.add(dwq);
			return true;
		}
		return false;
	}

	public void schedule(CrawlURL u) {
		/*
		 * 被schedule的url都假设已经在CandidatePreparer中经过了预处理，即：
		 * 1. workqueuekey
		 * 2. scheduleDirective  第一优先级//没有
		 * 3. precedence	第二优先级
		 */
		String canon = u.getCanonicalStr();
		if(!urlUniqFilter.add(canon)) {
			u.setOrdinal(ordinal.incrementAndGet());
			sendToQueue(u);
		}
		this.crawlURIs.offer(u); // TODO delete me!
	}

	private void sendToQueue(CrawlURL u) {
		WorkQueue wq;
		String key = u.getWorkQueueKey();
		if(allQueues.containsKey(key)) {
			wq = allQueues.get(key);
		}else {
			wq = createWorkQueueFor(key);
			allQueues.put(key, wq);
		}
		wq.enqueue(u);
		// wq如果在empty queues中，将其移入ready queues中，需要snoozed时移入snoozed
		if(emptyQueues.remove(key) && !snoozeIfNecessary(wq)) {
			readyQueues.add(key);
		}
	}

	private WorkQueue createWorkQueueFor(String key) {
		WorkQueue wq = new MemWorkQueue(key);
		return wq;
	}

	public void loadSeeds() {

	}

	private Pipeline getCrawlPipeline() {
		return controller.getPipeline();
	}



}
