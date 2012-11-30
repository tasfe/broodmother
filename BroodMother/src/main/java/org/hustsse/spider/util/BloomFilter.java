package org.hustsse.spider.util;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 布隆过滤器的简单实现。关于BloomFilter的理论见这里
 * http://www.cnblogs.com/allensun/archive/2011/02/16/1956532.html
 *
 * @author Anderson
 *
 */
public class BloomFilter {
	private static Logger logger = LoggerFactory.getLogger(BloomFilter.class);
	/** vector长度 */
	private int m;
	/** 预期元素数量(最大值) */
	private int n;
	/** hash函数个数 */
	private int k;
	/** 实际元素个数 */
	private AtomicInteger count = new AtomicInteger(0);
	/** vector */
	BitSet vector;

	static final Charset charset = Charset.forName("UTF-8");
	// 生成消息摘要的算法
	static final String algorithmName = "MD5";
	static final MessageDigest digestFunction;
	static {
		MessageDigest tmp;
		try {
			tmp = java.security.MessageDigest.getInstance(algorithmName);
		} catch (NoSuchAlgorithmException e) {
			tmp = null;
		}
		digestFunction = tmp;
	}

	/**
	 *
	 * @param falsePositiveExpected
	 *            当元素数量到达预期时，能容忍的误判率
	 * @param elementsNumExpected
	 *            预期存放元素个数
	 */
	public BloomFilter(double falsePositiveExpected, int elementsNumExpected) {
		n = elementsNumExpected;
		m = (int) Math.ceil(-n * Math.log(falsePositiveExpected) / Math.log(2) / Math.log(2));
		k = (int) Math.ceil(Math.log(2) * m / n);
		vector = new BitSet(m);
	}

	/**
	 * 对一个byte[]数组进行若干次hash，内部基于{@link BloomFilter#digestFunction}的salt生成摘要方式,
	 * 每次hash的结果为一个32位的int。
	 *
	 * @param data
	 * @param hashes 哈希次数
	 * @return
	 */
	public static int[] createHashes(byte[] data, int hashes) {
		int[] result = new int[hashes];

		int curHash = 0;
		byte salt = 0;
		while (curHash < hashes) {
			byte[] digest;
			// 用md5/sha1做digest,结果一般为128bit长度。每次digest要更新salt
			synchronized (digestFunction) {
				digestFunction.update(salt);
				salt++;
				digest = digestFunction.digest(data);
			}

			// digest从前往后每4个字节分割
			for (int i = 0; i < digest.length / 4 && curHash < hashes; i++) {
				int h = 0;
				// 将4个字节首尾连接起来当做一个int，这个int就是一次hash的结果
				for (int j = (i * 4); j < (i * 4) + 4; j++) {
					h <<= 8;
					h |= ((int) digest[j]) & 0xFF;
				}
				result[curHash] = h;
				curHash++;
			}
			// 如果当次digest结果的length/4 < hashes，我们继续下一次digest和截取填充的动作
		}

		// 最后的结果是一个长度为hashes的int数组，每个元素是一次hash的结果
		return result;
	}

	/**
	 * 预期误判率
	 *
	 * @return
	 */
	public double expectedFalsePositiveProbability() {
		return getFalsePositiveProbability(n);
	}

	/**
	 * 计算当含有n个元素时，过滤器的误判率
	 *
	 * @param numberOfElements
	 *            元素个数.
	 * @return 误判率.
	 */
	public double getFalsePositiveProbability(double numberOfElements) {
		// (1 - e^(-k * n / m)) ^ k
		return Math.pow((1 - Math.exp(-k * (double) numberOfElements / (double) m)), k);
	}

	/**
	 * 根据当前已加入的元素个数计算误判率
	 *
	 * @return 误判率
	 */
	public double getFalsePositiveProbability() {
		return getFalsePositiveProbability(count.get());
	}

	/**
	 * 添加一个字符串到过滤器，默认使用utf-8对其编码
	 *
	 * @param e
	 * @return 如果e已经存在，返回false；如果e不存在，添加并返回true
	 */
	public boolean add(String e) {
		return add(e.getBytes(charset));
	}

	/**
	 * 添加byte[]到过滤器
	 *
	 * @param e
	 * @return 如果e已经存在，返回false；如果e不存在，添加并返回true
	 */
	public boolean add(byte[] e) {
		if (count() >= n) {
			logger.warn("元素个数已达预期，误判率将高于" + expectedFalsePositiveProbability());
		}
		if (contains(e))
			return false;
		// 创建k个hash值，每个hash值是一个int
		int[] hashes = createHashes(e, k);
		for (int hash : hashes)
			vector.set(hashToIndex(hash), true);
		count.incrementAndGet();
		return true;
	}

	/**
	 * int收敛到m内，这里简单的取模。与0x7fffffff做与运算，将一个32位负整数转成一个31位正整数
	 *
	 * @param hash
	 * @return
	 */
	private int hashToIndex(int hash) {
		return (hash & 0x7fffffff) % m;
	}

	/**
	 * 判断字符串e是否过滤器的元素
	 *
	 * @param e
	 * @return
	 */
	public boolean contains(String e) {
		return contains(e.getBytes(charset));
	}

	/**
	 * 判断byte[] e是否过滤器的元素
	 *
	 * @param e
	 * @return
	 */
	public boolean contains(byte[] e) {
		int[] hashes = createHashes(e, k);
		for (int hash : hashes) {
			if (!vector.get(hashToIndex(hash)))
				return false;
		}
		return true;
	}

	public int count() {
		return count.get();
	}

	/**
	 * 清空
	 */
	public void clear() {
		vector.clear();
		count.set(0);
	}

	public int getHashNum() {
		return k;
	}

}
