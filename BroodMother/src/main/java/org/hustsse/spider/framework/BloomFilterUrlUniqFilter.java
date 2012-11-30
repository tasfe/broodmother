package org.hustsse.spider.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BloomFilterUrlUniqFilter implements UrlUniqFilter {
	private static Logger logger = LoggerFactory.getLogger(BloomFilterUrlUniqFilter.class);

	public static void main(String[] args) {
		BloomFilterUrlUniqFilter u = new BloomFilterUrlUniqFilter();
		System.out.println(u);
	}

	private BloomFilter bloom;
	// 需要内存约11.5M
	private static final double DEFAULT_FALSE_POSITIVE_EXPECTED = 0.0001;
	private static final int DEFAULT_ELEMENTS_NUM_EXPECTED = 500 * 10000;


	public BloomFilterUrlUniqFilter() {
		this(DEFAULT_FALSE_POSITIVE_EXPECTED,DEFAULT_ELEMENTS_NUM_EXPECTED);
	}

	public BloomFilterUrlUniqFilter(double falsePositiveExpected, int elementsNumExpected) {
		bloom = new BloomFilter(falsePositiveExpected, elementsNumExpected);
	}

	@Override
	public boolean add(String canonicalUrl) {
		return bloom.add(canonicalUrl);
	}

	@Override
	public long count() {
		return bloom.count();
	}

	@Override
	public boolean delete(String canonicalUrl) {
		// TODO 用一个白名单记录删除的url？
		logger.error("bloom filter based uri unique filter doesn't support deletion!");
		return false;
	}

}
