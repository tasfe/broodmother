package org.hustsse.spider.framework;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.hustsse.spider.handler.candidate.FrontierPreparer;
import org.hustsse.spider.model.CrawlController;
import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.workqueue.DelayedWorkQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

public class Frontier {
	private static Logger logger = LoggerFactory.getLogger(Frontier.class);

	@Autowired
	CrawlController controller;
	@Autowired
	UrlUniqFilter urlUniqFilter;
	@Autowired
	FrontierPreparer frontierPreparer;
	@Autowired
	WorkQueueFactory workQueueFactory;

	AtomicLong ordinal = new AtomicLong(0); // url被scheduled in的序号

	Set<String> readyQueues = Collections.newSetFromMap(new ConcurrentLinkedHashMap.Builder<String, Boolean>().maximumWeightedCapacity(
			MAX_WQS).build());
	// 不能用队列的原因：schedule时会向readyQueues添加key，可能与现有的重复
	Map<String, WorkQueue> allQueues = new ConcurrentHashMap<String, WorkQueue>();
	Set<String> emptyQueues = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>()); // j.u.c没有提供ConcurrentHashSet，用这种折中的方式
	Set<String> snoozedQueues = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	DelayQueue<DelayedWorkQueue> snoozedDelayedQueues = new DelayQueue<DelayedWorkQueue>();

	public Frontier() {
		startWakeThread();
	}

	Thread wakeSnoozedThread;

	class WakeThread implements Runnable {
		@Override
		public void run() {
			while (true) {
				try {
					DelayedWorkQueue waked = snoozedDelayedQueues.take();
					WorkQueue wq = waked.getWorkQueue();
					logger.debug("[AWAKE] 唤醒wq: " + wq.getKey());
					snoozedQueues.remove(wq.getKey());
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

		while (true) {

			// 首先从readyqueues里拿一个workqueue
			WorkQueue wq = null;
			String key = null;
			while (true) {
				key = getNextReadyQueue();
				// key = readyQueues.poll();
				// 没有ready的workQueue，返回null
				if (key == null)
					return null;
				wq = allQueues.get(key);
				// key在ready queues中但不在allQueues，记录错误并继续查看下个ready queue
				if (wq == null) {
					// this should not happen
					logger.error("key " + key + " 在readyQueues中但不在allQueues中！");
					continue;
				}
				// redy queue是个空的，将它放到emptyQueues中，继续查看下个ready queue
				if (wq.isEmpty()) {
					emptyQueues.add(key);
					continue;
				}
				// 这里已经成功拿到一个不为空的workqueue了，但是我们还得处理snooze
				if (snoozeIfNecessary(wq)) {
					continue;
				}
				break;
			}

			CrawlURL url = null;
			try {
				url = wq.dequeue();
			}catch (Throwable e) {
				// dequeue失败，换下一个
				e.printStackTrace();
			}
			if (url == null) {
				// this should not happen
				logger.error("非空workQueue出队URL时得到null：" + wq.getKey());
				continue;
			}
			getCrawlPipeline().attachTo(url);

			long now = System.nanoTime();
			logger.debug("wq:" + wq.getKey() + "出队，距上次url消费时间(ms)：" + (now - wq.getLastDequeueTime()) / (1000 * 1000));
			wq.setLastDequeueTime(now);
			// 从wq中拿了url后，因为它是ready状态的，应该重新入readyQueues队列。
			// 我们使用了ConcurrentLinkedQueue这个实现，所以对所有就绪workqueue的
			// 调度实际上是round-robin轮转方式。
			// 需要对workQueue增加优先级特性吗？
			readyQueues.add(key);
			return url;
		}
	}

	private String getNextReadyQueue() {
		if (!readyQueues.isEmpty()) {
			Iterator<String> i = readyQueues.iterator();
			String key = i.next();
			i.remove();
			return key;
		}
		return null;
	}

	private boolean snoozeIfNecessary(WorkQueue wq) {
		// handle politeness snooze
		long lastDequeueTime = wq.getLastDequeueTime();
		long now = System.nanoTime();
		long timeElapsed = now - lastDequeueTime;
		long politenessInterval = wq.getPolitenessInterval();
		// 距离上次取url过去的时间比politeness间隔短，需要休眠一会儿
		if (timeElapsed < politenessInterval) {
			long sleepTime = politenessInterval - timeElapsed;
			logger.debug("snooze wq:" + wq.getKey() + ", duration(ms): " + sleepTime / (1000 * 1000));
			DelayedWorkQueue dwq = new DelayedWorkQueue(wq, sleepTime);
			snoozedQueues.add(wq.getKey());
			snoozedDelayedQueues.add(dwq);
			return true;
		}
		return false;
	}

	public void schedule(CrawlURL u) {
		/*
		 * 被schedule的url都假设已经在CandidatePreparer中经过了预处理，即： 1. workqueuekey 2.
		 * scheduleDirective 第一优先级//没有 3. precedence 第二优先级
		 */
		u.setOrdinal(ordinal.incrementAndGet());
		if(!u.isNeedRetry()) {
			String canon = u.getCanonicalStr();
			if (urlUniqFilter.add(canon)) {
				sendToQueue(u);
			}
		}else {	//重试，跳过urlUniqFilter
			u.setNeedRetry(false);
			sendToQueue(u);
		}
	}

	private void sendToQueue(CrawlURL u)   {
		WorkQueue wq;
		String key = u.getWorkQueueKey();
		if (allQueues.containsKey(key)) {
			wq = allQueues.get(key);
		} else {
			wq = createWorkQueueFor(key);
			if (wq == null) { // TODO WQ数量上限的实现方式有待考虑，先用个简单方式
				return;
			}
			allQueues.put(key, wq);
		}

		wq.enqueue(u);

		// ---- 开始workqueue状态的变迁
		// wq如果在empty queues中，此时不可能在ready/snooze queues中，将其移入ready /snoozed
		// queue
		if (emptyQueues.remove(key)) {
			if (snoozeIfNecessary(wq)) { // snooze
				return;
			} else { // ready
				readyQueues.add(key);
				return;
			}
		} else {
			// wq不在empty queues中，如果在snoozed,do nothing;
			// 否则加入readyQueues(虽然有可能和已有的重复,但是使用的set,没关系)
			if (!snoozedQueues.contains(key)) {
				readyQueues.add(key);
			}
		}
	}

	// workqueue最大数，wq不会死亡（暂时）TODO
	private static final int MAX_WQS = 3000;
	private AtomicInteger wqNum = new AtomicInteger(0);

	private WorkQueue createWorkQueueFor(String key) {
//		if (wqNum.intValue() >= MAX_WQS) {
//			return null;
//		}
		wqNum.incrementAndGet();
		WorkQueue wq = workQueueFactory.createWorkQueueFor(key);
		// TODO politeness interval，先定死5秒，移入WorkqueueFactory中？抽象出一个Policy来？
		wq.setPolitenessInterval(5L * 1000 * 1000 * 1000);
		return wq;
	}

	public void loadSeeds(List<String> seeds) throws MalformedURLException {
		for (String seedUrl : seeds) {
			CrawlURL seed = new CrawlURL(seedUrl);
			seed.setSeed(true);
			frontierPreparer.prepare(seed);
			schedule(seed);
		}
	}

	private Pipeline getCrawlPipeline() {
		return controller.getPipeline();
	}

}
