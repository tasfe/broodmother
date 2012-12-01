package org.hustsse.spider.dns;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.hustsse.spider.handler.crawl.DnsResolver;

/**
 * DNS缓存。
 *
 * <p>
 * 为了避免抓取过程中对DNS服务器造成太大的压力，对DNS记录进行缓存。{@link DnsResolver}
 * 对某个CrawlURL进行dns解析前必须首先查看缓存中是否已有相应记录。
 * </p>
 *
 * <p>
 * 基于EnCache实现，默认加载配置文件encache.xml。
 * </p>
 *
 * @author Anderson
 *
 */
public class DnsCache {

	public final static CacheManager cacheManager = new CacheManager();
	private String cacheName;
	private Cache cache;

	public DnsCache(String cacheName) {
		this.cacheName = cacheName;
		cache = cacheManager.getCache(cacheName);
		if (cache == null)
			throw new IllegalArgumentException("找不到名为'" + cacheName + "'的缓存！");
	}

	public Dns get(String host) {
		Element element = cache.get(host);
		return element == null ? null : (Dns) (element.getValue());
	}

	public void put(String host, Dns dns) {
		// dns为0表示不应该缓存
		if (dns.getTtl() <= 0) {
			return;
		}
		Element e = new Element(host, dns, false, 0, dns.getTtl() == Dns.NEVER_EXPIRES ? 0 : dns.getTtl());
		cache.put(e);
	}

	public void clear() {
		cache.removeAll();
	}

	public String getCacheName() {
		return cacheName;
	}

	public Cache getCache() {
		return cache;
	}
}
