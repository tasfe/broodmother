package org.hustsse.spider.framework;

import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;

import com.fasterxml.jackson.databind.ObjectMapper;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisWorkQueueFactory implements WorkQueueFactory {

	private static ObjectMapper jackson = new ObjectMapper();

	private volatile JedisPool jedisPool;

	Resource configFile;

//	long maxLengthPerQueue;  每个wq的上限由redis的配置决定

	private void init() {
		Properties config = new Properties();
		if (configFile == null) {
			throw new IllegalArgumentException("未找到redis配置文件!");
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

		jedisPool = new JedisPool(poolConfig, host, port, timeout, StringUtils.isEmpty(password)?null:password);
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

//	public long getMaxLengthPerQueue() {
//		return maxLengthPerQueue;
//	}
//
//	public void setMaxLengthPerQueue(long maxLengthPerQueue) {
//		this.maxLengthPerQueue = maxLengthPerQueue;
//	}

	public Resource getConfigFile() {
		return configFile;
	}

	public void setConfigFile(Resource configFile) {
		this.configFile = configFile;
	}
}
