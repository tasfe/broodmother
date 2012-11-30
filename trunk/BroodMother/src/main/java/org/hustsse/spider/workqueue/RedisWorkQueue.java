package org.hustsse.spider.workqueue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;

import org.hustsse.spider.exception.DequeueFailedException;
import org.hustsse.spider.exception.EnqueueFailedException;
import org.hustsse.spider.model.CrawlURL;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WorkQueue基于redis的实现。每个WorkQueue在Redis中以一个[key--sortedSet]键值对形式存在，
 * 元素的Score为该URL的priority。
 *
 * @author Anderson
 *
 */
public class RedisWorkQueue extends AbstractWorkQueue {
	/** 序列化时用的字符集 */
	private static final String DEFAULT_SERIALIZE_CHARSET = "UTF-8";
	/** 使用的jedis连接池 */
	private JedisPool jedisPool;
	/** Jackson Json Util */
	private ObjectMapper jackson;
	/** Serialized WorkQueue Key */
	private byte[] serializedKey;

	public RedisWorkQueue(String key, JedisPool jedisPool, ObjectMapper jackson) {
		super(key);
		this.jedisPool = jedisPool;
		this.jackson = jackson;
	}

	@Override
	public void enqueue(CrawlURL u) {
		Jedis con = null;
		try {
			con = getConnection();
			try {
				con.zadd(getSerializedKey(), (double) u.getPriority(), serializeCrawlUrl(u));
			} catch (Exception e) {
				jedisPool.returnBrokenResource(con);
				// do not return the same object twice
				con = null;
				throw new EnqueueFailedException("failed to enqueue url to WorkQueue[" + workQueueKey + "],url: " + u, e);
			} finally {
				if (con != null)
					jedisPool.returnResource(con);
			}
		} catch (EnqueueFailedException e) {
			throw e;
		} catch (Exception e) {
			throw new EnqueueFailedException("failed to borrow an connection", e);
		}
	}

	@Override
	public CrawlURL dequeue() {
		Jedis con = null;
		try {
			con = getConnection();
			try {
				Transaction t = con.multi();
				Response<Set<byte[]>> result = t.zrange(getSerializedKey(), 0, 0);
				t.zremrangeByRank(getSerializedKey(), 0, 0);
				t.exec();

				if (result.get() == null || result.get().isEmpty())
					return null;

				return deserializeCrawlUrl(result.get().iterator().next());
			} catch (Exception e) {
				jedisPool.returnBrokenResource(con);
				// do not return the same object twice
				con = null;
				throw new DequeueFailedException("failed to dequeue,WorkQueue[" + workQueueKey + "] ", e);
			} finally {
				if (con != null)
					jedisPool.returnResource(con);
			}
		} catch (Exception e) {
			throw new DequeueFailedException("failed to borrow an connection", e);
		}
	}

	/**
	 * borrow a connection from pool
	 *
	 * @return
	 */
	private Jedis getConnection() {
		return jedisPool.getResource();
	}

	/**
	 * 得到序列化之后的workQueueKey
	 *
	 * @return
	 */
	private byte[] getSerializedKey() {
		if (serializedKey != null)
			return serializedKey;

		try {
			serializedKey = workQueueKey.getBytes(DEFAULT_SERIALIZE_CHARSET);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("平台不支持" + DEFAULT_SERIALIZE_CHARSET + "字符集?!", e);
		}
		return serializedKey;
	}

	/**
	 * 序列化一个CrawlURL对象。首先用Jackson将其json化，再将其使用默认字符集转换成字节数组返回。
	 *
	 * @param url
	 * @return
	 */
	private byte[] serializeCrawlUrl(CrawlURL url) {
		byte[] bytes = null;
		try {
			bytes = jackson.writeValueAsBytes(url);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("序列化失败, url: " + url, e);
		}
		return bytes;
	}

	/**
	 * 反序列化CrawlURL
	 *
	 * @param bytes
	 * @return
	 */
	private CrawlURL deserializeCrawlUrl(byte[] bytes) {
		CrawlURL u;
		try {
			u = jackson.readValue(bytes, CrawlURL.class);
			return u;
		} catch (IOException e) {
			throw new RuntimeException("反序列化CrawlURL失败！ ", e);
		}
	}

	@Override
	public long count() {
		Jedis con = null;
		try {
			con = getConnection();
			try {
				Long count = con.zcard(getSerializedKey());
				return count;
			} catch (Exception e) {
				jedisPool.returnBrokenResource(con);
				// do not return the same object twice
				con = null;
				throw e;
			} finally {
				if (con != null)
					jedisPool.returnResource(con);
			}
		} catch (Exception e) {
			throw new RuntimeException("failed to get count of WorkQueue", e);
		}
	}
}
