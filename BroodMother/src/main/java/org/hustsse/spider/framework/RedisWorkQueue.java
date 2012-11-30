package org.hustsse.spider.framework;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;

import org.hustsse.spider.exception.DequeueFailedException;
import org.hustsse.spider.exception.EnqueueFailedException;
import org.hustsse.spider.model.CrawlURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RedisWorkQueue extends AbstractWorkQueue {
	private static final String DEFAULT_SERIALIZE_CHARSET = "UTF-8";
	private JedisPool jedisPool;

	private static Logger logger = LoggerFactory.getLogger(RedisWorkQueue.class);
	private ObjectMapper jackson;

	public RedisWorkQueue(String key, JedisPool jedisPool,ObjectMapper jackson) {
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
				logger.error("failed to enqueue url to WorkQueue[{}],url: {}", new Object[] { workQueueKey, u, e });
				jedisPool.returnBrokenResource(con);
				// do not return the same object twice
				con = null;
				throw new EnqueueFailedException(e);
			} finally {
				if (con != null)
					jedisPool.returnResource(con);
			}
		}catch(EnqueueFailedException e) {
			throw e;
		} catch (Exception e) {
			logger.error("failed to borrow an connection", e);
			throw new EnqueueFailedException(e);
		}
	}

	@Override
	public CrawlURL dequeue(){
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
				logger.error("failed to dequeue,WorkQueue：{}", workQueueKey, e);
				jedisPool.returnBrokenResource(con);
				// do not return the same object twice
				con = null;
				throw new DequeueFailedException(e);
			} finally {
				if (con != null)
					jedisPool.returnResource(con);
			}
		} catch (Exception e) {
			logger.error("failed to borrow an connection", e);
			throw new DequeueFailedException(e);
		}
	}

	private Jedis getConnection() {
		return jedisPool.getResource();
	}

	private byte[] getSerializedKey() {
		byte[] bytes = null;
		try {
			bytes = workQueueKey.getBytes(DEFAULT_SERIALIZE_CHARSET);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("平台不支持" + DEFAULT_SERIALIZE_CHARSET + "字符集?!", e);
		}
		return bytes;
	}

	private byte[] serializeCrawlUrl(CrawlURL url) {
		byte[] bytes = null;

		try {
			bytes = jackson.writeValueAsBytes(url);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("序列化失败, url: " + url, e);
		}
		return bytes;
	}

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
				logger.error("failed to get count of WorkQueue：{}", workQueueKey, e);
				jedisPool.returnBrokenResource(con);
				// do not return the same object twice
				con = null;
				throw new RuntimeException(e);
			} finally {
				if (con != null)
					jedisPool.returnResource(con);
			}
		} catch (Exception e) {
			logger.error("failed to borrow an connection", e);
			throw new RuntimeException(e);
		}
	}
}
