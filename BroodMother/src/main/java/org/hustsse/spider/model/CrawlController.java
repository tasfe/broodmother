package org.hustsse.spider.model;

import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.hustsse.spider.exception.CrawlControllerException;
import org.hustsse.spider.exception.OverFlowException;
import org.hustsse.spider.framework.Frontier;
import org.hustsse.spider.framework.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 每个抓取任务都会有一个CrawlController作为爬取过程的控制中心。 CrawlController管理着爬取所需的部件和资源。
 *
 * @author Administrator
 *
 */
public class CrawlController implements ApplicationContextAware {
	private static Logger logger = LoggerFactory.getLogger(CrawlController.class);
	/** 使用的Spring容器。CrawlController利用Spring管理和组装各个子部件。 */
	ApplicationContext appContext;
	/** url manager */
	@Autowired
	private Frontier frontier;

	/**
	 * crawl url的pipeline bean id。有可能是prototype的， 不能注入，这样每次拿到的Pipeline对象还是同一个。
	 * 每次请求{@link CrawlController#getPipeline()}通过
	 * {@link ApplicationContext#getBean(String)} 返回 Pipeline bean。
	 */
	private String pipelineBeanId;

	/** Crawl线程使用的线程池 */
	private Executor crawlThreadPool;

	/** Crawl线程个数 */
	private int crawlThreadNum;

	/** 种子 */
	private List<String> seeds;

	/** 对应的抓取任务 */
	private CrawlJob crawlJob;

	// -------- consts
	private static final long DEFAULT_CRAWL_THREAD_TIMEOUT = 30L;

	/** 默认的Crawl线程数 */
	private static final int DEFAULT_CRAWL_THREAD_NUM = 1;

	/**
	 * 启动任务，开始抓取。
	 * <p>
	 * 1. 加载种子
	 * <p>
	 * 2. 提交{@link #crawlThreadNum}个{@link CrawlTask}到{@link #crawlThreadPool}
	 * 执行。
	 *
	 * @throws CrawlControllerException
	 *             加载种子失败时
	 */
	public void start() {
		// load the seeds first
		try {
			frontier.loadSeeds(seeds);
		} catch (MalformedURLException e) {
			// cannot recover, do nothing but throw a RuntimeException to
			// fail-fast
			throw new CrawlControllerException("seed格式不合法！", e);
		} catch (OverFlowException e) {
			// too many seeds
			throw new CrawlControllerException("too many seeds！workqueue overflow!", e);
		}

		if (crawlThreadPool == null)
			crawlThreadPool = Executors.newCachedThreadPool();
		if (crawlThreadNum <= 0)
			crawlThreadNum = DEFAULT_CRAWL_THREAD_NUM;
		for (int i = 0; i < crawlThreadNum; i++) {
			Runnable crawlTask = new CrawlTask(crawlJob, i);
			crawlThreadPool.execute(crawlTask);
		}
	}

	/**
	 * 抓取线程，不停地从Frontier取CrawlURL，开启它的Pipeline执行。
	 *
	 * TODO：拿不到时怎么办？sleep若干次并时间递增，还是拿不到则推出？
	 * @author Anderson
	 *
	 */
	class CrawlTask implements Runnable {
		int index;
		CrawlJob job;

		CrawlTask(CrawlJob crawlJob, int index) {
			job = crawlJob;
			this.index = index;
		}

		@Override
		public void run() {
			String newThreadName = "crawl thread , " + job.getName() + "#" + index;
			Thread.currentThread().setName(newThreadName);
			while (true) {
				CrawlURL uriToCrawl = null;
				uriToCrawl = frontier.next();
				if (uriToCrawl != null) {
					logger.debug("处理URL：" + uriToCrawl.toString());
					uriToCrawl.getPipeline().start();
				}

				if (uriToCrawl == null) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
					continue;
				}
			}
		}
	}

	public List<String> getSeeds() {
		return seeds;
	}

	public void setSeeds(List<String> seeds) {
		this.seeds = seeds;
	}

	public Executor getCrawlThreadPool() {
		return crawlThreadPool;
	}

	public void setCrawlThreadPool(Executor crawlThreadPool) {
		this.crawlThreadPool = crawlThreadPool;
	}

	public int getCrawlThreadNum() {
		return crawlThreadNum;
	}

	public void setCrawlThreadNum(int crawlThreadNum) {
		this.crawlThreadNum = crawlThreadNum;
	}

	public CrawlJob getCrawlJob() {
		return crawlJob;
	}

	public void setCrawlJob(CrawlJob crawlJob) {
		this.crawlJob = crawlJob;
	}

	public Frontier getFrontier() {
		return frontier;
	}

	public Pipeline getPipeline() {
		return (Pipeline) appContext.getBean(pipelineBeanId);
	}

	public String getPipelineId() {
		return pipelineBeanId;
	}

	public void setPipelineId(String pipelineId) {
		this.pipelineBeanId = pipelineId;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		appContext = applicationContext;
	}

}
