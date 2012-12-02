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
 * 一个CrawlJob代表一个爬取任务，即一个job directory下的子目录。
 *
 * 1. 初始化：set up crawl controller，把容器建起来，子部件都装配好。 2.
 * launch：spider类的工作移到这里来.启动boss线程，从frontier拿uri处理. 3. Spider类只负责启动jobScanner
 *
 * @author Anderson
 *
 */
public class CrawlJob {
	private static Logger logger = LoggerFactory.getLogger(CrawlJob.class);
	/** job所依赖的lib根目录，job启动后所需的类将优先从该目录下加载。各job之间的lib是隔离的。 */
	private static final String DEFAULT_JOB_LIB_DIR = "lib";
	/** job配置文件，job启动时将通过该文件初始化Spring容器。 */
	private static final String DEFAULT_JOB_CONFIG_FILE = "job.xml";
	private static final String DEFAULT_JOB_CONF_DIR = "conf";
	private static final String DEFAULT_GLOBAL_CONF_DIR = "conf";
	private String name;
	/** job使用的Spring Container。 */
	private FileSystemXmlApplicationContext container;
	/** job使用的Crawl Controller。 */
	private CrawlController crawlController;
	/** job的根目录。 */
	private File jobDir;

	/**
	 * 初始化Crawl Job。
	 * <p>
	 * job的名称将被设置为对应目录的名称，Spring容器将被初始化。
	 *
	 * @param jobDir
	 *            job目录
	 * @throws CrawlJobException
	 *             初始化失败
	 */
	public CrawlJob(File jobDir) {
		this.name = jobDir.getName(); // use the dir's name as the job's name
		this.jobDir = jobDir;
		logger.debug("create job for:" + name);
		try {
			// initiate the spring container
			prepareAppContext(jobDir);
			crawlController = container.getBean(CrawlController.class);
			crawlController.setCrawlJob(this);
		} catch (Throwable e) {
			throw new CrawlJobException(e);
		}
	}

	/**
	 * 加载job下的Spring配置文件，默认文件名为{@link #DEFAULT_JOB_CONFIG_FILE}
	 * ，初始化Spring容器，各job将使用自己的ClassLoader初始化ApplicationContext。
	 *
	 * @param jobDir
	 *            job的根目录
	 */
	private void prepareAppContext(File jobDir) {
		container = new FileSystemXmlApplicationContext();
		URLClassLoader jobClassLoader = createJobClassLoader(jobDir);
		container.setClassLoader(jobClassLoader);
		String jobConfigFile = jobDir.getAbsolutePath() + File.separator + DEFAULT_JOB_CONFIG_FILE;
		container.setConfigLocation(jobConfigFile);
		container.refresh();
	}

	/**
	 * 为job创建用于初始化Spring容器的ClassLoader，加载类时优先从{@link #DEFAULT_JOB_LIB_DIR}
	 * 目录中的jar包中加载。
	 *
	 * @param jobDir
	 * @return
	 */
	private URLClassLoader createJobClassLoader(File jobDir) {
		// get a new "job class loader"
		File lib = new File(jobDir.getAbsolutePath() + File.separator + DEFAULT_JOB_LIB_DIR);
		List<URL> libs = new ArrayList<URL>();
		if (lib.exists() && lib.isDirectory()) {
			File[] jars = lib.listFiles();
			for (File jar : jars) {
				if (jar.isFile() && jar.getName().endsWith(".jar")) {
					try {
						logger.debug("find jar: " + jar.toURI().toURL());
						libs.add(jar.toURI().toURL());
					} catch (MalformedURLException e) {
						logger.error("this should not happen!");
					}
				}
			}
		}
		// add the "job conf" & "global conf" directory to classpath
		File jobConf = new File(jobDir.getAbsoluteFile() + File.separator + DEFAULT_JOB_CONF_DIR);
		if(jobConf.exists()) {
			try {
				libs.add(jobConf.toURI().toURL());
			} catch (MalformedURLException e) {
			}
		}
		File globalConf = new File(jobDir.getParentFile().getParentFile().getAbsoluteFile() + File.separator + DEFAULT_GLOBAL_CONF_DIR);
		if(globalConf.exists()) {
			try {
				libs.add(globalConf.toURI().toURL());
			} catch (MalformedURLException e) {
			}
		}

		URLClassLoader jobClassLoader = new URLClassLoader(libs.toArray(new URL[libs.size()]));
		return jobClassLoader;
	}

	/**
	 * launch the job
	 */
	public void launch() {
		try {
			crawlController.start();
		} catch (Throwable e) {
			throw new CrawlJobException(e);
		}
	}

	public String getName() {
		return name;
	}

	public File getJobDir() {
		return jobDir;
	}
}
