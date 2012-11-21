package org.hustsse.spider.model;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.hustsse.spider.exception.CrawlJobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * 一个CrawlJob代表一个爬取任务，即一个job dir下的子目录。
 *
 * 1. 初始化：set up crawl controller，把容器建起来，子部件都装配好。
 * 2. launch：spider类的工作移到这里来.启动boss线程，从frontier拿uri处理.
 * 3. Spider类只负责启动jobScanner
 * @author Administrator
 *
 */
public class CrawlJob {
	private static Logger logger = LoggerFactory.getLogger(CrawlJob.class);

	private static final String DEFAULT_JOB_LIB_DIR = "lib";
	private static final String DEFAULT_JOB_CONFIG_FILE = "job.xml";
	private String name;
	private FileSystemXmlApplicationContext container;
	private CrawlController crawlController;


	public CrawlJob(File jobDir) {
		this.name = jobDir.getName();	// use the dir's name as the job's name
		logger.debug("create job for:"+name);
		try {
			//initiate the spring container
			prepareAppContext(jobDir);
			crawlController = container.getBean(CrawlController.class);
			crawlController.setCrawlJob(this);
		}catch(Throwable e) {
			throw new CrawlJobException(e);
		}
	}

	private void prepareAppContext(File jobDir) {
		container = new FileSystemXmlApplicationContext();
		URLClassLoader jobClassLoader = createJobClassLoader(jobDir);
		container.setClassLoader(jobClassLoader);
		String jobConfigFile = jobDir.getAbsolutePath() + File.separator + DEFAULT_JOB_CONFIG_FILE;
		container.setConfigLocation(jobConfigFile);
		container.refresh();
	}

	private URLClassLoader createJobClassLoader(File jobDir) {
		// get a new "job class loader"
		File lib = new File(jobDir.getAbsolutePath() + File.separator + DEFAULT_JOB_LIB_DIR);
		List<URL> libs = new ArrayList<URL>();
		if (lib.exists() && lib.isDirectory()) {
			File[] jars = lib.listFiles();
			for (File jar : jars) {
				if(jar.isFile() && jar.getName().endsWith(".jar")) {
					try {
						logger.debug("find jar: "+jar.toURI().toURL());
						libs.add(jar.toURI().toURL());
					} catch (MalformedURLException e) {
						logger.error("this should not happen!");
					}
				}
			}
		}
		URLClassLoader jobClassLoader = new URLClassLoader(libs.toArray(new URL[libs.size()]));
		return jobClassLoader;
	}

	public String getName() {
		return name;
	}

	public void launch() {
		try {
			crawlController.start();
		}catch(Throwable e) {
			throw new CrawlJobException(e);
		}
	}
}
