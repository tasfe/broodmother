package org.hustsse.spider.framework;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.hustsse.spider.exception.CrawlJobException;
import org.hustsse.spider.model.CrawlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扫描job dir，每个目录当做一个job，为其创建一个CrawlJob对象， initiate和launch。
 *
 * @author Administrator
 *
 */
public class JobScanner {
	private static Logger logger = LoggerFactory.getLogger(JobScanner.class);
	private static final String DEFAULT_JOBS_DIR = "jobs";
	private static JobScanner J = new JobScanner();

	// name -- job
	private Map<String,CrawlJob> jobs;

	public static JobScanner getInstance() {
		return J;
	}

	private JobScanner() {
		jobs = new HashMap<String,CrawlJob>(10);
	}

	private String spiderPath;

	private String getSpiderPath() {
		if (spiderPath != null)
			return spiderPath;
		String jarPath = JobScanner.class.getProtectionDomain().getCodeSource().getLocation().getFile();
		File jarFile = new File(jarPath);
		spiderPath = jarFile.getParentFile() // lib
				.getParent(); // BroodMother
		return spiderPath;
	}

	private String jobsDir;

	private String getJobsDir() {
		if (jobsDir != null) {
			return jobsDir;
		}
		jobsDir = getSpiderPath() + File.separator + DEFAULT_JOBS_DIR;
		return jobsDir;
	}

	public static void main(String[] args) {
		JobScanner j = JobScanner.getInstance();
		j.launchAll();
	}

	public void launchAll() {
		scanJob();
		for (String name : jobs.keySet()) {
			launch(name);
		}
	}

	/**
	 * scan dirs, create and initiate jobs, but NOT launch them.
	 * TODO 动态监控。文件夹新建 -- 创建job；删除 -- 停止job
	 */
	private void scanJob() {
		File jobsDir = new File(getJobsDir());
		File[] jobDirs = jobsDir.listFiles();
		//no crawl job
		if(jobDirs == null)
			return;

		for (File jobDir : jobDirs) {
			if (jobDir.isDirectory()) {
				try {
					CrawlJob job = new CrawlJob(jobDir);
					this.jobs.put(job.getName(),job);
				}catch(CrawlJobException e) {
					logger.error("为目录" + jobDir.getName() + "创建任务失败！",e);
				}
			}
		}
	}

	/**
	 * launch a job
	 * @param jobName
	 */
	private void launch(String jobName) {
		CrawlJob job = jobs.get(jobName);
		if(job != null) {
			try {
				job.launch();
			}catch(CrawlJobException e) {
				logger.error("启动任务"+jobName+"失败！",e);
			}
		}
	}
}
