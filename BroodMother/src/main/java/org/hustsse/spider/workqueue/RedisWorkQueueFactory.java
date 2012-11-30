package org.hustsse.spider.workqueue;

import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.hustsse.spider.framework.WorkQueue;
import org.hustsse.spider.framework.WorkQueueFactory;
import org.springframework.core.io.Resource;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RedisWorkQueue的工厂，负责创建{@link RedisWorkQueue}。
 *
 * @author Anderson
 *
 */
public class RedisWorkQueueFactory implements WorkQueueFactory {
	/** Jackson ObjectMapper，所有WorkQueue共用一个。 */
	private static ObjectMapper jackson = new ObjectMapper();
	/** Jedis连接池，所有WorkQueue共用一个，Lazy Init方式。 */
	private volatile JedisPool jedisPool;
	/** Redis配置文件，由外部注入。 */
	Resource configFile;

	// long maxLengthPerQueue; 每个wq的上限由redis的配置决定

	/**
	 * 加载Redis配置文件，初始化Redis连接池。
	 *
	 * @throws NullPointerException 未指定配置文件
	 * @throws RuntimeException 配置文件加载失败
	 */
	private void init() {
		Properties config = new Properties();
		if (configFile == null) {
			throw new NullPointerException("未指定redis配置文件!");
		}
		try {
			config.load(configFile.getInputStream());
		} catch (IOException e) {
			throw new RuntimeException("加载redis配置文件[" + configFile + "]失败!", e);
		}
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxActive(Integer.parseInt(config.getProperty("redis.pool.maxActive", "20")));
		poolConfig.setMaxIdle(Integer.parseInt(config.getProperty("redis.pool.maxIdle", "100")));
		poolConfig.setMaxWait(Long.parseLong(config.getProperty("redis.pool.maxWait", "6000")));
		poolConfig.setTestOnBorrow(true);

		String host = config.getProperty("redis.host");
		int port = Integer.parseInt(config.getProperty("redis.port", "6379"));
		String password = config.getProperty("redis.password");
		int timeout = Integer.parseInt(config.getProperty("redis.timeout", "30"));

		jedisPool = new JedisPool(poolConfig, host, port, timeout, StringUtils.isEmpty(password) ? null : password);
	}

	@Override
	public WorkQueue createWorkQueueFor(String workQueueKey) {
		// double check for the pool singleton
		if (jedisPool == null) {
			synchronized (this) {
				if (jedisPool == null)
					init();
			}
		}
		return new RedisWorkQueue(workQueueKey, jedisPool, jackson);
	}

	// public long getMaxLengthPerQueue() {
	// return maxLengthPerQueue;
	// }
	//
	// public void setMaxLengthPerQueue(long maxLengthPerQueue) {
	// this.maxLengthPerQueue = maxLengthPerQueue;
	// }

	public Resource getConfigFile() {
		return configFile;
	}

	public void setConfigFile(Resource configFile) {
		this.configFile = configFile;
	}
}
